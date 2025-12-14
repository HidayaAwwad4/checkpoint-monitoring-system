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
  }

