package com.checkpoint.streaming

import com.checkpoint.models.{CheckpointStatus, Message}
import com.checkpoint.utils.{BloomFilter, MessageAnalyzer}
import org.apache.spark.sql.{DataFrame, SparkSession}
import org.apache.spark.sql.functions._
import org.apache.spark.sql.streaming.Trigger
import com.typesafe.config.ConfigFactory
import java.sql.Timestamp
import com.mongodb.spark.MongoSpark
import com.mongodb.spark.config.WriteConfig
import org.bson.Document

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


  }




}