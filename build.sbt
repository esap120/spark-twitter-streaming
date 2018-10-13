
name := "twitter_streaming"

version := "1.0"

sbtVersion := "1.2.1"

scalaVersion := "2.11.12"

logLevel in assembly := Level.Error

crossPaths := false

assemblyMergeStrategy in assembly := {
  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  case x => MergeStrategy.first
}

resolvers += "spark-stemming" at "https://dl.bintray.com/spark-packages/maven/"

libraryDependencies += "org.apache.bahir" %% "spark-streaming-twitter" % "2.2.1"
libraryDependencies += "org.apache.spark" %% "spark-core" % "2.2.1" % "provided"
libraryDependencies += "org.apache.spark" %% "spark-streaming" % "2.2.1" % "provided"
libraryDependencies += "org.apache.spark" %% "spark-mllib" % "2.2.1" % "provided"
libraryDependencies += "master" % "spark-stemming" % "0.2.0"
libraryDependencies += "com.github.scopt" %% "scopt" % "3.7.0"
