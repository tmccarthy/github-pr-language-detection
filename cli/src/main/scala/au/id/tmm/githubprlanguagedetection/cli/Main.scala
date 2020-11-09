package au.id.tmm.githubprlanguagedetection.cli

import java.nio.file.Paths

import au.id.tmm.collections.syntax.toIterableOps
import au.id.tmm.githubprlanguagedetection.git.BranchCloner
import au.id.tmm.githubprlanguagedetection.github.PullRequestLister
import au.id.tmm.githubprlanguagedetection.linguist.LanguageDetector
import au.id.tmm.githubprlanguagedetection.reporting.ReportWriter
import cats.effect.{ExitCode, IO, IOApp}

object Main extends IOApp {

  override def run(args: List[String]): IO[ExitCode] =
    for {
      rawConfigFilePath <- IO.fromEither(args.onlyElementOrException)
      configFilePath <- IO(Paths.get(rawConfigFilePath))
      cliConfig <- CliConfig.from(configFilePath)

      pullRequestLister = new PullRequestLister(cliConfig.gitHubConfiguration)
      branchCloner = new BranchCloner(
        Some(cliConfig.performance.checkoutTimeout),
        cliConfig.gitHubConfiguration.credentials,
      )
      languageDetector = new LanguageDetector(cliConfig.performance.languageCheckTimeout)

      reportWriter = new ReportWriter(pullRequestLister, branchCloner, languageDetector)

      _ <- reportWriter.produceGitHubPrLanguageDetectionReport(
        cliConfig.performance.checkoutsPerMinute,
        cliConfig.performance.checkoutTimeout,
        cliConfig.performance.languageCheckTimeout,
        cliConfig.repositoryToScan,
      )

    } yield ???

}
