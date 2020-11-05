package au.id.tmm.githubprlanguagedetection

import java.nio.file.{Path, Paths}

import au.id.tmm.githubprlanguagedetection.configuration.Creds
import au.id.tmm.githubprlanguagedetection.github.PullRequestLister
import au.id.tmm.githubprlanguagedetection.github.configuration.{GitHubConfiguration, GitHubCredentials, GitHubInstance}
import au.id.tmm.githubprlanguagedetection.github.model.RepositoryName
import cats.effect.{ExitCode, IO, IOApp}

object Main extends IOApp {

  private def credsFile: Path = Paths.get("public.creds.properties")

  override def run(args: List[String]): IO[ExitCode] =
    for {
      creds <- Creds.from(credsFile)
      configuration <- IO.pure(
        GitHubConfiguration(
          GitHubCredentials.AccessToken(creds.gitHubUserName, creds.gitHubPersonalAccessToken),
          GitHubInstance.GitHubDotCom,
        ),
      )
      pullRequestLister <- IO.pure(new PullRequestLister(configuration))
      pullRequests      <- pullRequestLister.listPullRequestsFor(RepositoryName("typelevel", "mouse"))
      _ <- IO {
        pullRequests.sortBy(_.number).foreach { pr =>
          println(s"#${pr.number} ${pr.title}")
        }
      }
    } yield ExitCode.Success
}
