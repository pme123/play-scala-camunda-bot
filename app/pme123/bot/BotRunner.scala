package pme123.bot

import akka.Done
import akka.actor.{ActorRef, ActorSystem, CoordinatedShutdown}
import akka.pattern.ask
import akka.util.Timeout
import info.mukel.telegrambot4s.api.declarative.{Callbacks, Commands}
import info.mukel.telegrambot4s.api.{Polling, TelegramBot}
import info.mukel.telegrambot4s.methods.SendMessage
import info.mukel.telegrambot4s.models._
import javax.inject.{Inject, Named, Singleton}
import play.api.Logging
import play.api.libs.json.{JsError, JsNull, JsSuccess, Json}
import pme123.bot.BotActor.{RegisterCallback, RegisterChatId, RequestCallback, RequestChatId}
import pme123.bot.camunda._

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.io.Source

class TelegramBoundary @Inject()(actorSystem: ActorSystem,
                                 @Named("bot-actor") botActor: ActorRef,
                                 camundaService: CamundaService,
                                 cs: CoordinatedShutdown,
                                )
  extends TelegramBot // the general bot behavior
    with Polling // we use Polling
    with Callbacks
    with Commands { // and we want to listen to Commands

  private val CALLBACK_TAG = "CALLBACK"
  private implicit val timeout: Timeout = Timeout(1.second)

  lazy val botToken: String = scala.util.Properties
    .envOrNone("BOT_TOKEN")
    .getOrElse(Source.fromResource("bot.token").getLines().mkString)

  lazy val token: String = botToken // the token is required by the Bot behavior

  actorSystem.scheduler.schedule(initialDelay = 1.seconds, interval = 1.seconds) {
    fetchAndProcessTasks()
  }

  private val workerId = "camunda-bot-scheduler"

  val botTaskTag = "botTask"

  private def fetchAndProcessTasks(): Future[Unit] = {
    camundaService.fetchAndLock(
      FetchAndLock(workerId,
        List(Topic("pme.telegram.demo", Seq(botTaskTag))))
    )
      .map { externalTasks =>
        if (externalTasks.nonEmpty)
          externalTasks.foreach(handleExternalTask)

      }.recover {
      case ex: Throwable => logger.error(s"Unable to fetch external tasks - $ex")
    }
  }

  private def handleExternalTask(externalTask: ExternalTask): Unit = {
    Json.parse(externalTask.variables(botTaskTag).value).validate[BotTask] match {
      case JsSuccess(botTask, _) =>
        sendMessage(externalTask.id, botTask)
      case JsError(errors) =>
        logger.error(s"BotTask could not be parsed: $errors")
    }
  }

  def sendMessage(taskId: String, botTask: BotTask): Unit = {
    for {
      chatId <- botActor ? RequestChatId(botTask.chatUserOrGroup)
      replyMarkup <- markupClaimed(botTask)
      _ <- request(SendMessage(
        ChatId(chatId.toString),
        botTask.msg,
        replyMarkup = replyMarkup
      ))
    } camundaService.completeTask(CompleteTask(taskId, workerId, Map.empty))
  }

  onCallbackWithTag(CALLBACK_TAG) { implicit cbq => // listens on all callbacks that START with TAG
    // Notification only shown to the user who pressed the button.
    ackCallback(None)
    // Or just ackCallback() - this is needed by Telegram!

    for {
      callbackId <- cbq.data //the data is the callback identifier without the TAG (the count in our case)
      msg <- cbq.message
    } /* do */ {
      println(s"request SENT")
      (for {
        maybeRC <- (botActor ? RequestCallback(callbackId.toLong)).mapTo[Option[RegisterCallback]]
        _ = println(s"REG_CALLBACK: $maybeRC")
        _ <- maybeRC match {
          case Some(regCallback) =>
            val botTaskResult = BotTaskResult(regCallback.botTaskIdent, regCallback.callbackId, User(cbq.from))
            camundaService.signal(Signal(regCallback.signal,
              Map("botTaskResult" ->
                Variable(Json.toJson(botTaskResult).toString)))
            )
          case None => Future.successful(JsNull)
        }
        req <- request(
          SendMessage(
            msg.source,
            if (maybeRC.isDefined)
              s"Thanks, ${cbq.from.firstName} claimed the issue ($callbackId)!"
            else
              s"Sorry, this issue ($callbackId) was claimed already!"
          )
        )
      } yield (req))
        .recover{
          case throwable: Throwable =>
            logger.error("Problem", throwable)
        }
    }
  }

  onCommand('register) { implicit msg =>
    for {
      result <- registerGroupOrUser(msg)
      _ <- reply(s"Hello ${msg.from.map(_.firstName).getOrElse("")}!\n" + result)
    } ()
  } // and reply with the personalized greeting

  private def registerGroupOrUser(msg: Message) = {
    (msg.chat.`type` match {
      case ChatType.Private =>
        msg.chat.username
      case ChatType.Group =>
        msg.chat.title
    }).map(botActor ? RegisterChatId(_, msg.chat.id))
      .map(_ => "you were successful registered")
      .getOrElse("Sorry, you need a Username to talk with me")
  }

  private def markupClaimed(botTask: BotTask): Future[Option[InlineKeyboardMarkup]] = {
    if (botTask.maybeCallback.isDefined) {
      val callback = botTask.maybeCallback.get
      Future.sequence(callback.controls.map { c =>
        (botActor ? RegisterCallback(botTask.ident, c.ident, callback.signal))
          .map(callbackStr =>
            InlineKeyboardButton.callbackData(
              c.text,
              CALLBACK_TAG + callbackStr)
          )
      })
        .map(InlineKeyboardMarkup.singleColumn)
        .map(Some.apply)
    } else Future.successful(None)
  }

  // Shut-down hook
  cs.addTask(
    CoordinatedShutdown.PhaseServiceUnbind,
    "free-telegram-polling") { () =>
    shutdown()
      .map(_ => Done)
  }
}

@Singleton
class BotRunner @Inject()(telegramBot: TelegramBoundary)
  extends Logging {
  logger.info("initialized TelegramBoundary")
  telegramBot.run()
}
