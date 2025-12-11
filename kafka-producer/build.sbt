name := "checkpoint-kafka-producer"

version := "1.0"

scalaVersion := "2.12.15"

libraryDependencies ++= Seq(
  // Kafka Client
  "org.apache.kafka" % "kafka-clients" % "3.9.0",

  "org.json4s" %% "json4s-jackson" % "4.0.6",

  // Typesafe Config
  "com.typesafe" % "config" % "1.4.1",

  // Logging
  "org.slf4j" % "slf4j-api" % "1.7.36",
  "org.slf4j" % "slf4j-simple" % "1.7.36",

  // Scala Async HTTP Client (for Telegram API)
  "com.softwaremill.sttp.client3" %% "core" % "3.3.18"
)

scalacOptions ++= Seq(
  "-encoding", "UTF-8",
  "-deprecation",
  "-feature",
  "-unchecked"
)