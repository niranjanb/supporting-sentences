import sbt.Keys._
import sbt._
import spray.revolver.RevolverPlugin._

object BuildSettings extends Build {

  val solvers = "org.allenai.solvers" %% "solvers" % "0.1.1-SNAPSHOT"

  val allenAiCommon = "org.allenai.common" %% "common-core" % "2014.06.10-0-SNAPSHOT"

  val ari = "org.allenai.ari" %% "ari-interface" % "2014.06.10-0-SNAPSHOT"
  val ermineApi = "org.allenai.extraction" %% "extraction-api" % "2014.5.14-0-SNAPSHOT"

  val gremlin = "com.michaelpollmeier" %% "gremlin-scala" % "2.5.0"

  val mockito = "org.mockito" % "mockito-all" % "1.9.5"

  val sistanlpCore = "edu.arizona.sista" % "sistanlp-core" % "2.0"
  val sistaProcessors = "edu.arizona.sista"  % "processors" % "2.0"

  //Arizona dependencies
  val stanfordVersion = "3.2.0"
  val stanford = "edu.stanford.nlp" % "stanford-corenlp" % stanfordVersion
  val stanfordModels = "edu.stanford.nlp" % "stanford-corenlp" % stanfordVersion classifier("models")
  val luceneCore = "org.apache.lucene" % "lucene-core" % "3.0.3"
  val xom = "xom" % "xom" % "1.2.5"
  val jodaTime = "joda-time" % "joda-time" % "2.1"
  val deJollyday = "de.jollyday" % "jollyday" % "0.4.7"


  val tefclient = "org.allenai.tef" %% "tef-client" % "0.9.1-SNAPSHOT"
  val tefcommon = "org.allenai.tef" %% "tef-common" % "0.9.1-SNAPSHOT"
  val tefextractors = ("org.allenai.tef" %% "tef-extractors" % "0.9.1-SNAPSHOT")
    .exclude("edu.washington.cs.knowitall.clearnlp", "clear-pred-models")
    .exclude("edu.washington.cs.knowitall.clearnlp", "clear-role-models")
    .exclude("edu.washington.cs.knowitall.clearnlp", "clear-parse-models")
    .exclude("edu.washington.cs.knowitall.clearnlp", "clear-srl-models")

  // Inference depends on tef-extractors, which depends on uw-nlptools 2.4.4, so until
  // that dependency is updated, we depend on uw-nlptools 2.4.4 rather than allenai nlptools.
  // allenai nlptools isn't compatible with uw 2.4.4, so there's a real change to be made in
  // tef-extractors (more than just changing the dependency artifact name).
  val openNlpPostagger = "edu.washington.cs.knowitall.nlptools" %% "nlptools-postag-opennlp" % "2.4.4"

  // Since we depend on uw-nlptools 2.4.4, exclude allenai-nlptools via textualEntailment because
  // there's a binary conflict at runtime in tef-extractors, and because (luckilly) uw-nlptools
  // works with the textual entailment client.
  val textualEntailment = ("org.allenai.textual-entailment" %% "interface" % "2013.03.05-0-SNAPSHOT")
    .exclude("org.allenai.nlptools", "core_2.10")

  val morpha = "edu.washington.cs.knowitall.nlptools" %% "nlptools-stem-morpha" % "2.4.4"

  val logbackClassic = "ch.qos.logback" % "logback-classic" % "1.0.13"

  val datastoreCommon = "org.allenai.ari-datastore" %% "interface" % "2014.5.16-0-SNAPSHOT"
  val datastoreClient = "org.allenai.ari-datastore" %% "client" % "2014.5.16-0-SNAPSHOT"

  val globalBuildSettings =
  Revolver.settings ++
  Seq(
    // Some systems (like laptops) seem to pick a bad default heap size.
    // Force something big enough that we don't crash.
    organization := "org.allenai.ari.solvers",
    crossScalaVersions := Seq("2.10.4"),
    scalaVersion <<= crossScalaVersions { (vs: Seq[String]) => vs.head },
    scalacOptions ++= Seq("-Xlint", "-deprecation", "-feature"),
    javaOptions += "-Xmx1G",
    //conflictManager := ConflictManager.strict,
    resolvers ++= Seq(
      "AllenAI Snapshots" at "http://utility.allenai.org:8081/nexus/content/repositories/snapshots",
      "AllenAI Releases" at "http://utility.allenai.org:8081/nexus/content/repositories/releases",
      "Sonatype SNAPSHOTS" at "https://oss.sonatype.org/content/repositories/snapshots/",
      "spray" at "http://repo.spray.io/",
      "Typesafe Releases" at "http://repo.typesafe.com/typesafe/releases/")
  )
}
