import com.google.inject.AbstractModule
import play.libs.akka.AkkaGuiceSupport
import pme123.bot.{BotHandler, BotRunner}

class Module extends AbstractModule with AkkaGuiceSupport {

  override def configure(): Unit = {


    bind(classOf[BotRunner]).asEagerSingleton()
    // API
 //   bind(classOf[BotHandler])
 //     .asEagerSingleton()

}}
