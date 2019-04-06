import com.google.inject.AbstractModule
import play.api.libs.concurrent.AkkaGuiceSupport
import pme123.bot.{BotActor, BotRunner}

class Module
  extends AbstractModule
    with AkkaGuiceSupport {

  override def configure(): Unit = {


    bind(classOf[BotRunner]).asEagerSingleton()

    bindActor[BotActor]("bot-actor")


  }
}
