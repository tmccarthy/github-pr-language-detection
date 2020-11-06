package au.id.tmm.githubprlanguagedetection.cli

import java.nio.file.Paths

import au.id.tmm.collections.syntax.toIterableOps
import au.id.tmm.githubprlanguagedetection.cli.CliConfig.PerformanceConfig
import au.id.tmm.githubprlanguagedetection.git.BranchCloner
import au.id.tmm.githubprlanguagedetection.github.PullRequestLister
import au.id.tmm.githubprlanguagedetection.github.model.{PullRequest, RepositoryName}
import au.id.tmm.githubprlanguagedetection.linguist.LanguageDetector
import au.id.tmm.githubprlanguagedetection.linguist.model.DetectedLanguages
import au.id.tmm.utilities.errors.ExceptionOr
import au.id.tmm.utilities.syntax.tuples.->
import cats.effect.{ExitCode, IO, IOApp}
import java.time.Duration
import scala.concurrent.duration.{Duration => ScalaDuration}
import scala.concurrent.{duration => scaladuration}
import au.id.tmm.intime.std.syntax.all._

import scala.collection.immutable.ArraySeq

object Main extends IOApp {
  override def run(args: List[String]): IO[ExitCode] =
    for {
      rawConfigFilePath <- IO.fromEither(args.onlyElementOrException)
      configFilePath    <- IO(Paths.get(rawConfigFilePath))
      cliConfig         <- CliConfig.from(configFilePath)

      pullRequestLister = new PullRequestLister(cliConfig.gitHubConfiguration)
      branchCloner = new BranchCloner(Some(cliConfig.performance.checkoutTimeout), cliConfig.gitHubConfiguration.credentials)
      languageDetector = new LanguageDetector(cliConfig.performance.languageCheckTimeout)

      detectedLanguagesForEveryPr <-
        detectLanguagesForEveryPr(
          pullRequestLister,
          branchCloner,
          languageDetector,
          cliConfig.performance,
          cliConfig.repositoryToScan,
        )

    } yield ???

  def detectLanguagesForEveryPr(
    pullRequestLister: PullRequestLister,
    branchCloner: BranchCloner,
    languageDetector: LanguageDetector,

    performanceConfig: PerformanceConfig,
    repository: RepositoryName,
  ): IO[ArraySeq[PullRequest -> ExceptionOr[DetectedLanguages]]] =
    for {
      pullRequests <- pullRequestLister.listPullRequestsFor(repository)

      delayBetweenCheckouts = Duration.ofMinutes(1) / performanceConfig.checkoutsPerMinute

      _ = streamThatEmitsEvery(pullRequests, delayBetweenCheckouts).evalMap { pullRequest =>
        branchCloner.useRepositoryAtRef(
          cloneUri = ???,
          reference = pullRequest.head.ref
        )
      }

    } yield ???

  private def streamThatEmitsEvery[A](elements: ArraySeq[A], delay: Duration): fs2.Stream[IO, A] =
    fs2.Stream.awakeEvery[IO](scaladuration.Duration(delay.toMillis, scaladuration.MILLISECONDS)).zipRight(fs2.Stream.emits(elements))

}
