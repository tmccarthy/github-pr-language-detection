package au.id.tmm.githubprlanguagedetection.reporting

import java.io.OutputStream
import java.nio.file.{Files, Path}
import java.security.{DigestOutputStream, MessageDigest}
import java.time.Duration

import au.id.tmm.collections.NonEmptyArraySeq
import au.id.tmm.digest4s.binarycodecs.syntax._
import au.id.tmm.digest4s.digest.SHA256Digest
import au.id.tmm.githubprlanguagedetection.git.BranchCloner
import au.id.tmm.githubprlanguagedetection.github.PullRequestLister
import au.id.tmm.githubprlanguagedetection.github.model.{PullRequest, RepositoryName}
import au.id.tmm.githubprlanguagedetection.linguist.LanguageDetector
import au.id.tmm.githubprlanguagedetection.linguist.model.{DetectedLanguages, Fraction, Language}
import au.id.tmm.githubprlanguagedetection.reporting.ReportWriter.LOGGER
import au.id.tmm.githubprlanguagedetection.reporting.model.GitHubPrLanguageDetectionReport
import au.id.tmm.githubprlanguagedetection.reporting.model.GitHubPrLanguageDetectionReport.PullRequestResult
import au.id.tmm.intime.std.syntax.all._
import au.id.tmm.utilities.errors.{ExceptionOr, GenericException}
import au.id.tmm.utilities.syntax.tuples.->
import cats.effect.{IO, Timer}
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.immutable.ArraySeq
import scala.concurrent.{duration => scaladuration}
import scala.jdk.CollectionConverters.IteratorHasAsScala

class ReportWriter(
  pullRequestLister: PullRequestLister,
  branchCloner: BranchCloner,
  languageDetector: LanguageDetector,
)(
  implicit timer: Timer[IO],
) {

  def produceGitHubPrLanguageDetectionReport(
    checkoutsPerMinute: Int,
    repository: RepositoryName,
  ): IO[GitHubPrLanguageDetectionReport] =
    for {
      pullRequests <- pullRequestLister.listPullRequestsFor(repository)

      delayBetweenCheckouts = Duration.ofMinutes(1) / checkoutsPerMinute

      detectedLanguagesPerPullRequest <-
        streamThatEmitsEvery(pullRequests, delayBetweenCheckouts)
          .evalMap { pullRequest =>
            val resultIO: IO[(SHA256Digest, DetectedLanguages)] = for {
              cloneUri <- IO.fromEither {
                pullRequest.head.repository
                  .map(_.cloneUris.https)
                  .toRight(GenericException("Head repository was deleted"))
              }
              refToClone = pullRequest.head.ref.getOrElse(pullRequest.head.sha.asHexString)
              (checksum, detectedLanguages) <- branchCloner.useRepositoryAtRef(cloneUri, refToClone) { (repositoryPath, jGit) =>
                for {
                  detectedLanguages <- languageDetector.detectLanguages(repositoryPath)
                  checksum <- computeChecksumOfDirectory(repositoryPath)
                } yield (checksum, detectedLanguages)
              }
            } yield (checksum, detectedLanguages)

            resultIO.attempt.flatMap {
              case Left(e: Exception) => IO(LOGGER.error(s"Failed to parse languages for #${pullRequest.number}", e)).as(pullRequest -> Left(e))
              case Left(t: Throwable) => IO.raiseError(t)
              case Right(result) => IO(LOGGER.error(s"Parsed languages for #${pullRequest.number}. First was ${result._2.results.head.language.asString}")).as(pullRequest -> Right(result))
            }
          }
          .compile
          .to(ArraySeq)

      nonEmptyDetectedLanguagesPerPullRequest <- IO.fromEither(NonEmptyArraySeq.fromArraySeq(detectedLanguagesPerPullRequest).toRight(GenericException("No prs")))

      report = makeIntoReport(nonEmptyDetectedLanguagesPerPullRequest)

    } yield report

  private def streamThatEmitsEvery[A](elements: ArraySeq[A], delay: Duration): fs2.Stream[IO, A] =
    fs2.Stream
      .awakeEvery[IO](scaladuration.Duration(delay.toMillis, scaladuration.MILLISECONDS))
      .zipRight(fs2.Stream.emits(elements))

  private def makeIntoReport(
    resultsPerPr: NonEmptyArraySeq[PullRequest -> ExceptionOr[(SHA256Digest, DetectedLanguages)]],
  ): GitHubPrLanguageDetectionReport = GitHubPrLanguageDetectionReport {
    resultsPerPr.map[PullRequest -> PullRequestResult] {
      case (pr, Right((checksum, detectedLanguages))) =>
        pr -> (PullRequestResult.Success(detectedLanguages, bestGuessLanguage(detectedLanguages), checksum): PullRequestResult)
      case (pr, Left(e)) =>
        pr -> (PullRequestResult.Failure(e): PullRequestResult)
    }
  }

  private def bestGuessLanguage(detectedLanguages: DetectedLanguages): Language = {
    val pluralityLanguage = detectedLanguages.results.head
    if (pluralityLanguage.language == Language("Shell")) {
      detectedLanguages.results.underlying.lift(1) match {
        case Some(DetectedLanguages.LanguageFraction(secondLanguage, fraction)) if fraction > Fraction(0.2) => secondLanguage
        case _ => pluralityLanguage.language
      }
    } else {
      pluralityLanguage.language
    }
  }

  private def computeChecksumOfDirectory(path: Path): IO[SHA256Digest] = IO {

    val digest: MessageDigest = MessageDigest.getInstance("SHA-256")

    val digestOutputStream = new DigestOutputStream(OutputStream.nullOutputStream(), digest)

    Files.walk(path)
      .filter(path => Files.isRegularFile(path))
      .iterator
      .asScala
      .foreach { path =>
        Files.copy(path, digestOutputStream)
      }

    SHA256Digest(new ArraySeq.ofByte(digest.digest()))
  }

}

object ReportWriter {
  private val LOGGER: Logger = LoggerFactory.getLogger(getClass)
}