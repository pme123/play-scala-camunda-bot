package pme123.bot

import akka.Done
import akka.actor.{ActorSystem, CoordinatedShutdown}
import info.mukel.telegrambot4s.api.declarative.{Callbacks, Commands}
import info.mukel.telegrambot4s.api.{Polling, TelegramBot}
import info.mukel.telegrambot4s.methods.SendMessage
import info.mukel.telegrambot4s.models.{ChatId, ChatType, InlineKeyboardButton, InlineKeyboardMarkup}
import javax.inject.{Inject, Singleton}
import play.api.Logging
import play.api.libs.json.{JsError, JsSuccess, Json}
import pme123.bot.camunda._

import scala.collection.mutable
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.io.Source

class TelegramBoundary @Inject()(actorSystem: ActorSystem,
                                 camundaService: CamundaService,
                                 cs: CoordinatedShutdown,
                                )
  extends TelegramBot // the general bot behavior
    with Polling // we use Polling
    with Callbacks
    with Commands { // and we want to listen to Commands

  private val CALLBACK_TAG = "CALLBACK"
  private val camunda_group = "camunda_group"

  private val chatIdMap: mutable.Map[BotTask.ChatUserOrGroup, BotTask.ChatId] =
    mutable.Map(camunda_group -> -319641852, "pme123" -> 275469757)

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
    request(SendMessage(
     ChatId(chatIdMap.getOrElse(botTask.chatUserOrGroup, chatIdMap(camunda_group))),
      botTask.msg,
      replyMarkup = botTask.maybeCallback.map(markupClaimed(botTask.ident, _))
    ))
    camundaService.completeTask(CompleteTask(taskId, workerId, Map.empty))
  }

  onCallbackWithTag(CALLBACK_TAG) { implicit cbq => // listens on all callbacks that START with TAG
    // Notification only shown to the user who pressed the button.
    ackCallback(None)
    // Or just ackCallback() - this is needed by Telegram!

    for {
      botIdent <- cbq.data //the data is the callback identifier without the TAG (the count in our case)
      msg <- cbq.message
    } /* do */ {
      request(
        SendMessage(
          msg.source,
          s"Thanks, ${cbq.from.firstName} claimed the issue!"
        )
      )
      camundaService.signal(Signal("taskClaimed",
        Map("botTaskResult" -> Variable(Json.toJson(BotTaskResult(botIdent, cbq.from)).toString))))
    }

  }

  onCommand('register) { implicit msg =>
    val result =
      (msg.chat.`type` match {
        case ChatType.Private =>
          msg.chat.username
        case ChatType.Group =>
          msg.chat.title
      }).map(chatIdMap.put(_, msg.chat.id))

    reply(
      s"Hello ${msg.from.map(_.firstName).getOrElse("")}!\n" +
        result.map(_ => "you were successful registered")
          .getOrElse("Sorry, you need a Username to talk with me")
    )
    println(s"Registered Chats: $chatIdMap")
  } // and reply with the personalized greeting


  private def markupClaimed(botIdent: String, callback: Callback): InlineKeyboardMarkup = {
    InlineKeyboardMarkup.singleColumn( // set a layout for the Button
      callback.controls.map(c =>
        InlineKeyboardButton.callbackData( // create the button into the layout
          c.text, // text to show on the button (count of the times hitting the button and total request count)
          CALLBACK_TAG + s"$botIdent--${c.ident}"))) // create a callback identifier
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
