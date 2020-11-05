val settingsHelper = ProjectSettingsHelper("au.id.tmm", "github-pr-language-detection")(
  githubProjectName = "github-pr-language-detection",
)

settingsHelper.settingsForBuild

lazy val root = project
  .in(file("."))
  .settings(settingsHelper.settingsForRootProject)
  .settings(console := (console in Compile in core).value)
  .aggregate(
    core,
  )

val tmmCollectionsVersion = "0.0.4"
val tmmUtilsVersion = "0.6.2"

lazy val core = project
  .in(file("core"))
  .settings(settingsHelper.settingsForSubprojectCalled("core"))
  .settings(
    libraryDependencies += "org.typelevel"                   %% "cats-effect"                % "2.2.0",
    libraryDependencies += "org.typelevel"                   %% "mouse"                      % "0.25",
    libraryDependencies += "au.id.tmm.tmm-scala-collections" %% "tmm-scala-collections-core" % tmmCollectionsVersion,
    libraryDependencies += "au.id.tmm.tmm-scala-collections" %% "tmm-scala-collections-cats" % tmmCollectionsVersion,
    libraryDependencies += "au.id.tmm.tmm-utils"             %% "tmm-utils-syntax"           % tmmUtilsVersion,
    libraryDependencies += "au.id.tmm.tmm-utils"             %% "tmm-utils-errors"           % tmmUtilsVersion,
    libraryDependencies += "au.id.tmm.tmm-utils"             %% "tmm-utils-cats"             % tmmUtilsVersion,
    libraryDependencies += "au.id.tmm.tmm-utils"             %% "tmm-utils-codec"            % tmmUtilsVersion,
    libraryDependencies += "org.eclipse.jgit"                 % "org.eclipse.jgit"           % "5.9.0.202009080501-r",
    libraryDependencies += "org.kohsuke"                      % "github-api"                 % "1.116",
    libraryDependencies += "org.slf4j"                        % "slf4j-api"                  % "1.7.30",
    libraryDependencies += "org.slf4j"                        % "slf4j-simple"               % "1.7.30" % Runtime,
  )

addCommandAlias("check", ";+test;scalafmtCheckAll")
