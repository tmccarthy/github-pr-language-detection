val settingsHelper = ProjectSettingsHelper("au.id.tmm", "github-pr-language-detection")(
  githubProjectName = "github-pr-language-detection",
)

settingsHelper.settingsForBuild

lazy val root = project
  .in(file("."))
  .settings(settingsHelper.settingsForRootProject)
  .settings(console := (console in Compile in lib).value)
  .aggregate(
    lib,
    cli,
  )

val tmmCollectionsVersion = "0.0.4"
val tmmUtilsVersion = "0.7.0"
val intimeVersion = "2.2.0"

lazy val lib = project
  .in(file("lib"))
  .settings(settingsHelper.settingsForSubprojectCalled("lib"))
  .settings(
    libraryDependencies += "org.typelevel"                   %% "cats-effect"                % "2.2.0",
    libraryDependencies += "org.typelevel"                   %% "mouse"                      % "0.25",
    libraryDependencies += "au.id.tmm.tmm-scala-collections" %% "tmm-scala-collections-core" % tmmCollectionsVersion,
    libraryDependencies += "au.id.tmm.tmm-scala-collections" %% "tmm-scala-collections-cats" % tmmCollectionsVersion,
    libraryDependencies += "au.id.tmm.tmm-utils"             %% "tmm-utils-syntax"           % tmmUtilsVersion,
    libraryDependencies += "au.id.tmm.tmm-utils"             %% "tmm-utils-errors"           % tmmUtilsVersion,
    libraryDependencies += "au.id.tmm.tmm-utils"             %% "tmm-utils-cats"             % tmmUtilsVersion,
    libraryDependencies += "au.id.tmm.digest4s"              %% "digest4s-core"              % "0.0.1",
    libraryDependencies += "au.id.tmm.intime"                %% "intime-core"                % intimeVersion,
    libraryDependencies += "co.fs2"                          %% "fs2-core"                   % "2.4.4",
    libraryDependencies += "org.eclipse.jgit"                 % "org.eclipse.jgit"           % "5.9.0.202009080501-r",
    libraryDependencies += "org.kohsuke"                      % "github-api"                 % "1.116",
    libraryDependencies += "org.slf4j"                        % "slf4j-api"                  % "1.7.30",
    libraryDependencies += "org.slf4j"                        % "slf4j-simple"               % "1.7.30" % Runtime,
  )

lazy val cli = project
  .in(file("cli"))
  .settings(settingsHelper.settingsForSubprojectCalled("cli"))
  .settings(publish / skip := true)
  .dependsOn(lib)
  .settings(
    libraryDependencies += "au.id.tmm.tmm-scala-collections" %% "tmm-scala-collections-circe" % tmmCollectionsVersion,
    libraryDependencies += "au.id.tmm.tmm-scala-plotly"      %% "tmm-scala-plotly-core"       % "0.0.2",
    libraryDependencies += "io.circe"                        %% "circe-parser"                % "0.14.0-M1",
    libraryDependencies += "com.github.tototoshi"            %% "scala-csv"                   % "1.3.6",
  )

addCommandAlias("check", ";+test;scalafmtCheckAll")
