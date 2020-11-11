package au.id.tmm.githubprlanguagedetection.git

import java.net.URI
import java.nio.file.{Files, Path}
import java.time.Duration

import au.id.tmm.githubprlanguagedetection.github.configuration.GitHubCredentials
import au.id.tmm.utilities.errors.GenericException
import cats.effect.{Bracket, IO}
import org.apache.commons.io.FileUtils
import org.eclipse.jgit.api.{Git => JGit}
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider

class BranchCloner(
  checkoutTimeout: Option[Duration],
  gitHubCredentials: GitHubCredentials,
) {

  def useRepositoryAtRef[A](
    cloneUri: URI,
    reference: String, // TODO should have a type
  )(
    use: (Path, JGit) => IO[A],
  ): IO[A] =
    Bracket[IO, Throwable].bracket[(Path, JGit), A](
      acquire = checkout(checkoutTimeout, cloneUri, gitHubCredentials, reference),
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
    gitHubCredentials: GitHubCredentials,
    reference: String,
  ): IO[(Path, JGit)] =
    for {
      tempDirectory <- IO(Files.createTempDirectory("commit-cloner"))
      repository <- IO {
        val cloneCommand = JGit
          .cloneRepository()
          .setDirectory(tempDirectory.toFile)
          .setCloneAllBranches(false)
          .setNoTags()
          .setBranch(reference)
          .setURI(cloneUrl.toString)

        gitHubCredentials match {
          case GitHubCredentials.Anonymous => ()
          case GitHubCredentials.AccessToken(username, token) =>
            cloneCommand.setCredentialsProvider(
              new UsernamePasswordCredentialsProvider(
                username,
                token,
              ),
            )
        }

        checkoutTimeOut.foreach { timeoutDuration =>
          cloneCommand.setTimeout(math.toIntExact(timeoutDuration.toSeconds max 1))
        }

        cloneCommand
          .call()
      }

      _ <- if (repository.getRepository.getBranch != reference) {
        IO.raiseError(GenericException("Couldn't checkout branch. It was probably deleted"))
      } else {
        IO.unit
      }
    } yield (tempDirectory, repository)

  private def cleanup(
    path: Path,
    jgit: JGit,
  ): IO[Unit] =
    for {
      _ <- IO(jgit.close())
      _ <- IO(FileUtils.deleteDirectory(path.toFile))
    } yield ()

}
