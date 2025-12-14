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

  def parseTelegramUpdate(updateJson: String): Option[TelegramMessage] = {
    try {
      implicit val formats: DefaultFormats.type = DefaultFormats
      val json = parse(updateJson)

      val message = (json \ "message").extractOpt[JValue]
        .orElse((json \ "channel_post").extractOpt[JValue])

      message.flatMap { msg =>
        val messageId = (msg \ "message_id").extract[Int].toString
        val text = (msg \ "text").extractOpt[String].getOrElse("")
        val timestamp = (msg \ "date").extract[Long] * 1000
        val channelId = (msg \ "chat" \ "username").extractOpt[String]
          .orElse((msg \ "chat" \ "title").extractOpt[String])
          .getOrElse("unknown")

        if (text.nonEmpty) {
          Some(TelegramMessage(
            messageId = messageId,
            text = text,
            timestamp = timestamp,
            channelId = channelId
          ))
        } else {
          None
        }
      }
    } catch {
      case e: Exception =>
        println(s"Error parsing Telegram update: ${e.getMessage}")
        None
    }
  }
}