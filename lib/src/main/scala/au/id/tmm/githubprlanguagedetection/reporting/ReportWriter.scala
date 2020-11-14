package au.id.tmm.githubprlanguagedetection.reporting

import java.time.Duration

import au.id.tmm.collections.NonEmptyArraySeq
import au.id.tmm.digest4s.digest.SHA256Digest
import au.id.tmm.githubprlanguagedetection.common.DirectoryDigester
import au.id.tmm.githubprlanguagedetection.git.BranchCloner
import au.id.tmm.githubprlanguagedetection.github.PullRequestLister
import au.id.tmm.githubprlanguagedetection.github.model.{PullRequest, RepositoryName}
import au.id.tmm.githubprlanguagedetection.languagedetection.LanguageDetector
import au.id.tmm.githubprlanguagedetection.languagedetection.model.DetectedLanguages
import au.id.tmm.githubprlanguagedetection.reporting.ReportWriter.LOGGER
import au.id.tmm.githubprlanguagedetection.reporting.model.GitHubPrLanguageDetectionReport
import au.id.tmm.githubprlanguagedetection.reporting.model.GitHubPrLanguageDetectionReport.PullRequestResult
import au.id.tmm.intime.std.syntax.all._
import au.id.tmm.utilities.errors.{ExceptionOr, GenericException}
import au.id.tmm.utilities.syntax.tuples.->
import cats.effect.{Concurrent, ContextShift, IO, Timer}
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.immutable.ArraySeq
import scala.concurrent.{duration => scaladuration}

class ReportWriter(
  pullRequestLister: PullRequestLister,
  branchCloner: BranchCloner,
  languageDetector: LanguageDetector,
  directoryDigester: DirectoryDigester,
)(implicit
  timer: Timer[IO],
  contextShift: ContextShift[IO],
  concurrent: Concurrent[IO],
) {

  def produceGitHubPrLanguageDetectionReport(
    checkoutsPerMinute: Int,
    maxConcurrent: Int,
    repository: RepositoryName,
  ): IO[GitHubPrLanguageDetectionReport] =
    for {
      pullRequests <- pullRequestLister.listPullRequestsFor(repository)

      delayBetweenCheckouts = Duration.ofMinutes(1) / checkoutsPerMinute

      detectedLanguagesPerPullRequest <-
        streamThatEmitsEvery(pullRequests, delayBetweenCheckouts)
          .parEvalMap(maxConcurrent) { pullRequest =>
            val resultIO: IO[(SHA256Digest, DetectedLanguages)] = for {
              cloneUri <- IO.pure(pullRequest.repository.cloneUris.https)
              refToClone = BranchCloner.Reference.GitHubPullRequestHead(pullRequest.number)
              (checksum, detectedLanguages) <- branchCloner
                .useRepositoryAtRef(cloneUri, refToClone) { (repositoryPath, jGit) =>
                  directoryDigester.digestFor(repositoryPath) parProduct languageDetector.detectLanguages(repositoryPath)
                }
            } yield (checksum, detectedLanguages)

            resultIO.attempt.flatMap {
              case Left(e: Exception) =>
                IO(LOGGER.error(s"Failed to parse languages for #${pullRequest.number}", e)).as(pullRequest -> Left(e))
              case Left(t: Throwable) => IO.raiseError(t)
              case Right(result) =>
                IO(
                  LOGGER.info(
                    s"Parsed languages for #${pullRequest.number}. Main language was ${result._2.mainLanguage.asString}",
                  ),
                ).as(pullRequest -> Right(result))
            }
          }
          .compile
          .to(ArraySeq)

      nonEmptyDetectedLanguagesPerPullRequest <- IO.fromEither(
        NonEmptyArraySeq.fromArraySeq(detectedLanguagesPerPullRequest).toRight(GenericException("No prs")),
      )

      report = makeIntoReport(nonEmptyDetectedLanguagesPerPullRequest)

    } yield report

  private def streamThatEmitsEvery[A](elements: ArraySeq[A], delay: Duration): fs2.Stream[IO, A] =
    fs2.Stream
      .fixedDelay[IO](scaladuration.Duration(delay.toMillis, scaladuration.MILLISECONDS))
      .zipRight(fs2.Stream.emits(elements))

  private def makeIntoReport(
    resultsPerPr: NonEmptyArraySeq[PullRequest -> ExceptionOr[(SHA256Digest, DetectedLanguages)]],
  ): GitHubPrLanguageDetectionReport =
    GitHubPrLanguageDetectionReport {
      resultsPerPr.map[PullRequest -> PullRequestResult] {
        case (pr, Right((checksum, detectedLanguages))) =>
          pr -> (PullRequestResult
            .Success(detectedLanguages, checksum): PullRequestResult)
        case (pr, Left(e)) =>
          pr -> (PullRequestResult.Failure(e): PullRequestResult)
      }
    }

}

object ReportWriter {
  private val LOGGER: Logger = LoggerFactory.getLogger(getClass)
}
