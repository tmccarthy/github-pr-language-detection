package au.id.tmm.githubprlanguagedetection.cli

import java.nio.file.Paths
import java.time.ZoneId
import java.time.format.DateTimeFormatter

import au.id.tmm.collections.syntax.toIterableOps
import au.id.tmm.githubprlanguagedetection.git.BranchCloner
import au.id.tmm.githubprlanguagedetection.github.PullRequestLister
import au.id.tmm.githubprlanguagedetection.linguist.LanguageDetector
import au.id.tmm.githubprlanguagedetection.reporting.ReportWriter
import cats.effect.{Bracket, ExitCode, IO, IOApp}
import com.github.tototoshi.csv.{CSVWriter, DefaultCSVFormat}

object Main extends IOApp {

  override def run(args: List[String]): IO[ExitCode] =
    for {
      rawConfigFilePath <- IO.fromEither(args.onlyElementOrException)
      configFilePath    <- IO(Paths.get(rawConfigFilePath))
      cliConfig         <- CliConfig.from(configFilePath)

      pullRequestLister = new PullRequestLister(cliConfig.gitHubConfiguration)
      branchCloner = new BranchCloner(
        Some(cliConfig.performance.checkoutTimeout),
        cliConfig.gitHubConfiguration.credentials,
      )
      languageDetector = new LanguageDetector(cliConfig.performance.languageCheckTimeout)

      reportWriter = new ReportWriter(pullRequestLister, branchCloner, languageDetector)

      report <- reportWriter.produceGitHubPrLanguageDetectionReport(
        cliConfig.performance.checkoutsPerMinute,
        cliConfig.performance.maxConcurrent,
        cliConfig.repositoryToScan,
      )

      csvOutputFile <- IO(Paths.get(cliConfig.reportConfig.outputPath))

      _ <- Bracket[IO, Throwable].bracket(
        acquire = IO(CSVWriter.open(csvOutputFile.toFile)(new DefaultCSVFormat {})),
      )(
        use = csvWriter =>
          IO {
            csvWriter.writeAll(
              report
                .asEncodedCsvRows(
                  cliConfig.reportConfig.timeZone.getOrElse(ZoneId.systemDefault()),
                  DateTimeFormatter.ISO_LOCAL_DATE,
                )
                .toSeq,
            )
          },
      )(
        release = csvWriter => IO(csvWriter.close()),
      )

    } yield ExitCode.Success

}
