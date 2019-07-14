import com.google.inject.AbstractModule
import play.api.libs.concurrent.AkkaGuiceSupport
import pme123.bot.boundary.{BotRunner, CamundaRunner}
import pme123.bot.control.BotActor

class Module
  extends AbstractModule
    with AkkaGuiceSupport {

  override def configure(): Unit = {


    bind(classOf[BotRunner]).asEagerSingleton()
    bind(classOf[CamundaRunner]).asEagerSingleton()

    bindActor[BotActor]("bot-actor")


  }
}
