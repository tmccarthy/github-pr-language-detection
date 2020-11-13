package au.id.tmm.githubprlanguagedetection.github.model

import scala.collection.immutable.ArraySeq

final case class Commit(
  repository: Option[Repository], // This is unknown if the repo has been deleted
  ref: Option[String],
  sha: ArraySeq[Byte],
)
