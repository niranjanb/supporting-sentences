import sbt._

addSbtPlugin("com.github.mpeltonen" % "sbt-idea" % "1.5.1")

addSbtPlugin("com.github.gseitz" % "sbt-release" % "0.8")

addSbtPlugin("com.typesafe.sbt" % "sbt-scalariform" % "1.2.1")

addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "0.6.4")

// Revolver, for auto-reloading of changed files in sbt.
// See https://github.com/spray/sbt-revolver .
addSbtPlugin("io.spray" % "sbt-revolver" % "0.7.1")

// We use Bintray for hosting our plugins because it plays nice with
// Ivy and is the recommended host per Typesafe (see http://www.scala-sbt.org/0.13/docs/Bintray-For-Plugins.html)
resolvers += Resolver.url(
  "allenai-bintray-sbt-plugins",
  url("http://dl.bintray.com/content/allenai/sbt-plugins"))(Resolver.ivyStylePatterns)

lazy val ai2PluginsVersion = "2014.07.03-0"

addSbtPlugin("org.allenai.plugins" % "allenai-sbt-travis-publisher" % ai2PluginsVersion)

addSbtPlugin("org.allenai.plugins" % "allenai-sbt-format" % ai2PluginsVersion)

addSbtPlugin("org.allenai.plugins" % "allenai-sbt-version-injector" % ai2PluginsVersion)

addSbtPlugin("org.allenai.plugins" % "allenai-sbt-deploy" % ai2PluginsVersion)
