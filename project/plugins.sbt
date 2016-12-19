// Comment to get more information during initialization
logLevel := Level.Debug

// Resolvers
resolvers += "Typesafe repository" at "https://repo.typesafe.com/typesafe/releases/"

// Sbt plugins
addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.5.10")

addSbtPlugin("org.scala-js" % "sbt-scalajs" % "0.6.8")

addSbtPlugin("com.vmunier" % "sbt-play-scalajs" % "0.2.1")

addSbtPlugin("com.typesafe.sbt" % "sbt-gzip" % "1.0.0")
