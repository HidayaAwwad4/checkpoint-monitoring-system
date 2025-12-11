package com.checkpoint.utils

import com.checkpoint.models.{CheckpointStatus, Message}
import java.sql.Timestamp

object MessageAnalyzer {

  private val openKeywords = Set(
    "سالك", "سالكة", "مفتوح", "مفتوحة","بحري", "فاتح", "فاتحة",
    "open", "يعمل", "شغال", "طبيعي", "عادي"
  )

  private val closedKeywords = Set(
    "مغلق", "مغلقة", "مقفل", "مقفلة", "مخصوم", "محسوم", "محسومة",
    "closed", "مسكر", "مسكرة", "ممنوع", "معطل"
  )

  private val busyKeywords = Set(
    "أزمة", "ازمة", "أزمه", "ازمه", "زحمة", "زحمه", "ازدحام","كثافة","كثافه",
    "busy", "طابور", "انتظار", "تأخير", "مزدحم", "مزدحمة"
  )

  private val inboundKeywords = Set("للداخل","للفايت","فايت","الفايت", "داخل","دخول", "الداخل")
  private val outboundKeywords = Set("للخارج", "خارج","للطالع","لطالع","الطالع","طالع","خروج", "الخارج")

  private val statusKeywords = openKeywords ++ closedKeywords ++ busyKeywords

  private val checkpointNames = Map(
    "حوارة" -> "huwwara",
    "حاجز النفق" -> "tunnel_checkpoint",
    "النفق" -> "tunnel_checkpoint",
    "الإسكانات نصار" -> "iskanat_nssar",
    "نصار" -> "iskanat_nssar",
    "عقبة حسنة" -> "aqaba_hasna",
    "عقبة حسنه" -> "aqaba_hasna",
    "البوابة" -> "al_bawaba",
    "بوابة" -> "gate",
    "عطارة" -> "atara",
    "بوالة" -> "bwala",
    "الطيبون" -> "al_tayboun",
    "سنجل" -> "sinjil",
    "ترمسعيا" -> "turmus_ayya",
    "سلواد" -> "silwad",
    "بيرود" -> "birud",
    "المعالي" -> "al_maali",
    "النبي صالح" -> "nabi_saleh",
    "عابود" -> "aboud",
    "كفر عقب" -> "kafr_aqab",
    "عين سينيا" -> "ein_sinia",
    "العروب الجنوبي" -> "arroub_south",
    "العروب" -> "arroub",
    "بوابة فوق الجسر" -> "bridge_gate",
    "الجسر" -> "bridge",
    "عوريتا" -> "awarta",
    "عورتا" -> "awarta",
    "المربعة" -> "al_murabba",
    "دوار قدوميم" -> "qedumin_roundabout",
    "قدوميم" -> "qedumin",
    "مدخل أماتين" -> "amatain_entrance",
    "أماتين" -> "amatain",
    "الفندق" -> "al_funduq",
    "الكونتينر" -> "container",
    "بيت ايل" -> "beit_el",
    "زعترة" -> "zaatara",
    "الجلمة" -> "jalama",
    "قلنديا" -> "qalandia",
    "بيت فوريك" -> "beit_furik",
    "عناب" -> "annab",
    "شافي شمرون" -> "shavei_shomron",
    "يتسهار" -> "yitzhar"
  )



  def analyzeMessage(message: Message): Seq[CheckpointStatus] = {
    val text = message.text.trim
    val textLower = text.toLowerCase


    val lines = text.split("\n").map(_.trim).filter(_.nonEmpty)


    val statusList = lines.flatMap { line =>
      analyzeSingleLine(line, message)
    }.toSeq

    if (statusList.isEmpty) {
      analyzeSingleLine(text, message).toSeq
    } else {
      statusList
    }
  }

  private def analyzeSingleLine(line: String, message: Message): Seq[CheckpointStatus] = {
    val lineLower = line.toLowerCase


    val detectedCheckpoints = detectAllCheckpoints(lineLower)

    if (detectedCheckpoints.isEmpty) {
      return Seq.empty
    }


    val status = detectStatusFromEmojis(line)
      .getOrElse(detectStatusFromWords(lineLower))

    val direction = detectDirection(lineLower)
    val finalStatus = combineStatusWithDirection(status, direction)
    val confidence = calculateConfidence(line, lineLower, status)


    detectedCheckpoints.map { case (checkpointName, checkpointId) =>
      CheckpointStatus(
        checkpointId = checkpointId,
        checkpointName = checkpointName,
        status = finalStatus,
        location = None,
        lastUpdated = new Timestamp(System.currentTimeMillis()),
        messageContent = line,
        confidence = confidence
      )
    }
  }

