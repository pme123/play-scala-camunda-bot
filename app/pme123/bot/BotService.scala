package pme123.bot

import akka.actor.ActorRef
import akka.pattern.ask
import info.mukel.telegrambot4s.api.RequestHandler
import info.mukel.telegrambot4s.methods.{AnswerCallbackQuery, SendMessage}
import info.mukel.telegrambot4s.models.{CallbackQuery, ChatId, InlineKeyboardButton, InlineKeyboardMarkup, Message}
import javax.inject.{Inject, Named}
import play.api.Logging
import pme123.bot.BotActor.{RegisterCallback, RequestCallback, RequestChatId, ResultCallback}
import scalaz.zio.{Task, ZIO}

import scala.concurrent.Future

class BotService @Inject()(@Named("bot-actor") botActor: ActorRef)
  extends Logging {

  def sendMessage(botTask: BotTask
                 )(implicit request: RequestHandler): ZIO[Any, Throwable, Message] =
    for {
      chatId <- ZIO.fromFuture(implicit ec => (botActor ? RequestChatId(botTask.chatUserOrGroup)).mapTo[BotTask.ChatId])
      replyMarkup <- markupClaimed(botTask)
      botMsg <- requestMsg(chatId, botTask.msg)(replyMarkup)
    } yield botMsg


  def handleCallback(cbq: CallbackQuery
                    )(implicit request: RequestHandler): Task[Option[ResultCallback]] =
    ZIO.fromFuture(implicit ec =>
      Future.sequence {
        (for {
          callbackIdent <- cbq.data
          msg <- cbq.message
        } yield {
          for {
            maybeRC <- (botActor ? RequestCallback(callbackIdent)).mapTo[Option[ResultCallback]]
            _ <- request(
              SendMessage(
                msg.source,
                maybeRC match {
                  case Some(regCallback: ResultCallback) =>
                    TextTemplateEngine.generate(cbq, regCallback)
                  case None =>
                    s"Sorry, this issue (${extractRequestId(callbackIdent)}) was claimed already!"
                })
            )
          } yield maybeRC
        }).toSeq
      }.map(_.flatten.headOption)
    )

  def handleCallback(cbq: CallbackQuery,
                     msg: String
                    )(implicit request: RequestHandler): Task[Boolean] =
    ZIO.fromFuture(implicit ec =>
      request(AnswerCallbackQuery(cbq.id, Some(msg)))
    )


  private def markupClaimed(botTask: BotTask) =
    ZIO.fromFuture { implicit ec =>
      (botActor ? RegisterCallback(botTask)).mapTo[Option[(String, Callback)]]
        .map {
          case Some((requestId, callback)) =>
            Some(callback.controls.map { c =>
              InlineKeyboardButton.callbackData(
                c.text,
                CALLBACK_TAG + createCallbackIdent(requestId, c.ident)
              )
            }).map(seq => (requestId, InlineKeyboardMarkup.singleColumn(seq)))
          case None =>
            None
        }.recover {
        case throwable: Throwable =>
          logger.error("Problem", throwable)
          None
      }
    }

  private def requestMsg(chatId: BotTask.ChatId, msg: String
                        )(replyMarkup: Option[(String, InlineKeyboardMarkup)]
                        )(implicit request: RequestHandler) =
    ZIO.fromFuture(implicit ec =>
      request(SendMessage(
        ChatId(chatId.toString),
        msg,
        replyMarkup = replyMarkup.map { case (_, markup) => markup }
      ))
    )

}

