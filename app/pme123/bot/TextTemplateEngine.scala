package pme123.bot

import info.mukel.telegrambot4s.models.CallbackQuery
import org.fusesource.scalate.TemplateEngine
import pme123.bot.BotActor.ResultCallback

object TextTemplateEngine {

  val engine = new TemplateEngine()

  def generate(cbq: CallbackQuery, resultCallback: ResultCallback): String =
    generate(resultCallback.response, TemplateProps(cbq, resultCallback).toMap())

  def generate(text: String, templParams: Map[String, String]): String = {
    val template = engine.compileMoustache(text)
    engine.layout("notused", template, templParams)
  }

}

case class TemplateProps(cbq: CallbackQuery, resultCallback: ResultCallback) {
  def toMap() =
    Map("username" -> cbq.from.username.get,
      "botTaskIdent" -> resultCallback.botTaskIdent)
}
