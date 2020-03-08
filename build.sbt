name := "ratelimit-example"

version := "0.1"

scalaVersion := "2.13.1"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-http" % "10.1.11",
  "com.typesafe.akka" %% "akka-stream" % "2.5.26",
  "com.typesafe.akka" %% "akka-http-spray-json" % "10.1.11",
  "com.github.blemale" %% "scaffeine" % "3.1.0",

  "org.specs2" %% "specs2-core" % "4.8.3" % Test,
  "com.typesafe.akka" %% "akka-stream-testkit" % "2.5.26" % Test,
  "com.typesafe.akka" %% "akka-http-testkit" % "10.1.11" % Test
)
