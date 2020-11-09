package au.id.tmm.githubprlanguagedetection.linguist

import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.TimeUnit

import au.id.tmm.collections.NonEmptyArraySeq
import au.id.tmm.githubprlanguagedetection.linguist.LanguageDetector.{StdErr, StdOut}
import au.id.tmm.githubprlanguagedetection.linguist.model.{DetectedLanguages, Fraction, Language}
import au.id.tmm.utilities.errors.{ExceptionOr, GenericException}
import cats.effect.{ExitCode, IO}
import cats.syntax.traverse.toTraverseOps
import mouse.string._

import scala.collection.immutable.ArraySeq
import scala.concurrent.TimeoutException
import scala.util.matching.Regex

class LanguageDetector(
  timeout: Option[Duration],
) {

  def detectLanguages(path: Path): IO[DetectedLanguages] =
    for {
      linguistOutput    <- runLinguistIn(path)
      detectedLanguages <- IO.fromEither(parseDetectedLanguages(linguistOutput))
    } yield detectedLanguages

  private def runLinguistIn(path: Path): IO[String] =
    for {
      processBuilder <- IO.pure {
        new ProcessBuilder()
          .command("github-linguist")
          .directory(path.toFile)
      }
      (stdErr, stdOut, exitCode) <- runProcess(processBuilder)
      _ <- exitCode match {
        case ExitCode(0) => IO.unit
        case ExitCode(errorCode) =>
          IO.raiseError(GenericException(s"Linguist failed with return code $errorCode: ${stdErr.asString}"))
      }
    } yield stdOut.asString

  private def runProcess(processBuilder: ProcessBuilder): IO[(StdErr, StdOut, ExitCode)] =
    for {
      process <- IO(processBuilder.start())
      _       <- waitFor(process)
      (stdErr, stdOut, exitCode) <- IO {
        val stdErr = StdErr(new String(process.getErrorStream.readAllBytes(), StandardCharsets.UTF_8))
        val stdOut = StdOut(new String(process.getInputStream.readAllBytes(), StandardCharsets.UTF_8))
        val exitCode = ExitCode(process.exitValue())
        (stdErr, stdOut, exitCode)
      }
    } yield (stdErr, stdOut, exitCode)

  private def waitFor(process: Process): IO[Process] =
    for {
      timedOut <- IO {
        timeout match {
          case Some(timeout) => process.waitFor(timeout.toMillis, TimeUnit.MILLISECONDS)
          case None => {
            process.waitFor()
            false
          }
        }
      }
      _ <- if (timedOut) IO.raiseError(new TimeoutException("Process timed out")) else IO.unit
    } yield process

  private val LINGUIST_LINE_PATTERN: Regex = """^([\d\.]+)%\s+(.*)$""".r

  private def parseDetectedLanguages(linguistOutput: String): ExceptionOr[DetectedLanguages] =
    for {
      languageFractions <- linguistOutput.linesIterator.to(ArraySeq).traverse {
        case LINGUIST_LINE_PATTERN(fractionAsString, languageName) =>
          for {
            fraction <- fractionAsString.parseDouble
          } yield DetectedLanguages.LanguageFraction(Language(languageName), Fraction(fraction))
      }

      sortedLanguageFractions = languageFractions.sorted

      nonEmpty <-
        NonEmptyArraySeq.fromIterable(sortedLanguageFractions).toRight(GenericException("Empty set of detected languages"))
    } yield DetectedLanguages(nonEmpty)

}

object LanguageDetector {
  private final case class StdErr(asString: String) extends AnyVal
  private final case class StdOut(asString: String) extends AnyVal
}
