package au.id.tmm.githubprlanguagedetection.linguist

import java.nio.file.Path
import java.time.Duration

import au.id.tmm.collections.NonEmptyArraySeq
import au.id.tmm.githubprlanguagedetection.common.RunProcess
import au.id.tmm.githubprlanguagedetection.linguist.model.DetectedLanguages.LanguageFraction
import au.id.tmm.githubprlanguagedetection.linguist.model.{DetectedLanguages, Fraction, Language}
import au.id.tmm.utilities.cats.syntax.all.toMonadErrorSyntax
import au.id.tmm.utilities.errors.{ExceptionOr, GenericException}
import cats.effect.IO
import cats.syntax.traverse.toTraverseOps
import mouse.string._

import scala.collection.immutable.ArraySeq
import scala.util.matching.Regex

class LanguageDetector(
  timeout: Option[Duration],
  languagesToIgnoreIfPossible: Set[Language],
) {

  def detectLanguages(path: Path): IO[DetectedLanguages] =
    for {
      linguistOutput    <- runLinguistIn(path)
      detectedLanguages <- IO.fromEither(parseDetectedLanguages(linguistOutput))
    } yield detectedLanguages

  private def runLinguistIn(path: Path): IO[String] =
    for {
      result <- RunProcess.run(
        workingDirectory = IO.pure(path),
        timeout = timeout,
        "github-linguist",
      )
      result <-
        result
          .raiseErrorForFailures[IO]
          .wrapExceptionWithMessage(s"Failed to run github-linguist at path $path")
    } yield result.stdOut.asString

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
        NonEmptyArraySeq
          .fromIterable(sortedLanguageFractions)
          .toRight(GenericException("Empty set of detected languages"))

      mainLanguage = chooseMainLanguage(nonEmpty)
    } yield DetectedLanguages(nonEmpty, mainLanguage)

  private def chooseMainLanguage(
    allDetectedLanguages: NonEmptyArraySeq[DetectedLanguages.LanguageFraction],
  ): Language =
    allDetectedLanguages
      .collectFirst {
        case LanguageFraction(language, fraction) if !languagesToIgnoreIfPossible.contains(language) => language
      }
      .getOrElse(allDetectedLanguages.head.language)

}
