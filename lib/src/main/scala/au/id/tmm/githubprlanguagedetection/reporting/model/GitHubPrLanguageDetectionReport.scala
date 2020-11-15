package au.id.tmm.githubprlanguagedetection.reporting.model

import java.net.URI
import java.time.format.DateTimeFormatter
import java.time.{Instant, ZoneId}

import au.id.tmm.collections.NonEmptyArraySeq
import au.id.tmm.collections.syntax._
import au.id.tmm.digest4s.binarycodecs.syntax._
import au.id.tmm.digest4s.digest.SHA256Digest
import au.id.tmm.githubprlanguagedetection.github.model.PullRequest
import au.id.tmm.githubprlanguagedetection.languagedetection.model.{DetectedLanguages, Language}
import au.id.tmm.githubprlanguagedetection.reporting.model.GitHubPrLanguageDetectionReport._
import au.id.tmm.utilities.errors.ErrorMessageOr
import au.id.tmm.utilities.syntax.tuples.->

import scala.collection.immutable.ArraySeq

final case class GitHubPrLanguageDetectionReport(
  resultsPerPr: NonEmptyArraySeq[PullRequest -> PullRequestResult],
) {

  def asEncodedCsvRows(timeZone: ZoneId, instantFormat: DateTimeFormatter): NonEmptyArraySeq[ArraySeq[String]] = {
    val encoder: CsvRow.Encoder = CsvRow.Encoder(timeZone, instantFormat)
    asCsvRows.map(encoder.encode)
  }

  def mainProgrammingLanguageDetectedPerPr: ArraySeq[PullRequest -> Language] = {
    val lookup: Map[PullRequest, Language] = resultsPerPr
      .collect { case (pr, r: PullRequestResult.Success) => pr -> r }
      .safeGroupBy { case (pr, result) => result.checksum }
      .map {
        case (checksum, rows) =>
          rows.head match {
            case (pr, result) => pr -> result.detectedLanguages.mainProgrammingLanguage
          }
      }

    resultsPerPr.flatMap { case (pr, result) => lookup.get(pr).map(detectedLanguages => pr -> detectedLanguages) }
  }

  def asCsvRows: NonEmptyArraySeq[CsvRow] =
    resultsPerPr.map {
      case (pr, result) =>
        CsvRow(
          pr.number,
          pr.whenCreated,
          pr.whenClosed,
          pr.title,
          pr.htmlUrl,
          result match {
            case PullRequestResult.Failure(cause) => Left(cause.getMessage)
            case PullRequestResult.Success(detectedLanguages, checksum) =>
              Right(detectedLanguages.mainProgrammingLanguage)
          },
          result match {
            case PullRequestResult.Failure(cause)                 => Left(cause.getMessage)
            case PullRequestResult.Success(fullResults, checksum) => Right(checksum)
          },
        )
    }

}

object GitHubPrLanguageDetectionReport {

  sealed trait PullRequestResult

  object PullRequestResult {

    final case class Success(
      detectedLanguages: DetectedLanguages,
      checksum: SHA256Digest,
    ) extends PullRequestResult

    final case class Failure(
      e: Exception,
    ) extends PullRequestResult

  }

  final case class CsvRow(
    prNumber: Int,
    prCreated: Instant,
    prClosed: Option[Instant],
    prTitle: String,
    prUrl: URI,
    detectedLanguage: ErrorMessageOr[Language],
    projectChecksum: ErrorMessageOr[SHA256Digest],
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
            case Right(language)    => language.name.asString
            case Left(errorMessage) => s"Error: ${errorMessage.takeWhile(_ != '\n')}"
          },
          row.projectChecksum match {
            case Right(checksum)    => checksum.asHexString
            case Left(errorMessage) => s"Error: ${errorMessage.takeWhile(_ != '\n')}"
          },
        )
    }

    private[GitHubPrLanguageDetectionReport] object Encoder {
      def apply(timeZone: ZoneId, instantFormat: DateTimeFormatter): Encoder = new Encoder(timeZone, instantFormat)
    }

  }

}
