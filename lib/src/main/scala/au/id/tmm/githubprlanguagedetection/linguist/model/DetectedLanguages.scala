package au.id.tmm.githubprlanguagedetection.linguist.model

import au.id.tmm.collections.NonEmptyArraySeq
import au.id.tmm.utilities.syntax.tuples.->

final case class DetectedLanguages(
  results: NonEmptyArraySeq[Language -> Fraction],
)
