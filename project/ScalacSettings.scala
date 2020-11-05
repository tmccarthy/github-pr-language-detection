import sbt.Keys
import sbt.librarymanagement.CrossVersion

object ScalacSettings {

  private val scalacOptionsCommon = Seq(
    "-deprecation",                     // Emit warning and location for usages of deprecated APIs.
    "-encoding", "utf-8",               // Specify character encoding used by source files.
    "-explaintypes",                    // Explain type errors in more detail.
    "-feature",                         // Emit warning and location for usages of features that should be imported explicitly.
    "-language:existentials",           // Existential types (besides wildcard types) can be written and inferred
    "-language:higherKinds",            // Allow higher-kinded types
    "-language:implicitConversions",    // Allow implicit conversions
    "-unchecked",                       // Enable additional warnings where generated code depends on assumptions.
    "-Xcheckinit",                      // Wrap field accessors to throw an exception on uninitialized access.
    "-Xfatal-warnings",                 // Fail the compilation if there are any warnings.
    "-Xlint:constant",                  // Evaluation of a constant arithmetic expression results in an error.
    "-Xlint:delayedinit-select",        // Selecting member of DelayedInit.
    "-Xlint:doc-detached",              // A Scaladoc comment appears to be detached from its element.
    "-Xlint:inaccessible",              // Warn about inaccessible types in method signatures.
    "-Xlint:infer-any",                 // Warn when a type argument is inferred to be `Any`.
    "-Xlint:nullary-unit",              // Warn when nullary methods return Unit.
    "-Xlint:option-implicit",           // Option.apply used implicit view.
    "-Xlint:package-object-classes",    // Class or object defined in package object.
    "-Xlint:poly-implicit-overload",    // Parameterized overloaded implicit methods are not visible as view bounds.
    "-Xlint:private-shadow",            // A private field (or class parameter) shadows a superclass field.
    "-Xlint:stars-align",               // Pattern sequence wildcard must align with sequence component.
    "-Xlint:type-parameter-shadow",     // A local type parameter shadows a type already in scope.
    "-Ywarn-extra-implicit",            // Warn when more than one implicit parameter section is defined.
    "-Ywarn-unused:imports",            // Warn if an import selector is not referenced.
    "-Ywarn-unused:locals",             // Warn if a local definition is unused.
    "-Ywarn-unused:privates",           // Warn if a private member is unused.
    "-Ypatmat-exhaust-depth", "80",     // Increase max exhaustion depth
  )

  private val scalacOptions2_13 = Seq(
  )

  def scalacSetting =
    Keys.scalacOptions :=
      scalacOptionsCommon ++ {
        CrossVersion.partialVersion(Keys.scalaVersion.value) match {
          case Some((2, 13)) => scalacOptions2_13
          case _ => Seq.empty
        }
      }

}
