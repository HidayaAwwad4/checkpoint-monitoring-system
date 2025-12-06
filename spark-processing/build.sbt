name := "checkpoint-monitoring-spark"

version := "1.0"

scalaVersion := "2.12.15"

// Spark dependencies
libraryDependencies ++= Seq(
  // Spark Core
  "org.apache.spark" %% "spark-core" % "3.0.3",

  // Spark SQL
  "org.apache.spark" %% "spark-sql" % "3.0.3",

  // Spark Streaming
  "org.apache.spark" %% "spark-streaming" % "3.0.3",

  // Spark Streaming Kafka integration
  "org.apache.spark" %% "spark-streaming-kafka-0-10" % "3.0.3",

  // Spark SQL Kafka integration (Structured Streaming)
  "org.apache.spark" %% "spark-sql-kafka-0-10" % "3.0.3",

  // MongoDB Spark Connector
  "org.mongodb.spark" %% "mongo-spark-connector" % "3.0.1",

  // JSON parsing
  "org.json4s" %% "json4s-native" % "3.6.11",
  "org.json4s" %% "json4s-jackson" % "3.6.11",

  // Guava for Bloom Filter (optional - يمكن نكتب implementation خاص)
  "com.google.guava" % "guava" % "30.1-jre",

  // Logging
  "org.slf4j" % "slf4j-api" % "1.7.30",
  "org.slf4j" % "slf4j-log4j12" % "1.7.30",

  // Testing
  "org.scalatest" %% "scalatest" % "3.2.9" % Test
)

// Compiler options
scalacOptions ++= Seq(
  "-encoding", "UTF-8",
  "-deprecation",
  "-feature",
  "-unchecked"
)

// Exclude some problematic dependencies
assembly / assemblyMergeStrategy := {
  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  case "reference.conf" => MergeStrategy.concat
  case x => MergeStrategy.first
}

run / fork := true
run / javaOptions ++= Seq(
  "-Xms512M",
  "-Xmx2048M"
)