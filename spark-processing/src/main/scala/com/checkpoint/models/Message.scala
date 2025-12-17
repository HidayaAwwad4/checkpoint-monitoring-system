package com.checkpoint.models

import java.sql.Timestamp

case class Message(
                    messageId: String,
                    text: String,
                    timestamp: Timestamp,
                    channelId: String,
                    metadata: Map[String, String] = Map.empty
                  )
