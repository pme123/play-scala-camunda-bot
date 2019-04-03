package pme123.bot

import play.api.libs.json.{Json, OFormat}

case class BotTask(chatId: String, msg: String) {

}

object BotTask {
  implicit val jsonFormat: OFormat[BotTask] = Json.format[BotTask]
}

case class BotTaskResult(processInstanceId: String) {

}

object BotTaskResult {
  implicit val jsonFormat: OFormat[BotTaskResult] = Json.format[BotTaskResult]
}

