package au.id.tmm.githubprlanguagedetection.reporting

import java.net.URI
import java.time.format.DateTimeFormatter
import java.time.{Instant, LocalDate, Period, ZoneId}

import au.id.tmm.intime.std.syntax.all._
import au.id.tmm.collections.NonEmptyArraySeq
import au.id.tmm.collections.syntax._
import au.id.tmm.digest4s.digest.SHA256Digest
import au.id.tmm.githubprlanguagedetection.github.model.PullRequest
import au.id.tmm.githubprlanguagedetection.linguist.model.{DetectedLanguages, Language}
import au.id.tmm.githubprlanguagedetection.reporting.GitHubPrLanguageDetectionReport.{CsvRow, ProportionalReport, PullRequestResult, TemporalReport}
import au.id.tmm.digest4s.binarycodecs.syntax._
import au.id.tmm.githubprlanguagedetection.reporting.GitHubPrLanguageDetectionReport.PullRequestResult.LanguageDetectionResult
import au.id.tmm.utilities.errors.{ErrorMessageOr, ProductException}
import au.id.tmm.utilities.syntax.tuples.->

import scala.collection.immutable.ArraySeq

final case class GitHubPrLanguageDetectionReport(
  resultsPerPr: NonEmptyArraySeq[PullRequest -> PullRequestResult],
) {

  def asEncodedCsvRows(timeZone: ZoneId, instantFormat: DateTimeFormatter): NonEmptyArraySeq[ArraySeq[String]] = {
    val encoder: CsvRow.Encoder = CsvRow.Encoder(timeZone, instantFormat)
    asCsvRows.map(encoder.encode)
  }

  private lazy val languagesDetectedForDeduplicatedPrs: ArraySeq[PullRequest -> LanguageDetectionResult.LanguageDetected] = {
    val lookup: Map[PullRequest, LanguageDetectionResult.LanguageDetected] = resultsPerPr
      .safeGroupBy { case (pr, result) => result.projectChecksum }
      .map { case (checksum, rows) => rows.head }
      .collect { case (pr, PullRequestResult(result: PullRequestResult.LanguageDetectionResult.LanguageDetected, _)) => pr -> result }

    resultsPerPr.flatMap { case (pr, result) => lookup.get(pr).map(detectedLanguages => pr -> detectedLanguages) }
  }

  def asCsvRows: NonEmptyArraySeq[CsvRow] = ???

  def temporalReport(
    datum: LocalDate,
    binSize: Period,
    timeZone: ZoneId,
  ): TemporalReport = ???

  def proportionalReport: ProportionalReport = ???

}

object GitHubPrLanguageDetectionReport {

  final case class PullRequestResult(
    languageDetectionResult: PullRequestResult.LanguageDetectionResult,
    projectChecksum: SHA256Digest,
  )

  object PullRequestResult {
    sealed trait LanguageDetectionResult

    object LanguageDetectionResult {
      final case class LanguageDetectionFailed(cause: Exception)
          extends ProductException.WithCause(cause)
          with LanguageDetectionResult
      final case class LanguageDetected(
        fullResults: DetectedLanguages,
        bestGuess: Language,
      ) extends LanguageDetectionResult
    }
  }

  final case class CsvRow(
    prNumber: Int,
    prCreated: Instant,
    prClosed: Option[Instant],
    prTitle: String,
    prUrl: URI,
    detectedLanguage: ErrorMessageOr[Language],
    projectChecksum: SHA256Digest,
  )

  object CsvRow {
    val HEADER_ROW: ArraySeq[String] =
      ArraySeq("prNumber", "prCreated", "prClosed", "prTitle", "prUrl", "detectedLanguage", "projectChecksum")

    private[GitHubPrLanguageDetectionReport] final class Encoder private (
      timeZone: ZoneId,
      dateTimeFormatter: DateTimeFormatter,
    ) {
      def encode(row: CsvRow): ArraySeq[String] =
        ArraySeq(
          row.prNumber.toString,
          row.prCreated.atZone(timeZone).format(dateTimeFormatter),
          row.prClosed match {
            case Some(prClosed) => prClosed.atZone(timeZone).format(dateTimeFormatter)
            case None           => ""
          },
          row.prTitle,
          row.prUrl.toString,
          row.detectedLanguage match {
            case Right(language)    => language.asString
            case Left(errorMessage) => s"Error: $errorMessage"
          },
          row.projectChecksum.asHexString,
        )
    }

    private[GitHubPrLanguageDetectionReport] object Encoder {
      def apply(timeZone: ZoneId, instantFormat: DateTimeFormatter): Encoder = new Encoder(timeZone, instantFormat)
    }

  }

  final case class TemporalReport(
    datum: LocalDate,
    binSize: Period,

    countsPerPeriod: ArraySeq[LocalDate -> Map[Language, Int]],
  )

  final case class ProportionalReport(
    countsPerLanguage: Map[Language, Int],
  )

}
