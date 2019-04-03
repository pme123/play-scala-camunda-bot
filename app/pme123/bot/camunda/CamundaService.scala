package pme123.bot.camunda

import javax.inject.Inject
import play.api.libs.json.{JsValue, Json}
import play.api.libs.ws.{WSAuthScheme, WSClient}

import scala.concurrent.ExecutionContext

class CamundaService @Inject()(ws: WSClient)
                              (implicit ec: ExecutionContext) {
  val camundaUrl = "http://localhost:8080/engine-rest"

  def fetchAndLock(fetchAndLock: FetchAndLock) = {
    post("external-task/fetchAndLock", Json.toJson(fetchAndLock))
      .map { json =>
        json.validate[Seq[ExternalTask]].get // make safe
      }
  }

  def completeTask(completeTask: CompleteTask) = {
    post(s"external-task/${completeTask.taskId}/complete", Json.toJson(completeTask))
  }

  def signal(signal: Signal) = {
    post(s"signal", Json.toJson(signal))
  }

  private def post(path: String, body: JsValue) = {
    ws.url(s"$camundaUrl/$path")
      .withAuth("demo", "demo", WSAuthScheme.BASIC)
      .post(body)
      .map(_.json)
  }
}

