name := "ClickScript"

organization := "io.github.clickscript"

normalizedName := "clickscript"

version := "0.2.0-2.1.4"

scalaVersion := "2.11.4"

libraryDependencies ++= Seq(
  "io.gatling" % "gatling-core" % "2.1.4",
  "io.gatling" % "gatling-http" % "2.1.4",
  "org.scalatest" %% "scalatest" % "2.2.3" % "test",
  "com.typesafe.scala-logging" %% "scala-logging-slf4j" % "2.1.2"
)

publishMavenStyle := true

publishTo := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value)
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases"  at nexus + "service/local/staging/deploy/maven2")
}

publishArtifact in Test := false

pomExtra := (
  <url>https://github.com/clickscript/clickscript</url>
    <licenses>
      <license>
        <name>MIT License</name>
        <url>http://www.opensource.org/licenses/mit-license.php</url>
        <distribution>repo</distribution>
      </license>
    </licenses>
    <scm>
      <url>git@github.com:clickscript/clickscript.git</url>
      <connection>scm:git:git@github.com:clickscript/clickscript.git</connection>
    </scm>
    <developers>
      <developer>
        <id>jamespic</id>
        <name>James Pickering</name>
        <url>https://github.com/jamespic</url>
      </developer>
    </developers>
  )