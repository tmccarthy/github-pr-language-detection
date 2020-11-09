package au.id.tmm.githubprlanguagedetection.reporting

import java.net.URI
import java.time.format.DateTimeFormatter
import java.time.{Instant, LocalDate, Period, ZoneId}

import au.id.tmm.collections.NonEmptyArraySeq
import au.id.tmm.collections.syntax._
import au.id.tmm.digest4s.binarycodecs.syntax._
import au.id.tmm.digest4s.digest.SHA256Digest
import au.id.tmm.githubprlanguagedetection.github.model.PullRequest
import au.id.tmm.githubprlanguagedetection.linguist.model.{DetectedLanguages, Language}
import au.id.tmm.githubprlanguagedetection.reporting.GitHubPrLanguageDetectionReport.PullRequestResult.LanguageDetectionResult
import au.id.tmm.githubprlanguagedetection.reporting.GitHubPrLanguageDetectionReport._
import au.id.tmm.intime.std.syntax.all._
import au.id.tmm.utilities.errors.{ErrorMessageOr, ProductException}
import au.id.tmm.utilities.syntax.tuples.->
import cats.syntax.foldable._

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

  def asCsvRows: NonEmptyArraySeq[CsvRow] =
    resultsPerPr.map {
      case (pr, result) => CsvRow(
        pr.number,
        pr.whenCreated,
        pr.whenClosed,
        pr.title,
        pr.htmlUrl,
        result.languageDetectionResult match {
          case LanguageDetectionResult.LanguageDetectionFailed(cause) => Left(cause.getMessage)
          case LanguageDetectionResult.LanguageDetected(fullResults, bestGuess) => Right(bestGuess)
        },
        result.projectChecksum,
      )
    }

  def temporalReport(
    startDate: LocalDate,
    binSize: Period,
    timeZone: ZoneId,
  ): TemporalReport = {

    val binStartDates: ArraySeq[LocalDate] = ArraySeq
      .iterate(startDate, MAX_NUM_PERIODS_FOR_TEMPORAL_REPORT)(d => d + binSize)

    val prsPerDate: collection.BufferedIterator[LocalDate -> Map[Language, Int]] = languagesDetectedForDeduplicatedPrs
      .groupMap {
        case (pr, languageDetected) => pr.whenCreated.atZone(timeZone).toLocalDate
      } {
        case (pr, languageDetected) => languageDetected.bestGuess
      }
      .to(ArraySeq)
      .collect {
        case (date, languagesDetected) if date >= startDate => date -> languagesDetected.countOccurrences
      }
      .sortBy { case (date, _) => date }
      .iterator
      .buffered

    val countsPerPeriod = binStartDates.map { binStart =>
      binStart -> prsPerDate
        .takeUpTo { case (d, _) => d < (binStart + binSize) }
        .to(ArraySeq)
        .map { case (_, counts) => counts }
        .fold
    }

    TemporalReport(startDate, binSize, countsPerPeriod)
  }

  def proportionalReport: ProportionalReport = ProportionalReport {
    languagesDetectedForDeduplicatedPrs
      .map { case (pr, languageDetectionResult) => languageDetectionResult.bestGuess }
      .countOccurrences
  }

}

object GitHubPrLanguageDetectionReport {

  private val MAX_NUM_PERIODS_FOR_TEMPORAL_REPORT = 1_000

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
    startDate: LocalDate,
    binSize: Period,

    countsPerPeriod: ArraySeq[LocalDate -> Map[Language, Int]],
  )

  final case class ProportionalReport(
    countsPerLanguage: Map[Language, Int],
  )

}
