name := """daily_routine"""

version := "1.0"

lazy val root = project in file(".")

scalaVersion := "2.11.8"

// See https://www.scala-sbt.org/1.x/docs/Using-Sonatype.html for instructions on how to publish to Sonatype.
libraryDependencies += "javazoom" % "jlayer" % "1.0.1"
