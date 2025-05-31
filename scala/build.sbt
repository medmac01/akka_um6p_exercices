lazy val commonSettings = Seq(
  version := "1.0",
  name := "akka-cookbook",
  organization := "io.github.gpetri",
  scalaVersion := "2.13.16"
)

scalacOptions ++= Seq("-language:postfixOps")


libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.6.20",
  "com.typesafe.akka" %% "akka-remote" % "2.6.20",
  "com.typesafe.akka" %% "akka-slf4j" % "2.6.20",
  "ch.qos.logback" % "logback-classic" % "1.2.12",
  "com.typesafe.akka" %% "akka-cluster" % "2.6.20",
  "com.typesafe.akka" %% "akka-cluster-tools" % "2.6.20",
  "com.typesafe.akka" %% "akka-serialization-jackson" % "2.6.20"
)
