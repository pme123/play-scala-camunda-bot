package pme123.bot.boundary

import play.api.Logger
import scalaz.zio.{DefaultRuntime, ZIO}

trait UnsafeRunner {

  private val runtime = new DefaultRuntime {}

  protected def run(block: => ZIO[Any, Throwable, Any]): Unit =
    runtime.unsafeRun(block.catchAll { t =>
      Logger(this.getClass).error("Exception running block", t)
      ZIO.fail(t)
    })
}
