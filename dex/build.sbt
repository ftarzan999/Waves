import Dependencies.akkaModule
import DockerSettings.additionalFiles

enablePlugins(ExtensionPackaging)

Test / fork := true

libraryDependencies ++= Seq(
  "com.typesafe.akka"   %% "akka-stream-kafka"         % "1.0-RC1",
  "com.github.dnvriend" %% "akka-persistence-inmemory" % "2.5.15.1" % "test",
  ("org.iq80.leveldb" % "leveldb" % "0.9" % "test").exclude("com.google.guava", "guava")
) ++ Seq(
  akkaModule("testkit"),
  akkaModule("persistence-tck")
).map(_ % "test") ++ Dependencies.Test

docker / additionalFiles += (Universal / stage).value
