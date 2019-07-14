package pme123.bot.boundary

import akka.actor.ActorSystem
import javax.inject.{Inject, Singleton}
import pme123.bot.control.{BotService, CamundaService, JsonService, RegisterService}
import pme123.bot.entity.bot.{BotTask, Receipt}
import pme123.bot.entity.camunda.{CompleteTask, ExternalTask, FetchAndLock, Topic}
import scalaz.zio.ZIO

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

@Singleton
class CamundaRunner @Inject()(actorSystem: ActorSystem,
                              registerService: RegisterService,
                              camundaService: CamundaService,
                              botService: BotService,
                              jsonService: JsonService,
                             )(
                               implicit ec: ExecutionContext,
                             )
  extends CamundaBot
    with UnsafeRunner {

  private val workerId = "camunda-bot-scheduler"
  private val botTaskTag = "botTask"

  actorSystem.scheduler.schedule(initialDelay = 1.seconds, interval = 1.seconds) {
    run(fetchAndProcessTasks)
  }

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


}
