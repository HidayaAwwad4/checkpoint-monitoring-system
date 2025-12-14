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
