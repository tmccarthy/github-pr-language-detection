package au.id.tmm.githubprlanguagedetection.cli

import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.time.format.DateTimeFormatter
import java.time.{Duration, ZoneId}

import au.id.tmm.githubprlanguagedetection.cli.CliConfig.PerformanceConfig
import au.id.tmm.githubprlanguagedetection.github.configuration.{GitHubConfiguration, GitHubCredentials, GitHubInstance}
import au.id.tmm.githubprlanguagedetection.github.model.RepositoryName
import au.id.tmm.utilities.errors.ExceptionOr
import cats.effect.IO
import cats.syntax.functor.toFunctorOps
import io.circe.Decoder

import scala.util.matching.Regex

final case class CliConfig(
  gitHubConfiguration: GitHubConfiguration,
  repositoryToScan: RepositoryName,
  performance: PerformanceConfig,
  timeZone: Option[ZoneId],
  reportDateTimeFormat: Option[DateTimeFormatter],
)

object CliConfig {

  final case class PerformanceConfig(
    checkoutsPerMinute: Int,
    checkoutTimeout: Duration,
    languageCheckTimeout: Option[Duration],
  )

  object PerformanceConfig {
    implicit val decoder: Decoder[PerformanceConfig] = Decoder.forProduct3("checkoutsPerMinute", "checkoutTimeout", "languageCheckTimeout")(PerformanceConfig.apply)
  }

  private val REPOSITORY_NAME_PATTERN: Regex = """^(\w+)/(\w+)$""".r

  private implicit val gitHubCredentialsDecoder: Decoder[GitHubCredentials] = {
    val anonymousCredentialsDecoder: Decoder[GitHubCredentials.Anonymous.type] = Decoder[String].emap {
      case "anonymous" => Right(GitHubCredentials.Anonymous)
      case _           => Left("")
    }

    val personalAccessTokenDecoder: Decoder[GitHubCredentials.AccessToken] =
      Decoder.forProduct2("username", "personalAccessToken")(GitHubCredentials.AccessToken.apply)

    anonymousCredentialsDecoder.widen[GitHubCredentials] or personalAccessTokenDecoder.widen[GitHubCredentials]
  }

  private implicit val gitHubInstanceDecoder: Decoder[GitHubInstance] = {
    val gitHubDotComeDecoder: Decoder[GitHubInstance.GitHubDotCom.type] = Decoder[String].emap {
      case "github.com" => Right(GitHubInstance.GitHubDotCom)
      case _            => Left("")
    }

    val gitHubEnterpriseDecoder: Decoder[GitHubInstance.GitHubEnterprise] = Decoder[String]
      .emap(rawUri => ExceptionOr.catchIn(new URI(rawUri)).left.map(_.toString))
      .map(GitHubInstance.GitHubEnterprise.apply)

    gitHubDotComeDecoder.widen[GitHubInstance] or gitHubEnterpriseDecoder.widen[GitHubInstance]
  }

  private implicit val gitHubConfigurationDecoder: Decoder[GitHubConfiguration] =
    Decoder.forProduct2("credentials", "instance")(GitHubConfiguration.apply)

  private implicit val gitHubRepositoryNameDecoder: Decoder[RepositoryName] = Decoder[String].emap {
    case REPOSITORY_NAME_PATTERN(owner, repo) => Right(RepositoryName(owner, repo))
    case badRepositoryName => Left(s"""Bad repository name "$badRepositoryName"""")
  }

  private implicit val dateTimeFormatterDecoder: Decoder[DateTimeFormatter] = Decoder[String]
    .emap { s =>
      try {
        Right(DateTimeFormatter.ofPattern(s))
      } catch {
        case e: IllegalArgumentException => Left(e.getMessage)
      }
    }

  implicit val decoder: Decoder[CliConfig] = Decoder.forProduct5(
    "gitHubCredentials",
    "repositoryToScan",
    "performance",
    "timeZone",
    "reportDateTimeFormat",
  )(CliConfig.apply)

  def from(path: Path): IO[CliConfig] =
    for {
      fileContents <- IO(new String(Files.readAllBytes(path), StandardCharsets.UTF_8))
      cliConfig    <- IO.fromEither(io.circe.parser.decode[CliConfig](fileContents))
    } yield cliConfig

}
