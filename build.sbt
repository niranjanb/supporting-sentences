import BuildSettings._

name := "supporting-sentences"

description := "Project for finding supporting sentences for Aristo questions."

version := "0.1.0-SNAPSHOT"

scalacOptions in ThisBuild ++= Seq("-feature")

scalaVersion := "2.10.3"

lazy val root = Project(id = "supporting-sentences", base = file("."), settings = globalBuildSettings)

libraryDependencies ++= Seq(
  //
  solvers,
  weka,
  // Implement solver interface.
  ari,
  // Process incoming questions.
  ermineApi,
  // Graph library.
  gremlin,
  // Search algorithm library.
  "com.googlecode.aima-java" % "aima-core" % "0.10.5",
  // Words entailment library
  textualEntailment,
  // POS tagger
  openNlpPostagger,
  // Stemmer
  morpha,
  // Ari Datastore types and client
  datastoreCommon,
  datastoreClient,
  logbackClassic,
  allenAiCommon,
  jsoup, sentencer)
