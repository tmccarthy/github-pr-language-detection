package au.id.tmm.githubprlanguagedetection.languagedetection.model

import cats.syntax.invariant._

final case class Fraction(asDouble: Double) extends AnyVal

object Fraction {
  implicit val ordering: Ordering[Fraction] = Ordering[Double].imap[Fraction](Fraction.apply)(_.asDouble)
  implicit def orderingOps(fraction: Fraction): ordering.OrderingOps = ordering.mkOrderingOps(fraction)
}
