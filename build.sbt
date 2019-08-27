name := """play-scala-camunda-bot"""
organization := "pme123"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.12.8"

libraryDependencies ++= Seq(
  guice,
  ws,
  "info.mukel" %% "telegrambot4s" % "3.0.16",
  "org.scalatra.scalate" %% "scalate-core" % "1.9.1",
  "org.scalaz" %% "scalaz-zio" % "0.18",
  "com.typesafe.akka" %% "akka-http" % "10.1.8",
  "org.scalatestplus.play" %% "scalatestplus-play" % "4.0.1" % Test,
)

