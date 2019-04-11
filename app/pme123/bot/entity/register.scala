package pme123.bot.entity

import pme123.bot.entity.bot._

object register {

  case class RegisterChatId(maybeId: Option[ChatUserOrGroup], chatId: ChatId)

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

  case class ResultCallback(botTaskIdent: String, signal: String, callbackId: String, response: String)

}