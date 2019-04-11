package pme123.bot.control

import play.api.libs.json.{JsValue, Json, Reads, Writes}
import scalaz.zio.{Task, UIO, ZIO}

import scala.util.Try

class JsonService {

  def fromJsonString[T](jsonStr: String)(implicit reader: Reads[T]): Task[T] =
    ZIO.fromTry {
      Try(Json.parse(jsonStr).validate[T].get)
    }

  def toJson[T](obj: T)(implicit reader: Writes[T]): UIO[JsValue] =
    UIO.succeed {
      Json.toJson(obj)
    }
}
