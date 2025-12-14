package com.checkpoint.producer

import com.typesafe.config.ConfigFactory

object KafkaProducerApp {

  def main(args: Array[String]): Unit = {

    val config = ConfigFactory.load()

    println("=" * 70)
    println("Checkpoint Monitoring - Kafka Producer")
    println("=" * 70)
