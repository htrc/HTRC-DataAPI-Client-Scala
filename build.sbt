import sbt.{Credentials, Path}
import sbtbuildinfo.BuildInfoPlugin

showCurrentGitBranch

git.useGitDescribe := true

lazy val commonSettings = Seq(
  organization := "org.hathitrust.htrc",
  organizationName := "HathiTrust Research Center",
  organizationHomepage := Some(url("https://www.hathitrust.org/htrc")),
  scalaVersion := "2.12.4",
  scalacOptions ++= Seq(
    "-feature",
    "-language:postfixOps",
    "-language:implicitConversions",
    "-target:jvm-1.8"
  ),
  resolvers ++= Seq(
    "I3 Repository" at "http://nexus.htrc.illinois.edu/content/groups/public",
    Resolver.mavenLocal
  ),
  publishTo := {
    val nexus = "https://nexus.htrc.illinois.edu/"
    if (isSnapshot.value)
      Some("HTRC Snapshots Repository" at nexus + "content/repositories/snapshots")
    else
      Some("HTRC Releases Repository"  at nexus + "content/repositories/releases")
  },
  credentials += Credentials(Path.userHome / ".ivy2" / ".credentials"),
  buildInfoOptions ++= Seq(BuildInfoOption.BuildTime),
  buildInfoPackage := "build",
  buildInfoKeys ++= Seq[BuildInfoKey](
    "gitSha" -> git.gitHeadCommit.value.getOrElse("N/A"),
    "gitBranch" -> git.gitCurrentBranch.value,
    "gitVersion" -> git.gitDescribedVersion.value.getOrElse("N/A"),
    "gitDirty" -> git.gitUncommittedChanges.value,
    "nameWithVersion" -> s"${name.value} ${version.value}"
  ),
  packageOptions in (Compile, packageBin) += Package.ManifestAttributes(
    ("Git-Sha", git.gitHeadCommit.value.getOrElse("N/A")),
    ("Git-Branch", git.gitCurrentBranch.value),
    ("Git-Version", git.gitDescribedVersion.value.getOrElse("N/A")),
    ("Git-Dirty", git.gitUncommittedChanges.value.toString),
    ("Build-Date", new java.util.Date().toString)
  )
)

lazy val `dataapi-client` = (project in file("."))
  .enablePlugins(GitVersioning, GitBranchPrompt, JavaAppPackaging, BuildInfoPlugin)
  .settings(commonSettings)
  .settings(
    name := "dataapi-client",
    licenses += "Apache2" -> url("http://www.apache.org/licenses/LICENSE-2.0"),
    libraryDependencies ++= Seq(
      "org.hathitrust.htrc"           %% "pairtree-to-text"       % "5.1.2",
      "org.scalatest"                 %% "scalatest"              % "3.0.4"        % "test"
    ),
    crossScalaVersions := Seq("2.11.12", "2.12.4")
  )
