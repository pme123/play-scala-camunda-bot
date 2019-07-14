package pme123.bot.control

import akka.actor.Actor
import play.api.Logging
import pme123.bot.entity.bot._
import pme123.bot.entity.register._

class BotActor extends Actor with Logging {


  private val camunda_group = "camunda_group"


  def receive: Receive =
    running(
      Map(camunda_group -> -319641852, "pme123" -> 275469757),
      Map.empty,
    )

  def running(chatIdMap: Map[ChatUserOrGroup, ChatId],
              callbackIdMap: Map[String, RegisterCallback]): Receive = {

    case RegisterChatId(maybeId, chatId) =>
      //noinspection UnitInMap
      sender ! maybeId
        .map(chatUserOrGroup => context.become(running(chatIdMap + (chatUserOrGroup -> chatId), callbackIdMap)))
        .map(_ => "you were successful registered")
        .getOrElse("Sorry, you need a Username to talk with me")

    case RequestChatId(chatUserOrGroup) =>
      sender ! chatIdMap.getOrElse(chatUserOrGroup, chatIdMap(camunda_group))

    case Some(rc: RegisterCallback) =>
      context.become(running(chatIdMap, callbackIdMap + (rc.botTaskIdent -> rc)))
      sender ! Some((rc.botTaskIdent, rc.callback))

    case rc: RequestCallback =>
      sender !
        (for {
          reg <- callbackIdMap.get(rc.requestId)
          control <- reg.callback.controls.find(_.ident == rc.callbackId)
        } yield
          ResultCallback(reg.botTaskIdent, reg.callback.signal, control.ident, control.response))

      context.become(running(chatIdMap, callbackIdMap.filterNot(_._1 == rc.requestId)))

    case OpenTasksRequest(maybeId) =>
      sender !
        (for {
          id <- maybeId.toSeq
          rc <- callbackIdMap.values
          if rc.userId == id
        } yield
         rc.botTaskIdent)

    case None =>
      sender ! None
    case other =>
      logger.info(s"Unhandled message: $other")
  }
}
