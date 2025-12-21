package com.checkpoint.streaming

import com.checkpoint.models.{CheckpointStatus, DirectionStatus, Message}
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
                println(s"Processed: ${status.checkpointName} - ${status.generalStatus} " +
                  s"(↓${status.inboundStatus.status} ↑${status.outboundStatus.status})")
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

      partition.foreach { newStatus =>
        try {
          val filter = Filters.eq("checkpointId", newStatus.checkpointId)
          val existingDoc = coll.find(filter).first()

          // دمج الحالة الجديدة مع القديمة
          val finalStatus = if (existingDoc != null) {
            mergeStatuses(existingDoc, newStatus)
          } else {
            newStatus
          }

          // تحديث MongoDB
          val update = Updates.combine(
            Updates.set("checkpointName", finalStatus.checkpointName),
            Updates.set("generalStatus", finalStatus.generalStatus),
            Updates.set("inboundStatus", new Document()
              .append("status", finalStatus.inboundStatus.status)
              .append("lastUpdated", finalStatus.inboundStatus.lastUpdated)
              .append("isRecent", finalStatus.inboundStatus.isRecent)
            ),
            Updates.set("outboundStatus", new Document()
              .append("status", finalStatus.outboundStatus.status)
              .append("lastUpdated", finalStatus.outboundStatus.lastUpdated)
              .append("isRecent", finalStatus.outboundStatus.isRecent)
            ),
            Updates.set("lastUpdated", finalStatus.lastUpdated),
            Updates.set("messageContent", finalStatus.messageContent),
            Updates.set("confidence", finalStatus.confidence)
          )

          val options = new UpdateOptions().upsert(true)
          coll.updateOne(filter, update, options)

          println(s"  ✓ Upserted: ${finalStatus.checkpointName} " +
            s"(General:${finalStatus.generalStatus} ↓${finalStatus.inboundStatus.status} ↑${finalStatus.outboundStatus.status})")

        } catch {
          case e: Exception =>
            println(s"  ✗ Error upserting ${newStatus.checkpointName}: ${e.getMessage}")
        }
      }

      mongoClient.close()
    }
  }

  private def mergeStatuses(existingDoc: org.bson.Document, newStatus: CheckpointStatus): CheckpointStatus = {
    val oneHourAgo = new Timestamp(System.currentTimeMillis() - 3600000) // ساعة

    // قراءة الحالات القديمة من MongoDB
    val oldInbound = Option(existingDoc.get("inboundStatus", classOf[Document])).map { doc =>
      DirectionStatus(
        status = doc.getString("status"),
        lastUpdated = new Timestamp(doc.getDate("lastUpdated").getTime),
        isRecent = doc.getBoolean("isRecent", false)
      )
    }

    val oldOutbound = Option(existingDoc.get("outboundStatus", classOf[Document])).map { doc =>
      DirectionStatus(
        status = doc.getString("status"),
        lastUpdated = new Timestamp(doc.getDate("lastUpdated").getTime),
        isRecent = doc.getBoolean("isRecent", false)
      )
    }

    // دمج الحالات: لو الجديد unknown، خلي القديم
    val finalInbound = if (newStatus.inboundStatus.status != "unknown") {
      newStatus.inboundStatus.copy(isRecent = true)
    } else {
      oldInbound.map { old =>
        old.copy(isRecent = old.lastUpdated.after(oneHourAgo))
      }.getOrElse(DirectionStatus("unknown", newStatus.lastUpdated, isRecent = false))
    }

    val finalOutbound = if (newStatus.outboundStatus.status != "unknown") {
      newStatus.outboundStatus.copy(isRecent = true)
    } else {
      oldOutbound.map { old =>
        old.copy(isRecent = old.lastUpdated.after(oneHourAgo))
      }.getOrElse(DirectionStatus("unknown", newStatus.lastUpdated, isRecent = false))
    }

    // حساب الحالة العامة
    val finalGeneral = (finalInbound.status, finalOutbound.status) match {
      case (s1, s2) if s1 == s2 && s1 != "unknown" => s1
      case ("unknown", s) if s != "unknown" => "partial"
      case (s, "unknown") if s != "unknown" => "partial"
      case (s1, s2) if s1 != s2 && s1 != "unknown" && s2 != "unknown" => "mixed"
      case _ => "unknown"
    }

    CheckpointStatus(
      checkpointId = newStatus.checkpointId,
      checkpointName = newStatus.checkpointName,
      generalStatus = finalGeneral,
      inboundStatus = finalInbound,
      outboundStatus = finalOutbound,
      location = newStatus.location,
      lastUpdated = newStatus.lastUpdated,
      messageContent = newStatus.messageContent,
      confidence = newStatus.confidence
    )
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