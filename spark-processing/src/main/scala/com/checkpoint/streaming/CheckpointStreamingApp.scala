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

    val query = processedDF.writeStream
      .foreachBatch { (batchDS: org.apache.spark.sql.Dataset[CheckpointStatus], batchId: Long) =>
        if (!batchDS.isEmpty) {
          val count = batchDS.count()
          println(s"\nProcessing batch $batchId with $count records")


          upsertToMongoDB(batchDS, config)

          println(s"Batch $batchId saved to MongoDB ($count checkpoints)\n")
        }
      }
      .trigger(Trigger.ProcessingTime(s"${config.getInt("spark.streaming.batch.interval")} seconds"))
      .option("checkpointLocation", config.getString("spark.streaming.checkpoint.location"))
      .start()

    println("\nStreaming application started successfully!")
    println("Waiting for messages from Kafka...\n")

    query.awaitTermination()
  }

  private def upsertToMongoDB(
                               batchDS: org.apache.spark.sql.Dataset[CheckpointStatus],
                               config: com.typesafe.config.Config
                             ): Unit = {
    import batchDS.sparkSession.implicits._

    val mongoUri = config.getString("mongodb.uri")
    val database = config.getString("mongodb.database")
    val collection = config.getString("mongodb.collection")

    batchDS.rdd.foreachPartition { partition =>
      import com.mongodb.client.MongoClients
      import com.mongodb.client.model.{UpdateOptions, Filters, Updates}
      import org.bson.Document

      val mongoClient = MongoClients.create(mongoUri)
      val db = mongoClient.getDatabase(database)
      val coll = db.getCollection(collection)

      partition.foreach { status =>
        try {
          val filter = Filters.eq("checkpointId", status.checkpointId)
          val update = Updates.combine(
            Updates.set("checkpointName", status.checkpointName),
            Updates.set("status", status.status),
            Updates.set("lastUpdated", status.lastUpdated),
            Updates.set("messageContent", status.messageContent),
            Updates.set("confidence", status.confidence)
          )

          val options = new UpdateOptions().upsert(true)

          coll.updateOne(filter, update, options)

          println(s"  âœ“ Upserted: ${status.checkpointName} (${status.status})")
        } catch {
          case e: Exception =>
            println(s"  âœ— Error upserting ${status.checkpointName}: ${e.getMessage}")
        }
      }

      mongoClient.close()
    }
  }

  private def parseMessage(jsonStr: String): Message = {
    import org.json4s._
    import org.json4s.native.JsonMethods._

    implicit val formats: DefaultFormats.type = DefaultFormats

    val json = parse(jsonStr)

    Message(
      messageId = (json \ "message_id").extract[String],
      text = (json \ "text").extract[String],
      timestamp = new Timestamp((json \ "timestamp").extract[Long]),
      channelId = (json \ "channel_id").extract[String],
      metadata = (json \ "metadata").extractOpt[Map[String, String]].getOrElse(Map.empty)
    )
  }
}