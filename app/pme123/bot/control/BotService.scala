package pme123.bot.control

import info.mukel.telegrambot4s.api.RequestHandler
import info.mukel.telegrambot4s.methods.{AnswerCallbackQuery, SendMessage}
import info.mukel.telegrambot4s.models._
import javax.inject.Inject
import play.api.Logging
import pme123.bot.entity.bot
import pme123.bot.entity.bot.Callback
import pme123.bot.entity.register.ResultCallback
import scalaz.zio.{Task, UIO, ZIO}

import scala.concurrent.Future

class BotService @Inject()()
  extends Logging {

  def sendMessage(chatId: bot.ChatId,
                  maybeRCs: Option[(String, Callback)],
                  msg: String
                 )(implicit request: RequestHandler): ZIO[Any, Throwable, Message] =
    for {
      replyMarkup <- claimedCallback(maybeRCs)
      botMsg <- requestMsg(chatId, msg)(replyMarkup)
    } yield botMsg

  def handleReply(chatId: ChatId, replyMsg: String
                 )(implicit request: RequestHandler): Task[Message] =
    ZIO.fromFuture(implicit ec =>
      request(SendMessage(
        chatId,
        replyMsg
      ))
    )


  def handleCallback(cbq: CallbackQuery,
                     maybeRC: Option[ResultCallback],
                    )(implicit request: RequestHandler): Task[Seq[Unit]] =
    ZIO.fromFuture(implicit ec =>
      Future.sequence {
        (for {
          callbackIdent <- cbq.data
          msg <- cbq.message
        } yield {
          for {
            _ <- request(
              SendMessage(
                msg.source,
                maybeRC match {
                  case Some(regCallback: ResultCallback) =>
                    TextTemplateEngine.generate(cbq.from.username.get, regCallback.response, regCallback.botTaskIdent)
                  case None =>
                    s"Sorry, this issue (${bot.extractRequestId(callbackIdent)}) was claimed already!"
                })
            )
          } yield ()
        }).toSeq
      }
    )

  def handleAnswerCallback(cbq: CallbackQuery,
                           msg: String
                          )(implicit request: RequestHandler): Task[Boolean] =
    ZIO.fromFuture(implicit ec =>
      request(AnswerCallbackQuery(cbq.id, Some(msg)))
    )


  def claimedCallback(maybeRegCallback: Option[(String, Callback)]): UIO[Option[(String, InlineKeyboardMarkup)]] =
    UIO.succeed {
      maybeRegCallback map { case (requestId, callback) =>
        (requestId, InlineKeyboardMarkup.singleColumn(
          callback.controls.map { c =>
            InlineKeyboardButton.callbackData(
              c.text,
              bot.CALLBACK_TAG + bot.createCallbackIdent(requestId, c.ident)
            )
          }))
      }
    }

  private def requestMsg(chatId: bot.ChatId, msg: String
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

