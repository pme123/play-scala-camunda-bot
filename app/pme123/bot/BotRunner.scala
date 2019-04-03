package pme123.bot

import akka.Done
import akka.actor.{ActorSystem, CoordinatedShutdown}
import info.mukel.telegrambot4s.api.declarative.{Callbacks, Commands}
import info.mukel.telegrambot4s.api.{Polling, TelegramBot}
import info.mukel.telegrambot4s.methods.SendMessage
import info.mukel.telegrambot4s.models.{ChatId, InlineKeyboardButton, InlineKeyboardMarkup}
import javax.inject.{Inject, Singleton}
import play.api.Logging
import play.api.libs.json.{JsError, JsSuccess, Json}
import pme123.bot.camunda.{CamundaService, CompleteTask, ExternalTask, FetchAndLock, Signal, Topic, Variable}

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
  private val CLAIM_TAG = "CLAIM_IT"

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
        sendMessage(botTask, externalTask.processInstanceId)
      case JsError(errors) =>
        logger.error(s"BotTask could not be parsed: $errors")
    }
  }

  def sendMessage(botTask: BotTask, processInstanceId: String): Unit = {
    request(SendMessage(
      botTask.chatId,
      botTask.msg,
      replyMarkup = Some(markupClaimed(processInstanceId))
    ))
    camundaService.completeTask(CompleteTask(processInstanceId, workerId, Map.empty))
  }

  onCallbackWithTag(CLAIM_TAG) { implicit cbq => // listens on all callbacks that START with TAG
    // Notification only shown to the user who pressed the button.
    ackCallback(None)
    // Or just ackCallback() - this is needed by Telegram!

    for {
      processInstanceId <- cbq.data //the data is the callback identifier without the TAG (the count in our case)
      msg <- cbq.message
    } /* do */ {
      request(
        SendMessage(
          ChatId(msg.source),
          s"Thanks, ${cbq.from.firstName} claimed the issue: $processInstanceId!"
        )
      )
      camundaService.signal(Signal("taskClaimed", Map("botTaskResult" -> Variable(Json.toJson(BotTaskResult(processInstanceId)).toString), "user" -> Variable(cbq.from.username.get))))
    }

  }


  private def markupClaimed(processInstanceId: String): InlineKeyboardMarkup = {
    println("OK: " + CLAIM_TAG + processInstanceId)
    InlineKeyboardMarkup.singleButton( // set a layout for the Button
      InlineKeyboardButton.callbackData( // create the button into the layout
        s"Please claim it", // text to show on the button (count of the times hitting the button and total request count)
        CLAIM_TAG + processInstanceId)) // create a callback identifier
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
