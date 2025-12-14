package com.checkpoint.producer
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerConfig}
import org.apache.kafka.common.serialization.StringSerializer
import java.util.Properties
import com.typesafe.config.ConfigFactory

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