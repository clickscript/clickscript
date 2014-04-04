name := "ClickScript"

organization := "io.github.clickscript"

normalizedName := "clickscript"

version := "0.1-SNAPSHOT"

scalaVersion := "2.10.4"

libraryDependencies ++= Seq(
  "io.gatling" % "gatling-core" % "2.0.0-SNAPSHOT",
  "io.gatling" % "gatling-http" % "2.0.0-SNAPSHOT",
  "org.scalatest" %% "scalatest" % "2.0" % "test"
)