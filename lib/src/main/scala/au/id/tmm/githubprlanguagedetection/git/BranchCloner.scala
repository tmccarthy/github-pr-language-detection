package au.id.tmm.githubprlanguagedetection.git

import java.net.URI
import java.nio.file.{Files, Path}
import java.time.Duration

import au.id.tmm.githubprlanguagedetection.git.BranchCloner.Reference
import au.id.tmm.githubprlanguagedetection.github.configuration.GitHubCredentials
import cats.effect.{Bracket, IO}
import org.apache.commons.io.FileUtils
import org.eclipse.jgit
import org.eclipse.jgit.api.{Git => JGit}
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider

class BranchCloner(
  checkoutTimeout: Option[Duration],
  gitHubCredentials: GitHubCredentials,
) {

  def useRepositoryAtRef[A](
    cloneUri: URI,
    reference: BranchCloner.Reference,
  )(
    use: (Path, JGit) => IO[A],
  ): IO[A] =
    Bracket[IO, Throwable].bracket[(Path, JGit), A](
      acquire = checkout(checkoutTimeout, cloneUri, reference),
    )(
      use = {
        case (p, g) => use(p, g)
      },
    )(
      release = {
        case (p, g) => cleanup(p, g)
      },
    )

  private def checkout(
    checkoutTimeOut: Option[Duration],
    cloneUrl: URI,
    reference: BranchCloner.Reference,
  ): IO[(Path, JGit)] =
    for {
      tempDirectory <- IO(Files.createTempDirectory("commit-cloner"))
      repository <- cloneRepository(cloneUrl, checkoutTimeOut, tempDirectory)
      _ <- checkoutReference(repository, reference)
    } yield (tempDirectory, repository)

  private def cloneRepository(
    cloneUrl: URI,
    checkoutTimeOut: Option[Duration],
    cloneLocation: Path,
  ): IO[JGit] = IO {
    val cloneCommand = JGit
      .cloneRepository()
      .setDirectory(cloneLocation.toFile)
      .setCloneAllBranches(false)
      .setNoTags()
      .setURI(cloneUrl.toString)

    configureCredentials(cloneCommand)
    configureCheckoutTimeout(checkoutTimeOut, cloneCommand)

    cloneCommand
      .call()
  }

  private def configureCredentials(command: jgit.api.TransportCommand[_, _]): Unit =
    gitHubCredentials match {
      case GitHubCredentials.Anonymous => ()
      case GitHubCredentials.AccessToken(username, token) =>
        command.setCredentialsProvider(
          new UsernamePasswordCredentialsProvider(
            username,
            token,
          ),
        )
    }

  private def configureCheckoutTimeout(checkoutTimeOut: Option[Duration], cloneCommand: jgit.api.CloneCommand): Unit =
    checkoutTimeOut.foreach { timeoutDuration =>
    cloneCommand.setTimeout(math.toIntExact(timeoutDuration.toSeconds max 1))
  }

  private def checkoutReference(jGit: JGit, reference: Reference): IO[Unit] = {
    reference match {
      case Reference.Simple(refName) => IO(jGit.checkout().setName(refName).call()).as(())
      case Reference.PullRequestHead(prNumber) => {
        for {
          _ <- IO {
            val fetchCommand = jGit.fetch()
              .setRemote("origin")
              .setRefSpecs(s"+refs/pull/$prNumber/head:refs/remotes/origin/pull/$prNumber/head")

            configureCredentials(fetchCommand)

            fetchCommand.call()
          }
          _ <- IO(jGit.checkout().setName("refs/remotes/origin/pull/$prNumber/head").call())
        } yield ()
      }
    }
  }

  private def cleanup(
    path: Path,
    jgit: JGit,
  ): IO[Unit] =
    for {
      _ <- IO(jgit.close())
      _ <- IO(FileUtils.deleteDirectory(path.toFile))
    } yield ()

}

object BranchCloner {
  sealed trait Reference

  object Reference {
    final case class Simple(asString: String)       extends Reference
    final case class PullRequestHead(prNumber: Int) extends Reference
  }
}