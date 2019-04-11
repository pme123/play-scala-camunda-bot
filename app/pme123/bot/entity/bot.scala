package pme123.bot.entity

import akka.util.Timeout
import info.mukel.telegrambot4s.models.{User => TelegramUser}
import play.api.libs.json.{Json, OFormat}
import scala.concurrent.duration._

object bot {

  type ChatUserOrGroup = String
  type ChatId = Long

  implicit val timeout: Timeout = Timeout(1.second)

  val CALLBACK_TAG = "CALLBACK"

  def createCallbackIdent(requestId: String, callbackId: String) =
    s"$requestId--$callbackId"

  def extractCallbackIdent(callbackIdent: String): (String, String) = {
    val requestIdStr :: callbackId :: Nil = callbackIdent.split("--").toList
    (requestIdStr, callbackId)
  }

  def extractCallbackId(callbackIdent: String): String =
    extractCallbackIdent(callbackIdent)._2

  def extractRequestId(callbackIdent: String): String =
    extractCallbackIdent(callbackIdent)._1


  case class BotTask(ident: String, chatUserOrGroup: ChatUserOrGroup, msg: String, maybeCallback: Option[Callback]) {
  }

  object BotTask {

    implicit val jsonFormat: OFormat[BotTask] = Json.format[BotTask]
  }

  case class Callback(signal: String, controls: Seq[Control]) {

  }

  object Callback {
    implicit val jsonFormat: OFormat[Callback] = Json.format[Callback]
  }

  case class Control(ident: String, text: String, response: String) {

  }

  object Control {
    implicit val jsonFormat: OFormat[Control] = Json.format[Control]
  }

  case class BotTaskResult(botTaskIdent: String, callbackIdent: String, from: User) {
  }

  object BotTaskResult {

    implicit val jsonFormat: OFormat[BotTaskResult] = Json.format[BotTaskResult]
  }

  case class User(id: Int, firstName: String, lastName: Option[String], username: String) {

  }

  object User {

    def apply(botUser: TelegramUser): User =
      User(botUser.id, botUser.firstName, botUser.lastName, botUser.username.getOrElse("--"))

    implicit val jsonFormat: OFormat[User] = Json.format[User]
  }

  case class Receipt(value: Map[String, Option[Throwable]]) {
    self =>
    final def |+|(that: Receipt): Receipt =
      Receipt(self.value ++ that.value)

    final def succeeded: Int = value.values.count(_.isEmpty)

    final def failures: List[Throwable] =
      value.values.collect { case Some(t) => t }.toList
  }

  object Receipt {
    def empty: Receipt = Receipt(Map())

    def success(id: String): Receipt = Receipt(Map(id -> None))

    def failure(id: String, t: Throwable): Receipt =
      Receipt(Map(id -> Some(t)))
  }

}