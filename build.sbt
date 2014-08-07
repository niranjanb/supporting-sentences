import BuildSettings._

name := "supporting-sentences"

description := "Ari solver implementations and supporting entailment algorithms."

version := "0.1.1-SNAPSHOT"

scalacOptions in ThisBuild ++= Seq("-feature")

scalaVersion := "2.10.3"

lazy val root = Project(id = "solvers-root", base = file("."), settings = globalBuildSettings)