  private def detectAllCheckpoints(text: String): Seq[(String, String)] = {
    val checkpoints = scala.collection.mutable.ListBuffer[(String, String)]()


    checkpointNames.foreach { case (name, id) =>
      if (text.contains(name.toLowerCase)) {
        checkpoints += ((name, id))
      }
    }


    val patterns = Seq(
      """حاجز\s+(\S+(?:\s+\S+)?)""",
      """مدخل\s+(\S+(?:\s+\S+)?)""",
      """بوابة\s+(\S+(?:\s+\S+)?)""",
      """دوار\s+(\S+(?:\s+\S+)?)""",
      """معبر\s+(\S+(?:\s+\S+)?)"""
    )

    patterns.foreach { pattern =>
      val regex = pattern.r
      regex.findAllMatchIn(text).foreach { m =>
        val name = m.group(1).trim
        val cleanName = cleanCheckpointName(name)
        if (cleanName.nonEmpty && !checkpoints.exists(_._1 == cleanName)) {
          val id = generateCheckpointId(cleanName)
          checkpoints += ((cleanName, id))
        }
      }
    }

    if (checkpoints.isEmpty) {
      detectCheckpointByContext(text).foreach { checkpoint =>
        checkpoints += checkpoint
      }
    }

    checkpoints.toSeq.distinct
  }

  private def detectCheckpoint(text: String): Option[(String, String)] = {
    detectCheckpointByPattern(text)
      .orElse(detectCheckpointFromKnownList(text))
      .orElse(detectCheckpointByContext(text))
  }

  private def detectCheckpointByPattern(text: String): Option[(String, String)] = {
    val patterns = Seq(
      """حاجز\s+(\S+(?:\s+\S+)?)""",
      """مدخل\s+(\S+(?:\s+\S+)?)""",
      """بوابة\s+(\S+(?:\s+\S+)?)""",
      """دوار\s+(\S+(?:\s+\S+)?)""",
      """معبر\s+(\S+(?:\s+\S+)?)"""
    )

    patterns.foreach { pattern =>
      val regex = pattern.r
      regex.findFirstMatchIn(text).foreach { m =>
        val name = m.group(1).trim
        val cleanName = cleanCheckpointName(name)
        if (cleanName.nonEmpty) {
          val id = generateCheckpointId(cleanName)
          return Some((cleanName, id))
        }
      }
    }
    None
  }

  private def detectCheckpointFromKnownList(text: String): Option[(String, String)] = {
    checkpointNames.find { case (name, _) =>
      text.contains(name.toLowerCase)
    }.map { case (name, id) => (name, id) }
  }

  private def detectCheckpointByContext(text: String): Option[(String, String)] = {
    val words = text.split("\\s+")

    for (i <- words.indices) {
      val word = words(i).toLowerCase

      if (statusKeywords.contains(word)) {
        if (i > 0) {
          val prevWord = words(i - 1)
          if (isValidCheckpointName(prevWord)) {
            val id = generateCheckpointId(prevWord)
            return Some((prevWord, id))
          }
        }

        if (i < words.length - 1) {
          val nextWord = words(i + 1)
          if (isValidCheckpointName(nextWord)) {
            val id = generateCheckpointId(nextWord)
            return Some((nextWord, id))
          }
        }
      }
    }

    val emojiPattern = """[✅❌🔴]""".r
    emojiPattern.findAllMatchIn(text).foreach { _ =>
      words.foreach { word =>
        if (isValidCheckpointName(word) && !statusKeywords.contains(word.toLowerCase)) {
          val id = generateCheckpointId(word)
          return Some((word, id))
        }
      }
    }

    None
  }

  private def cleanCheckpointName(name: String): String = {
    val cleanWords = name.split("\\s+").filterNot { word =>
      val lower = word.toLowerCase
      statusKeywords.contains(lower) ||
        inboundKeywords.contains(lower) ||
        outboundKeywords.contains(lower) ||
        lower.matches("[✅❌🔴]+")
    }
    cleanWords.mkString(" ").trim
  }



