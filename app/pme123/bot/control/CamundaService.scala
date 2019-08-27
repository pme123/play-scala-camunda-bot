package pme123.bot.control

import javax.inject.Inject
import play.api.libs.json.{JsNull, JsValue, Json}
import play.api.libs.ws.{WSAuthScheme, WSClient}
import pme123.bot.entity.camunda._
import scalaz.zio.{Task, ZIO}

import scala.concurrent.ExecutionContext

class CamundaService @Inject()(ws: WSClient)
                              (implicit ec: ExecutionContext) {
  val camundaUrl = "http://localhost:8082/engine-rest"

  def fetchAndLock(fetchAndLock: FetchAndLock): Task[Seq[ExternalTask]] =
    post("external-task/fetchAndLock", Json.toJson(fetchAndLock))
      .map { json =>
        json.validate[Seq[ExternalTask]].get // make safe
      }

  def completeTask(completeTask: CompleteTask): Task[JsValue] =
    post(s"external-task/${completeTask.taskId}/complete", Json.toJson(completeTask))

  def signal(signal: Signal): Task[JsValue] =
    post(s"signal", Json.toJson(signal))


  private def post(path: String, body: JsValue): Task[JsValue] =
    ZIO.fromFuture { implicit ec =>
      ws.url(s"$camundaUrl/$path")
        .withAuth("demo", "demo", WSAuthScheme.BASIC)
        .post(body)
        .map {
          case b if b.body.isEmpty => JsNull
          case b => b.json
        }
    }
}

