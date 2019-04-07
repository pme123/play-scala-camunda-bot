name := """play-scala-camunda-bot"""
organization := "pme123"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.12.8"

libraryDependencies += guice
libraryDependencies += ws

libraryDependencies += "info.mukel" %% "telegrambot4s" % "3.0.16"
libraryDependencies +=  "org.scalatra.scalate" %% "scalate-core" % "1.9.1"
libraryDependencies += "org.scalatestplus.play" %% "scalatestplus-play" % "4.0.1" % Test

