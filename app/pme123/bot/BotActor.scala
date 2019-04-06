package pme123.bot

import akka.actor.Actor

class BotActor extends Actor {

  import BotActor._

  private val camunda_group = "camunda_group"

  private var chatIdMap: Map[BotTask.ChatUserOrGroup, BotTask.ChatId] =
    Map(camunda_group -> -319641852, "pme123" -> 275469757)

  private var callbackId: Long = 0
  private var callbackIdMap: Map[Long, RegisterCallback] = Map.empty

  def receive: Receive = {
    case RegisterChatId(chatUserOrGroup, chatId) =>
      chatIdMap += (chatUserOrGroup -> chatId)
      sender ! "ok"
    case RequestChatId(chatUserOrGroup) =>
      sender ! chatIdMap.getOrElse(chatUserOrGroup, chatIdMap(camunda_group))
    case rc: RegisterCallback =>
      callbackId += 1
      callbackIdMap += (callbackId -> rc)
      sender ! callbackId
    case RequestCallback(callbackIdent) =>
      sender ! callbackIdMap.get(callbackIdent)
      callbackIdMap = callbackIdMap.filterNot(_._1 == callbackIdent)
  }
}

object BotActor {

  import BotTask._

  case class RegisterChatId(chatUserOrGroup: ChatUserOrGroup, chatId: ChatId)

  case class RequestChatId(chatUserOrGroup: ChatUserOrGroup)

  case class RegisterCallback(botTaskIdent: String, callbackId: String, signal: String)

  case class RequestCallback(callbackIdent: Long)


}