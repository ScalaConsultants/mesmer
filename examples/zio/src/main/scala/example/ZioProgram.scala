package example

import zio.Console
import zio.Random
import zio.Schedule
import zio.ZIO
import zio.durationInt

object ZioProgram {

  def findTheMeaningOfLife(parallelism: Int, lowerBound: Int, upperBound: Int): ZIO[Any, Nothing, Boolean] = {
    val numberToGuess = 42

    val task: ZIO[Any, Nothing, Boolean] = {
      val recurringTask = (for {
        _             <- Console.printLine("Looking for the meaning of life...")
        guessedNumber <- Random.nextIntBetween(lowerBound, upperBound)
        _             <- Console.printLine(s"Found some number: $guessedNumber") *> ZIO.sleep(100.milliseconds)
        result = guessedNumber == numberToGuess
      } yield result).orDie

      recurringTask.repeat(Schedule.recurUntilEquals(true))
    }

    val tasks = (0 until parallelism).map(_ => task).toList
    ZIO.raceAll(tasks.head, tasks.tail)
  }

}
