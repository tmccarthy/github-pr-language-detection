package au.id.tmm.githubprlanguagedetection.linguist.model

import cats.syntax.invariant._

final case class Fraction(asDouble: Double) extends AnyVal

object Fraction {
  implicit val ordering: Ordering[Fraction] = Ordering[Double].imap[Fraction](Fraction.apply)(_.asDouble)
}
