package au.id.tmm.githubprlanguagedetection.linguist.model

import au.id.tmm.collections.NonEmptyArraySeq

final case class DetectedLanguages(
  results: NonEmptyArraySeq[DetectedLanguages.LanguageFraction],
)

object DetectedLanguages {
  final case class LanguageFraction(
    language: Language,
    fraction: Fraction,
  )

  object LanguageFraction {
    implicit val ordering: Ordering[LanguageFraction] = Ordering.by[LanguageFraction, Fraction](_.fraction).reverse
  }
}