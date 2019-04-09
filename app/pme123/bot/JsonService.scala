package pme123.bot

import play.api.libs.json.{Json, Reads, Writes}
import scalaz.zio.{UIO, ZIO}

import scala.util.Try

class JsonService {

  def fromJsonString[T](jsonStr: String)(implicit reader: Reads[T]) =
    ZIO.fromTry {
      Try(Json.parse(jsonStr).validate[T].get)
    }

  def toJson[T](obj: T)(implicit reader: Writes[T]) =
    UIO.succeed {
      Json.toJson(obj)
    }
}
