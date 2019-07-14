package pme123.bot.control

import akka.actor.ActorRef
import akka.pattern.ask
import javax.inject.{Inject, Named}
import pme123.bot.entity.bot._
import pme123.bot.entity.register._
import scalaz.zio.{Task, ZIO}

class RegisterService @Inject()(@Named("bot-actor") botActor: ActorRef) {
  val camundaUrl = "http://localhost:8080/engine-rest"

  def registerChat(maybeId: Option[ChatUserOrGroup], chatId: ChatId): Task[Any] =
    ZIO.fromFuture { implicit ec =>
      botActor ? RegisterChatId(maybeId, chatId)
    }

  def myTasks(maybeId: Option[ChatUserOrGroup], chatId: ChatId): Task[Seq[String]] =
    ZIO.fromFuture { implicit ec =>
      println(s"maybeIdmaybeId: $maybeId")
      (botActor ? OpenTasksRequest(maybeId)).mapTo[Seq[String]]
    }

  def requestChat(chatUserOrGroup: ChatUserOrGroup): Task[ChatId] =
    ZIO.fromFuture { implicit ec =>
      (botActor ? RequestChatId(chatUserOrGroup)).mapTo[ChatId]
    }

  def registerCallback(botTask: BotTask): Task[Option[(String, Callback)]] =
    ZIO.fromFuture { implicit ec =>
      (botActor ? RegisterCallback(botTask)).mapTo[Option[(String, Callback)]]
    }

  def requestCallback(callbackIdent: String): Task[Option[ResultCallback]] =
    ZIO.fromFuture { implicit ec =>
      (botActor ? RequestCallback(callbackIdent)).mapTo[Option[ResultCallback]]
    }
}

