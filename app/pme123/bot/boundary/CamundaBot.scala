package pme123.bot.boundary

import info.mukel.telegrambot4s.api.{RequestHandler, TelegramBot}
import info.mukel.telegrambot4s.models.{ChatType, Message}
import play.api.Logging

import scala.io.Source

trait CamundaBot
  extends TelegramBot{

  lazy val token: String = scala.util.Properties
    .envOrNone("BOT_TOKEN")
    .getOrElse(Source.fromResource("bot.token").getLines().mkString)

  override implicit val request: RequestHandler = super.request

  protected def maybeUserOrGroup(msg: Message): Option[String] = {
    msg.chat.`type` match {
      case ChatType.Private =>
        msg.chat.username
      case ChatType.Group =>
        msg.chat.title
    }
  }
}
