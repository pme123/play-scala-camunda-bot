package pme123.bot

import info.mukel.telegrambot4s.api.{Polling, TelegramBot}
import info.mukel.telegrambot4s.api.declarative.{Callbacks, Commands}
import info.mukel.telegrambot4s.methods.SendMessage
import info.mukel.telegrambot4s.models.{ChatId, InlineKeyboardButton, InlineKeyboardMarkup}

import scala.io.Source

object TestBot
  extends TelegramBot // the general bot behavior
    with Polling // we use Polling
    with Callbacks
    with Commands { // and we want to listen to Commands
  private val CLAIM_TAG = "CLAIM_IT"

  lazy val botToken: String = scala.util.Properties
    .envOrNone("BOT_TOKEN")
    .getOrElse(Source.fromResource("bot.token").getLines().mkString)

  lazy val token: String = botToken // the token is required by the Bot behavior


  request(SendMessage(
    "-319641852",
    "what's up",
    replyMarkup = Some(markupClaimed("T1231"))
  ))


  onCallbackWithTag(CLAIM_TAG) { implicit cbq => // listens on all callbacks that START with TAG
    // Notification only shown to the user who pressed the button.
    ackCallback(None)
    // Or just ackCallback() - this is needed by Telegram!

    for {
      data <- cbq.data //the data is the callback identifier without the TAG (the count in our case)
      msg <- cbq.message
    } /* do */ {
      request(
        SendMessage(
          ChatId(msg.source),
          s"Thanks, ${cbq.from.firstName} claimed the issue: $data!"
        )
      )
    }

  }


  private def markupClaimed(taskId: String): InlineKeyboardMarkup = {
    println("OK: " + CLAIM_TAG + taskId)
    InlineKeyboardMarkup.singleButton( // set a layout for the Button
      InlineKeyboardButton.callbackData( // create the button into the layout
        s"Please claim it", // text to show on the button (count of the times hitting the button and total request count)
        CLAIM_TAG + taskId)) // create a callback identifier
  }




}

object BotApp extends App {
  TestBot.run()
}

