package pme123.bot

import akka.Done
import akka.actor.{ActorRef, ActorSystem, CoordinatedShutdown}
import akka.pattern.ask
import info.mukel.telegrambot4s.api.declarative.{Callbacks, Commands}
import info.mukel.telegrambot4s.api.{Polling, RequestHandler, TelegramBot}
import info.mukel.telegrambot4s.models._
import javax.inject.{Inject, Named, Singleton}
import org.fusesource.scalate.util.Logging
import pme123.bot.BotActor.RegisterChatId
import pme123.bot.camunda._
import scalaz.zio.{DefaultRuntime, UIO, ZIO}

import scala.concurrent.duration._
import scala.io.Source

class TelegramBoundary @Inject()(actorSystem: ActorSystem,
                                 @Named("bot-actor") botActor: ActorRef,
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

  override implicit val request: RequestHandler = super.request
  actorSystem.scheduler.schedule(initialDelay = 1.seconds, interval = 1.seconds) {
    runtime.unsafeRun(fetchAndProcessTasks.fold(t => error(t), r => info(s"Receipt is $r")))
  }

  private val workerId = "camunda-bot-scheduler"

  val botTaskTag = "botTask"

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


  private def handleExternalTask(externalTask: ExternalTask): ZIO[Any, Throwable, Unit] =
    for {
      botTask <- jsonService.fromJsonString[BotTask](externalTask.variables(botTaskTag).value)
      _ <- botService.sendMessage(botTask)
      _ <- camundaService.completeTask(CompleteTask(externalTask.id, workerId, Map.empty))
    } yield ()

  onCallbackWithTag(CALLBACK_TAG) { implicit cbq => // listens on all callbacks that START with TAG
    runtime.unsafeRun((for {
      _ <- botService.handleCallback(cbq, "processing...")
      maybeRC <- botService.handleCallback(cbq)
      _ <- maybeRC.map { regCallback =>
        val botTaskResult = BotTaskResult(regCallback.botTaskIdent, regCallback.callbackId, User(cbq.from))
        for {
          json <- jsonService.toJson(botTaskResult)
          _ <- camundaService.signal(
            Signal(regCallback.signal,
              Map("botTaskResult" -> Variable(json.toString)))
          )
        } yield ()
      }.getOrElse(UIO.succeed())

    } yield ())
      .fold(t => logger.error("Problem on Callback", t), _ => info("Callback handled"))
    )
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
  info("initialized TelegramBoundary")
  telegramBot.run()
}
