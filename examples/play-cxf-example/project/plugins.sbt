// Comment to get more information during initialization
logLevel := Level.Warn

// The Typesafe repository 
resolvers += "Typesafe repository" at "https://repo.typesafe.com/typesafe/releases/"

// Use the Play sbt plugin for Play projects
addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.7.3")

addSbtPlugin("io.paymenthighway.sbt" % "sbt-cxf" % "1.6")

addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.9.0")
