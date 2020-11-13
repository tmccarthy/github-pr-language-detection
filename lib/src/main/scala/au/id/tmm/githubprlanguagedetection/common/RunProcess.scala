package au.id.tmm.githubprlanguagedetection.common

import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.TimeUnit

import au.id.tmm.githubprlanguagedetection.common.RunProcess.Result.{StdErr, StdOut}
import au.id.tmm.utilities.errors.GenericException
import cats.MonadError
import cats.effect.{ExitCode, IO}

import scala.concurrent.TimeoutException

object RunProcess {

  def run(
    workingDirectory: IO[Path],
    timeout: Option[Duration],
    command: String*,
  ): IO[Result] =
    for {
      workingDirectory <- workingDirectory
      processBuilder <- IO.pure {
        new ProcessBuilder()
          .command(command: _*)
          .directory(workingDirectory.toFile)
      }
      process  <- IO(processBuilder.start())
      _        <- waitFor(process, timeout)
      stdErr   <- IO(new String(process.getErrorStream.readAllBytes(), StandardCharsets.UTF_8)).map(StdErr.apply)
      stdOut   <- IO(new String(process.getInputStream.readAllBytes(), StandardCharsets.UTF_8)).map(StdOut.apply)
      exitCode <- IO(process.exitValue()).map(ExitCode.apply)
    } yield Result(exitCode, stdErr, stdOut)

  private def waitFor(
    process: Process,
    timeout: Option[Duration],
  ): IO[Process] =
    for {
      timedOut <- IO {
        timeout match {
          case Some(timeout) => !process.waitFor(timeout.toMillis, TimeUnit.MILLISECONDS)
          case None => {
            process.waitFor()
            false
          }
        }
      }
      _ <- if (timedOut) IO.raiseError(new TimeoutException("Process timed out")) else IO.unit
    } yield process

  final case class Result(
    exitCode: ExitCode,
    stdErr: StdErr,
    stdOut: StdOut,
  ) {
    def raiseErrorForFailures[F[_]](implicit F: MonadError[F, Throwable]): F[Result] =
      exitCode match {
        case ExitCode.Success => F.pure(this)
        case ExitCode(errorCode) =>
          F.raiseError(GenericException(s"Process failed with return code $errorCode. StdErr was \n${stdErr.asString}"))
      }
  }

  object Result {
    final case class StdErr(asString: String) extends AnyVal
    final case class StdOut(asString: String) extends AnyVal
  }

}
