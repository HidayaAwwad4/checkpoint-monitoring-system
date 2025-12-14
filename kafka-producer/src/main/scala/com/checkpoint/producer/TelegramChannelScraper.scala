package com.checkpoint.producer

import sttp.client3._
import org.json4s._
import org.json4s.jackson.JsonMethods._

class TelegramChannelScraper(botToken: String, chatId: String) {

  val backend: SttpBackend[Identity, Any] = HttpURLConnectionBackend()
  val apiUrl = "https://api.telegram.org"

  private var lastUpdateId: Long = 0

  def getLatestMessages(): List[TelegramMessage] = {
    try {
      println(s"[DEBUG] Fetching updates from Bot API...")

      val url = s"$apiUrl/bot$botToken/getUpdates?offset=$lastUpdateId&timeout=30&allowed_updates=[%22channel_post%22]"

      val request = basicRequest
        .get(uri"$url")
        .response(asString)

      val response = request.send(backend)

      response.body match {
        case Right(body) =>
          println(s"[DEBUG] Got response")
          parseUpdates(body)
        case Left(error) =>
          println(s"[ERROR] Error fetching updates: $error")
          List.empty
      }
    } catch {
      case e: Exception =>
        println(s"[ERROR] Exception in getLatestMessages: ${e.getMessage}")
        e.printStackTrace()
        List.empty
    }
  }
  private def parseUpdates(responseBody: String): List[TelegramMessage] = {
    try {
      implicit val formats: DefaultFormats.type = DefaultFormats
      val json = parse(responseBody)

      val ok = (json \ "ok").extract[Boolean]

      if (ok) {
        val updates = (json \ "result").extract[List[JValue]]

        println(s"[DEBUG] Found ${updates.size} updates")

        if (updates.nonEmpty) {
          val maxUpdateId = updates.map(u => (u \ "update_id").extract[Long]).max
          lastUpdateId = maxUpdateId + 1
          println(s"[DEBUG] Updated lastUpdateId to: $lastUpdateId")
          val messages = updates.flatMap { update =>
            try {
              val channelPost = (update \ "channel_post").extractOpt[JValue]

              channelPost.flatMap { post =>
                val messageId = (post \ "message_id").extract[Int].toString
                val date = (post \ "date").extract[Long] * 1000

                val text = (post \ "text").extractOpt[String]
                val caption = (post \ "caption").extractOpt[String]

                val hasPhoto = (post \ "photo").extractOpt[List[JValue]].exists(_.nonEmpty)
                val hasVideo = (post \ "video").extractOpt[JValue].isDefined
                val hasDocument = (post \ "document").extractOpt[JValue].isDefined

                val hasMedia = hasPhoto || hasVideo || hasDocument

                val finalText = text.orElse(caption).getOrElse("").trim

                val mediaType = if (hasPhoto) "photo"
                else if (hasVideo) "video"
                else if (hasDocument) "document"
                else "none"

                if (finalText.nonEmpty) {
                  val mediaInfo = if (hasMedia) s" [$mediaType with caption]" else ""
                  println(s"[DEBUG] Message $messageId$mediaInfo - Text: ${finalText.take(50)}...")

                  Some(TelegramMessage(
                    messageId = messageId,
                    text = finalText,
                    timestamp = date,
                    channelId = chatId
                  ))
                } else {
                  if (hasMedia) {
                    println(s"[DEBUG] Message $messageId [$mediaType] - Skipped (no text/caption)")
                  } else {
                    println(s"[DEBUG] Message $messageId - Skipped (no text)")
                  }
                  None
                }
              }
            } catch {
              case e: Exception =>
                println(s"[ERROR] Error parsing update: ${e.getMessage}")
                None
            }
          }

          println(s"[DEBUG] Returning ${messages.size} messages (text/caption only)")
          messages
        } else {
          val description = (json \ "description").extractOpt[String]
          println(s"[ERROR] Telegram API returned ok=false: ${description.getOrElse("unknown error")}")
          List.empty
        }
      }


    }
    }




