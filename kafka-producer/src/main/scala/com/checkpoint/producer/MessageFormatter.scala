package com.checkpoint.producer

import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.json4s.jackson.Serialization.write
import java.sql.Timestamp

case class TelegramMessage(
                            messageId: String,
                            text: String,
                            timestamp: Long,
                            channelId: String,
                            metadata: Map[String, String] = Map.empty
                          )

object MessageFormatter {
  implicit val formats: Formats = DefaultFormats


  def toKafkaMessage(telegramMsg: TelegramMessage): String = {
    write(Map(
      "message_id" -> telegramMsg.messageId,
      "text" -> telegramMsg.text,
      "timestamp" -> telegramMsg.timestamp,
      "channel_id" -> telegramMsg.channelId,
      "metadata" -> telegramMsg.metadata
    ))
  }
