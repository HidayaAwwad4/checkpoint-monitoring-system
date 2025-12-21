package com.checkpoint.producer

import org.apache.kafka.clients.producer.{KafkaProducer, ProducerConfig, ProducerRecord}
import org.apache.kafka.common.serialization.StringSerializer
import com.typesafe.config.ConfigFactory
import java.util.Properties
import scala.util.{Try, Success, Failure}

object KafkaProducerApp {

  def main(args: Array[String]): Unit = {

    val config = ConfigFactory.load()

    println("=" * 70)
    println("Checkpoint Monitoring - Kafka Producer")
    println("=" * 70)

    val kafkaProps = new Properties()
    kafkaProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
      config.getString("kafka.bootstrap.servers"))
    kafkaProps.put(ProducerConfig.CLIENT_ID_CONFIG,
      config.getString("kafka.client.id"))
    kafkaProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
      classOf[StringSerializer].getName)
    kafkaProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
      classOf[StringSerializer].getName)
    kafkaProps.put(ProducerConfig.ACKS_CONFIG,
      config.getString("kafka.producer.acks"))
    kafkaProps.put(ProducerConfig.RETRIES_CONFIG,
      config.getInt("kafka.producer.retries").toString)

    val producer = new KafkaProducer[String, String](kafkaProps)
    val topic = config.getString("kafka.topic")

    val botToken = config.getString("telegram.bot.token")
    val chatId = config.getString("telegram.chat.id")
    val scraper = new TelegramChannelScraper(botToken, chatId)
    val pollInterval = config.getInt("telegram.poll.interval.seconds") * 1000

    println(s"Kafka Producer started")
    println(s"Topic: $topic")
    println(s"Bootstrap Servers: ${config.getString("kafka.bootstrap.servers")}")
    println(s"Chat ID: $chatId")
    println(s"Polling every ${pollInterval / 1000} seconds")
    println("=" * 70)
    println("Waiting for messages...\n")

    var running = true
    var messageCount = 0

    sys.addShutdownHook {
      println("\nShutting down...")
      running = false
      scraper.close()
      producer.close()
      println("Producer closed")
    }

    while (running) {
      try {
        val messages = scraper.getLatestMessages()

        messages.foreach { telegramMsg =>
          val kafkaMessage = MessageFormatter.toKafkaMessage(telegramMsg)
          val record = new ProducerRecord[String, String](
            topic,
            telegramMsg.messageId,
            kafkaMessage
          )

          Try(producer.send(record).get()) match {
            case Success(metadata) =>
              messageCount += 1
              println(s"[$messageCount] Message sent to Kafka")
              println(s"    Topic: ${metadata.topic()}")
              println(s"    Partition: ${metadata.partition()}")
              println(s"    Offset: ${metadata.offset()}")
              println(s"    Text: ${telegramMsg.text.take(50)}...")
              println()

            case Failure(e) =>
              println(s"Failed to send message: ${e.getMessage}")
          }
        }

        Thread.sleep(pollInterval)

      } catch {
        case e: InterruptedException =>
          println("Producer interrupted")
          running = false
        case e: Exception =>
          println(s"Error in main loop: ${e.getMessage}")
          Thread.sleep(5000)
      }
    }
  }
}