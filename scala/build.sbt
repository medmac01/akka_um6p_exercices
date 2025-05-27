lazy val commonSettings = Seq(
  version := "1.0",
  name := "akka-cookbook",
  organization := "io.github.gpetri",
  scalaVersion := "2.13.16"
)

scalacOptions ++= Seq("-language:postfixOps")


libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor-typed" % "2.8.5",
  "ch.qos.logback" % "logback-classic" % "1.5.6",
)
