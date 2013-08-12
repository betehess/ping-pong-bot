import sbt._
import Keys._
import play.Project._

object ApplicationBuild extends Build {

  val appName         = "ping-pong-bot"
  val appVersion      = "1.0-SNAPSHOT"

  val main = play.Project(appName, appVersion).settings(
    scalaVersion := "2.10.2",
    scalacOptions += "-feature",
    resolvers += "Sonatype Snapshots" at "http://oss.sonatype.org/content/repositories/snapshots/",
    resolvers += "Typesafe repository releases" at "http://repo.typesafe.com/typesafe/releases/",
    libraryDependencies += "org.scalatest" %% "scalatest" % "2.0.M6-SNAP35" % "test",
    libraryDependencies += "org.reactivemongo" % "reactivemongo_2.10" % "0.10-SNAPSHOT",
    libraryDependencies += "org.reactivemongo" %% "play2-reactivemongo" % "0.10-SNAPSHOT"
  )

}
