package au.id.tmm.githubprlanguagedetection.configuration

import java.io.InputStream
import java.nio.file.{Files, Path}
import java.util.Properties

import scala.jdk.CollectionConverters._
import au.id.tmm.utilities.errors.{ExceptionOr, GenericException}
import cats.effect.{Bracket, IO}

final case class Creds(
  gitHubUserName: String,
  gitHubPersonalAccessToken: String,
)

object Creds {

  def from(path: Path): IO[Creds] =
    for {
      properties <- Bracket[IO, Throwable].bracket(IO(Files.newInputStream(path)))(propsFrom)(is => IO(is.close()))
      creds <- IO.fromEither(Creds.from(properties))
    } yield creds

  private def propsFrom(is: InputStream): IO[Properties] = {
    val properties = new Properties()

    IO(properties.load(is)).as(properties)
  }

  def from(properties: Properties): ExceptionOr[Creds] = {
    val propertiesMap: collection.Map[String, String] = properties.asScala

    for {
      gitHubUserName <- propertiesMap.get("username").toRight(GenericException("No username"))
      gitHubPersonalAccessToken <- propertiesMap.get("personal_access_token").toRight(GenericException("No token"))
    } yield Creds(gitHubUserName, gitHubPersonalAccessToken)

  }
}