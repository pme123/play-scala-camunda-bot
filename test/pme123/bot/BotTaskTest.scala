package pme123.bot

import org.scalatest.FunSuite
import play.api.libs.json.Json

class BotTaskTest extends FunSuite {

  test("Json un-/marshalling") {
    val expected = BotTask("-319641852", "Hello there")
    val json = Json.toJson(expected)
    println(json)
    assert(expected == json.validate[BotTask].get)
  }

}
