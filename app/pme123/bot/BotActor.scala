package pme123.bot

import akka.actor.Actor
import play.api.Logging

class BotActor extends Actor with Logging{

  import BotActor._

  private val camunda_group = "camunda_group"

  private var chatIdMap: Map[BotTask.ChatUserOrGroup, BotTask.ChatId] =
    Map(camunda_group -> -319641852, "pme123" -> 275469757)

  private var callbackIdMap: Map[String, RegisterCallback] = Map.empty

  def receive: Receive = {
    case RegisterChatId(chatUserOrGroup, chatId) =>
      chatIdMap += (chatUserOrGroup -> chatId)
      sender ! "ok"
    case RequestChatId(chatUserOrGroup) =>
      sender ! chatIdMap.getOrElse(chatUserOrGroup, chatIdMap(camunda_group))
    case Some(rc: RegisterCallback) =>
      callbackIdMap += (rc.botTaskIdent -> rc)
      sender ! Some((rc.botTaskIdent, rc.callback))
    case rc:RequestCallback =>
      sender ! callbackIdMap.get(rc.requestId).map(reg =>
        ResultCallback(reg.botTaskIdent, reg.callback.signal, rc.callbackId)
      )
      callbackIdMap = callbackIdMap.filterNot(_._1 == rc.requestId)
    case None =>
      sender ! None
    case other =>
    logger.info(s"Unhandled message: $other")
  }
}

object BotActor {

  import BotTask._

  case class RegisterChatId(chatUserOrGroup: ChatUserOrGroup, chatId: ChatId)

  case class RequestChatId(chatUserOrGroup: ChatUserOrGroup)

  case class RegisterCallback(botTaskIdent: String, callback: Callback)

  object RegisterCallback {
    def apply(botTask: BotTask): Option[RegisterCallback] = {
      botTask.maybeCallback.map(callback =>
        RegisterCallback(botTask.ident, callback)
      )
    }
  }

  case class RequestCallback(callbackIdent: String) {
    val requestId: String = extractRequestId(callbackIdent)
    val callbackId: String = extractCallbackId(callbackIdent)
  }

  case class ResultCallback(botTaskIdent: String, signal: String, callbackId: String)

}