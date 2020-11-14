package au.id.tmm.githubprlanguagedetection.languagedetection.model

import io.circe.Decoder

final case class Language(
  name: Language.Name,
  languageType: Option[Language.Type],
  htmlColor: Option[String],
)

object Language {
  final case class Name(asString: String) extends AnyVal

  object Name {
    implicit val decoder: Decoder[Name] = Decoder[String].map(Name.apply)
  }

  sealed trait Type

  object Type {
    case object Data        extends Type
    case object Programming extends Type
    case object Markup      extends Type
    case object Prose       extends Type
  }
}
