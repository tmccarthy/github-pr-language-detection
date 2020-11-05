package au.id.tmm.githubprlanguagedetection.github.model

import scala.collection.immutable.ArraySeq

final case class Commit(
  sha: ArraySeq[Byte],
)
