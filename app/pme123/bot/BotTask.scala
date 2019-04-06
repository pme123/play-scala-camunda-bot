package pme123.bot

import play.api.libs.json.{Json, OFormat}
import info.mukel.telegrambot4s.models.{User => TelegramUser}
import BotTask._

case class BotTask(ident: String, chatUserOrGroup: ChatUserOrGroup, msg: String, maybeCallback: Option[Callback]) {

}

object BotTask {

  type ChatUserOrGroup = String
  type ChatId = Long

  implicit val jsonFormat: OFormat[BotTask] = Json.format[BotTask]
}

case class Callback(signal: String, controls: Seq[Control]) {

}

object Callback {
  implicit val jsonFormat: OFormat[Callback] = Json.format[Callback]
}

case class Control(ident: String, text: String) {

}

object Control {
  implicit val jsonFormat: OFormat[Control] = Json.format[Control]
}

case class BotTaskResult(botTaskIdent: String, callbackIdent: String, from: User) {

}

object BotTaskResult {

    implicit val jsonFormat: OFormat[BotTaskResult] = Json.format[BotTaskResult]
}

case class User(id: Int, firstName: String, lastName: Option[String], username: Option[String]) {

}

object User {

  def apply(botUser: TelegramUser): User =
    User(botUser.id, botUser.firstName, botUser.lastName, botUser.username)

  implicit val jsonFormat: OFormat[User] = Json.format[User]
}

