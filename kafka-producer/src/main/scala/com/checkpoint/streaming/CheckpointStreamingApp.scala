package com.checkpoint.streaming

import com.checkpoint.models.{CheckpointStatus, Message}
import com.checkpoint.utils.{BloomFilter, MessageAnalyzer}
import org.apache.spark.sql.{DataFrame, SparkSession}
import org.apache.spark.sql.functions._

import com.typesafe.config.ConfigFactory





object CheckpointStreamingApp {

  def main(args: Array[String]): Unit = {

    System.setProperty("hadoop.home.dir", "C:\\hadoop")

    val config = ConfigFactory.load()

    val spark = SparkSession.builder()
      .appName(config.getString("spark.app.name"))
      .master(config.getString("spark.master"))
      .config("spark.mongodb.output.uri", config.getString("mongodb.uri"))
      .config("spark.mongodb.output.database", config.getString("mongodb.database"))
      .config("spark.mongodb.output.collection", config.getString("mongodb.collection"))
      .getOrCreate()

    spark.sparkContext.setLogLevel("WARN")

    import spark.implicits._

    val bloomFilter = BloomFilter(
      expectedElements = config.getInt("bloom.filter.expected.elements"),
      falsePositiveRate = config.getDouble("bloom.filter.false.positive.rate")
    )

    println("=" * 60)
    println("Starting Checkpoint Monitoring Streaming Application")
    println("=" * 60)
    println(bloomFilter.getStats)
    println("=" * 60)

    val kafkaBootstrapServers = config.getString("kafka.bootstrap.servers")
    val kafkaTopic = config.getString("kafka.topic")

    val kafkaDF = spark.readStream
      .format("kafka")
      .option("kafka.bootstrap.servers", kafkaBootstrapServers)
      .option("subscribe", kafkaTopic)
      .option("startingOffsets", "earliest")
      .load()

    val messagesDF = kafkaDF
      .selectExpr("CAST(value AS STRING) as json")
      .as[String]

    val processedDF = messagesDF
      .flatMap { jsonStr =>
        try {
          val message = parseMessage(jsonStr)

          val messageHash = Message.generateHash(message)

          if (bloomFilter.mightContain(messageHash)) {
            println(s"Duplicate message detected (Bloom Filter): ${message.messageId}")
            Seq.empty[CheckpointStatus]
          } else {
            bloomFilter.add(messageHash)

            val results = MessageAnalyzer.analyzeMessage(message)

            if (results.isEmpty) {
              println(s"No checkpoint detected in message: ${message.text.take(50)}...")
              Seq.empty[CheckpointStatus]
            } else {
              results.foreach { status =>
                println(s"Processed: ${status.checkpointName} - ${status.status}")
              }
              results
            }
          }
        } catch {
          case e: Exception =>
            println(s"Error processing message: ${e.getMessage}")
            Seq.empty[CheckpointStatus]
        }
      }


  }




  }


