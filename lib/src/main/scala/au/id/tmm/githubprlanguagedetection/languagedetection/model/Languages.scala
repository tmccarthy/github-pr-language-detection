package au.id.tmm.githubprlanguagedetection.languagedetection.model

import java.net.URI
import java.nio.charset.StandardCharsets

import au.id.tmm.utilities.cats.syntax.all.toMonadErrorSyntax
import au.id.tmm.utilities.errors.{ExceptionOr, GenericException}
import cats.effect.IO
import cats.syntax.applicativeError._
import cats.syntax.traverse.toTraverseOps
import io.circe
import io.circe.{Decoder, JsonObject}
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.immutable.ArraySeq

final case class Languages(
  all: ArraySeq[Language],
) {

  private val lookupByName: Map[String, Language] = all
    .groupBy(_.name)
    .map {
      case (lang, languages) => lang.asString -> languages.head
    }

  def findByNameOrError(name: Language.Name): ExceptionOr[Language] =
    lookupByName
      .get(name.asString)
      .toRight(GenericException(s"No language called $name"))

}

object Languages {

  private val LOGGER: Logger = LoggerFactory.getLogger(getClass)

  private implicit val languageTypeDecoder: Decoder[Language.Type] =
    Decoder[String]
      .emap {
        case "data"        => Right(Language.Type.Data)
        case "programming" => Right(Language.Type.Programming)
        case "markup"      => Right(Language.Type.Markup)
        case "prose"       => Right(Language.Type.Prose)
        case bad           => Left(s"Bad language type $bad")
      }

  private implicit val decoder: Decoder[Languages] = c =>
    for {
      jsonObj <- c.as[JsonObject]
      languages <- jsonObj.toIterable.to(ArraySeq).traverse {
        case (name, body) => {
          val c = body.hcursor
          for {
            htmlColor    <- c.get[Option[String]]("color")
            languageType <- c.get[Option[Language.Type]]("type")
          } yield Language(Language.Name(name), languageType, htmlColor)
        }
      }
    } yield Languages(languages)

  private[languagedetection] def makeFromLinguistLinguistYaml: IO[Languages] =
    for {
      bytes     <- readLinguistLanguagesYamlBytesFromGitHub
      json      <- IO.fromEither(circe.yaml.parser.parse(new String(bytes.unsafeArray, StandardCharsets.UTF_8)))
      languages <- IO.fromEither(json.as[Languages])
      _         <- IO(LOGGER.info("Loaded languages from linguist languages.yml"))
    } yield languages

  private def readLinguistLanguagesYamlBytesFromGitHub: IO[ArraySeq.ofByte] = {
    def go(uri: URI): IO[ArraySeq.ofByte] = IO(new ArraySeq.ofByte(uri.toURL.openStream().readAllBytes()))

    def uriAssumingBranchName(branchName: String): URI =
      new URI(s"https://raw.githubusercontent.com/github/linguist/$branchName/lib/linguist/languages.yml")

    (go(uriAssumingBranchName("master")) orElse go(uriAssumingBranchName("main")))
      .wrapExceptionWithMessage("Couldn't find linguist languages yml")
  }

}
