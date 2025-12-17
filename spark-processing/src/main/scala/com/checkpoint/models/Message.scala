package com.checkpoint.models

import java.sql.Timestamp

case class Message(
                    messageId: String,
                    text: String,
                    timestamp: Timestamp,
                    channelId: String,
                    metadata: Map[String, String] = Map.empty
                  )
package com.checkpoint.models

import java.sql.Timestamp

case class Message(
                    messageId: String,
                    text: String,
                    timestamp: Timestamp,
                    channelId: String,
                    metadata: Map[String, String] = Map.empty
                  )

object Message {

  def fromJsonString(json: String): Message = {

    import org.json4s._
    import org.json4s.native.JsonMethods._

    implicit val formats: DefaultFormats.type = DefaultFormats

    val parsed = parse(json)

    Message(
      messageId = (parsed \ "message_id").extract[String],
      text = (parsed \ "text").extract[String],
      timestamp = new Timestamp((parsed \ "timestamp").extract[Long]),
      channelId = (parsed \ "channel_id").extract[String],
      metadata = (parsed \ "metadata")
        .extractOpt[Map[String, String]]
        .getOrElse(Map.empty)
    )
  }

}
