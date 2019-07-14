package pme123.bot.boundary

import akka.Done
import akka.actor.{ActorSystem, CoordinatedShutdown}
import info.mukel.telegrambot4s.api.declarative.{Callbacks, Commands}
import info.mukel.telegrambot4s.api.{Polling, RequestHandler, TelegramBot}
import info.mukel.telegrambot4s.models.{ChatType, Message}
import javax.inject.{Inject, Singleton}
import play.api.Logging
import pme123.bot.control._
import pme123.bot.entity.bot
import pme123.bot.entity.bot._
import pme123.bot.entity.camunda._
import scalaz.zio.{DefaultRuntime, ZIO}

import scala.concurrent.duration._
import scala.io.Source

class TelegramBoundary @Inject()(actorSystem: ActorSystem,
                                 registerService: RegisterService,
                                 camundaService: CamundaService,
                                 botService: BotService,
                                 jsonService: JsonService,
                                 cs: CoordinatedShutdown,
                                )
  extends TelegramBot
    with Polling
    with Callbacks
    with Commands
    with Logging {

  private val runtime = new DefaultRuntime {}

  lazy val botToken: String = scala.util.Properties
    .envOrNone("BOT_TOKEN")
    .getOrElse(Source.fromResource("bot.token").getLines().mkString)

  lazy val token: String = botToken // the token is required by the Bot behavior
  private val workerId = "camunda-bot-scheduler"
  private val botTaskTag = "botTask"

  override implicit val request: RequestHandler = super.request

  actorSystem.scheduler.schedule(initialDelay = 1.seconds, interval = 1.seconds) {
    run(fetchAndProcessTasks)
  }

  onCallbackWithTag(CALLBACK_TAG) { implicit cbq => // listens on all callbacks that START with TAG
    run {
      for {
        _ <- botService.handleAnswerCallback(cbq, "processing...")
        maybeRC <- registerService.requestCallback(cbq.data.getOrElse("---"))
        _ <- botService.handleCallback(cbq, maybeRC)
        _ <- maybeRC.map { regCallback =>
          val botTaskResult = BotTaskResult(regCallback.botTaskIdent, regCallback.callbackId, bot.User(cbq.from))
          for {
            json <- jsonService.toJson(botTaskResult)
            _ <- camundaService.signal(
              Signal(regCallback.signal,
                Map("botTaskResult" -> Variable(json.toString)))
            )
          } yield ()
        }.getOrElse(ZIO.succeed("ok"))

      } yield ()
    }
  }

  onCommand('register) { implicit msg =>
    run {
      for {
        result <- registerService.registerChat(maybeUserOrGroup(msg), msg.chat.id)
        _ <- botService.handleReply(msg.source, s"Hello ${msg.from.map(_.firstName).getOrElse("")}!\n" + result)
      } yield ()
    }
  }

  onCommand('mytasks) { implicit msg =>
    run {
      for {
        result <- registerService.myTasks(maybeUserOrGroup(msg), msg.chat.id)
        text = if (result.nonEmpty) result.mkString("\n") else "No open Tasks"
        _ <- botService.handleReply(msg.source, text)
      } yield ()
    }
  }

  private def run(block: => ZIO[Any, Throwable, Any]): Unit =
    runtime.unsafeRun(block.catchAll { t =>
      logger.error("Exception running block", t)
      ZIO.fail(t)
    })

  private def handleExternalTask(externalTask: ExternalTask): ZIO[Any, Throwable, Unit] =
    for {
      botTask <- jsonService.fromJsonString[BotTask](externalTask.variables(botTaskTag).value)
      chatId <- registerService.requestChat(botTask.chatUserOrGroup)
      maybeRCs <- registerService.registerCallback(botTask)
      _ <- botService.sendMessage(chatId, maybeRCs, botTask.msg)
      _ <- camundaService.completeTask(CompleteTask(externalTask.id, workerId, Map.empty))
    } yield ()

  private lazy val fetchAndProcessTasks: ZIO[Any, Throwable, Receipt] =
    for {
      externalTasks <- camundaService.fetchAndLock(FetchAndLock(workerId, List(Topic("pme.telegram.demo", Seq(botTaskTag)))))
      receipts <- ZIO.foreachParN(5)(externalTasks)(task =>
        handleExternalTask(task)
          .fold(
            t => Receipt.failure(task.id, t),
            _ => Receipt.success(task.id)
          ))
    } yield receipts.foldLeft(Receipt.empty)(_ |+| _)

  private def maybeUserOrGroup(msg: Message) = {
    msg.chat.`type` match {
      case ChatType.Private =>
        msg.chat.username
      case ChatType.Group =>
        msg.chat.title
    }
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
