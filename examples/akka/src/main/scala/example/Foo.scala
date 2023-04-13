package example

import example.Boot.logger
import org.slf4j.{Logger, LoggerFactory}

object Foo {

  final def main(args: Array[String]): Unit = {
    println("I work!")
    val logger: Logger = LoggerFactory.getLogger(Boot.getClass)

    while (true) {
      Thread.sleep(1000)
      logger.info("working...")
      println("I still work!")
    }
  }

}
