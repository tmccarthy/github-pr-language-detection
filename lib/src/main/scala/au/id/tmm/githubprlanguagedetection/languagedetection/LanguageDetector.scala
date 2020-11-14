package au.id.tmm.githubprlanguagedetection.languagedetection

import java.nio.file.{Path, Paths}
import java.time.Duration

import au.id.tmm.collections.NonEmptyArraySeq
import au.id.tmm.githubprlanguagedetection.common.RunProcess
import au.id.tmm.githubprlanguagedetection.languagedetection.model.DetectedLanguages.LanguageFraction
import au.id.tmm.githubprlanguagedetection.languagedetection.model.{DetectedLanguages, Fraction, Language, Languages}
import au.id.tmm.utilities.cats.syntax.all.toMonadErrorSyntax
import au.id.tmm.utilities.errors.{ExceptionOr, GenericException}
import cats.effect.IO
import cats.syntax.traverse.toTraverseOps
import mouse.string._

import scala.collection.immutable.ArraySeq
import scala.util.matching.Regex

final class LanguageDetector private (
  languages: Languages,
  timeout: Option[Duration],
  languagesToIgnoreIfPossible: Set[Language.Name],
) {

  def detectLanguages(path: Path): IO[DetectedLanguages] =
    for {
      linguistOutput    <- runLinguistIn(path)
      detectedLanguages <- IO.fromEither(parseDetectedLanguages(linguistOutput))
    } yield detectedLanguages

  private def runLinguistIn(path: Path): IO[String] =
    for {
      result <- RunProcess.run(
        workingDirectory = IO(Paths.get(".")),
        timeout = timeout,
        "github-linguist",
        path.toAbsolutePath.toString,
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
            language <- languages.findByNameOrError(Language.Name(languageName))
            fraction <- fractionAsString.parseDouble
          } yield DetectedLanguages.LanguageFraction(language, Fraction(fraction))
      }

      sortedLanguageFractions = languageFractions.sorted

      nonEmpty <-
        NonEmptyArraySeq
          .fromIterable(sortedLanguageFractions)
          .toRight(GenericException("Empty set of detected languages"))

      mainLanguage = chooseMainProgrammingLanguage(nonEmpty)
    } yield DetectedLanguages(nonEmpty, mainLanguage)

  private def chooseMainProgrammingLanguage(
    allDetectedLanguages: NonEmptyArraySeq[DetectedLanguages.LanguageFraction],
  ): Language =
    allDetectedLanguages
      .collectFirst {
        case LanguageFraction(language @ Language(name, Some(Language.Type.Programming), _), _)
            if !languagesToIgnoreIfPossible.contains(name) =>
          language
      }
      .getOrElse(allDetectedLanguages.head.language)

}

object LanguageDetector {
  def apply(
    timeout: Option[Duration],
    languagesToIgnoreIfPossible: Set[Language.Name],
  ): IO[LanguageDetector] =
    for {
      languages <- Languages.makeFromLinguistLinguistYaml
    } yield new LanguageDetector(languages, timeout, languagesToIgnoreIfPossible)
}
