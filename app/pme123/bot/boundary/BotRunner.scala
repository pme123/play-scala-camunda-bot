package pme123.bot.boundary

import akka.Done
import akka.actor.CoordinatedShutdown
import info.mukel.telegrambot4s.api.Polling
import info.mukel.telegrambot4s.api.declarative.{Callbacks, Commands}
import javax.inject.{Inject, Singleton}
import pme123.bot.control._
import pme123.bot.entity.bot
import pme123.bot.entity.bot._
import pme123.bot.entity.camunda._
import scalaz.zio.ZIO


@Singleton
class BotRunner @Inject()(
                           registerService: RegisterService,
                           camundaService: CamundaService,
                           botService: BotService,
                           jsonService: JsonService,
                           cs: CoordinatedShutdown,
                         )
  extends CamundaBot
    with UnsafeRunner
    with Polling
    with Callbacks
    with Commands {

  logger.info("initialized TelegramBoundary")
  run()

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

  // Shut-down hook
  cs.addTask(
    CoordinatedShutdown.PhaseServiceUnbind,
    "free-telegram-polling") { () =>
    shutdown()
      .map(_ => Done)
  }
}
