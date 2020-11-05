package au.id.tmm.githubprlanguagedetection

import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}

import au.id.tmm.githubprlanguagedetection.configuration.Creds
import au.id.tmm.githubprlanguagedetection.git.BranchCloner
import au.id.tmm.githubprlanguagedetection.github.PullRequestLister
import au.id.tmm.githubprlanguagedetection.github.configuration.{GitHubConfiguration, GitHubCredentials, GitHubInstance}
import au.id.tmm.githubprlanguagedetection.github.model.RepositoryName
import cats.effect.{ExitCode, IO, IOApp}
import com.github.ghik.silencer.silent
import org.eclipse.jgit.api.{Git => JGit}

object Main extends IOApp {

  private val publicGitHubConfig: IO[GitHubConfiguration] =
    for {
      credsFile <- IO(Paths.get("public.creds.properties"))
      creds     <- Creds.from(credsFile)
      configuration <- IO.pure(
        GitHubConfiguration(
          GitHubCredentials.AccessToken(creds.gitHubUserName, creds.gitHubPersonalAccessToken),
          GitHubInstance.GitHubDotCom,
        ),
      )
    } yield configuration

  @silent("never used")
  private def listPrsFromMouse: IO[Unit] =
    for {
      configuration     <- publicGitHubConfig
      pullRequestLister <- IO.pure(new PullRequestLister(configuration))
      pullRequests      <- pullRequestLister.listPullRequestsFor(RepositoryName("typelevel", "mouse"))
      _ <- IO {
        pullRequests.sortBy(_.number).foreach { pr =>
          println(s"#${pr.number} ${pr.title}")
        }
      }
    } yield ()

  @silent("never used")
  private def useTmmUtilsNonEmptyCollections: IO[Unit] =
    for {
      configuration <- publicGitHubConfig
      branchCloner = new BranchCloner()
      _ <- branchCloner.useRepositoryAtRef(
        checkoutTimeout = None,
        cloneUri = new URI("https://github.com/tmccarthy/tmmUtils.git"),
        configuration.credentials,
        reference = "non-empty-collections",
      )(printJavaVersion)
    } yield ()

  private def printJavaVersion(repoPath: Path, jgit: JGit): IO[Unit] =
    for {
      javaVersionFile <- IO(repoPath.resolve(".java-version"))
      javaVersion     <- IO(new String(Files.readAllBytes(javaVersionFile), StandardCharsets.UTF_8))
      _               <- IO(println(javaVersion))
    } yield ()

  override def run(args: List[String]): IO[ExitCode] =
    useTmmUtilsNonEmptyCollections.as(ExitCode.Success)
}
