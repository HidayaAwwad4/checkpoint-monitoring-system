package com.checkpoint.utils

import com.checkpoint.models.{CheckpointStatus, Message}
import java.sql.Timestamp
import scala.collection.mutable

object MessageAnalyzer {

  private val openKeywords = Set(
    "سالك", "سالكة", "سالكين", "مفتوح", "مفتوحة", "مفتوحين", "مفتوحات",
    "بحري", "فاتح", "فاتحة", "فاتحين", "open", "يعمل", "شغال",
    "طبيعي", "عادي", "ماشي", "ماشيين", "ماشية"
  )

  private val closedKeywords = Set(
    "مغلق", "مغلقة", "مغلقين", "مغلقات", "مقفل", "مقفلة", "مقفلين",
    "مخصوم", "محسوم", "محسومة", "محسومين", "closed", "مسكر", "مسكرة",
    "مسكرين", "مسكّر", "مسكّرين", "ممنوع", "ممنوعين", "معطل", "معطلين", "واقف"
  )

  private val busyKeywords = Set(
    "أزمة", "ازمة", "أزمه", "ازمه", "أزمات", "ازمات",
    "زحمة", "زحمه", "زحمات", "ازدحام", "كثافة", "كثافه",
    "busy", "طابور", "طوابير", "انتظار", "تأخير", "تأخيرات",
    "مزدحم", "مزدحمة", "مزدحمين", "صف", "زنقة"
  )

  private val inboundKeywords = Set(
    "للداخل", "للفايت", "فايت", "الفايت", "داخل", "دخول", "الداخل",
    "لداخل", "لفايت", "ع الداخل"
  )

  private val outboundKeywords = Set(
    "للخارج", "خارج", "للطالع", "لطالع", "الطالع", "طالع", "خروج", "الخارج",
    "لخارج", "لطالع", "ع الخارج"
  )

  private val spamKeywords = Set(
    "للبيع", "للإيجار", "انضموا", "انضمو", "رابط", "مجموعة", "مجموعه",
    "واتساب", "whatsapp", "صباح الخير", "مساء الخير", "للتواصل",
    "يرجى", "للمزيد", "اشترك", "subscribe"
  )

  private val generalPhrases = Set(
    "في حد", "حد عنده", "عنده خبر", "خبر عن", "شو الوضع",
    "ايش الوضع", "كيف الوضع", "وين", "شو صار", "ايش صار"
  )

  private val weakGeneralPhrases = Set(
    "كله", "كل شي", "كل مكان", "الكل"
  )

  private val statusKeywords = openKeywords ++ closedKeywords ++ busyKeywords

  private val checkpointNames = Map(
    "العروب الجنوبي" -> "arroub_south",
    "حاجز النفق" -> "tunnel_checkpoint",
    "الإسكانات نصار" -> "iskanat_nssar",
    "بوابة فوق الجسر" -> "bridge_gate",
    "دوار قدوميم" -> "qedumin_roundabout",
    "مدخل أماتين" -> "amatain_entrance",
    "النبي صالح" -> "nabi_saleh",
    "شافي شمرون" -> "shavei_shomron",
    "دير الغصون" -> "deir_al_ghasoun",
    "كفر عقب" -> "kafr_aqab",
    "عين سينيا" -> "ein_sinia",
    "بيت فوريك" -> "beit_furik",
    "عقبة حسنة" -> "aqaba_hasna",
    "عقبة حسنه" -> "aqaba_hasna",
    "بيت ايل" -> "beit_el",
    "بيت لحم" -> "bethlehem",
    "بوابة رام الله" -> "ramallah_gate",
    "رام الله" -> "ramallah",
    "دير شرف" -> "deir_sharaf",

    "حوارة" -> "huwwara",
    "النفق" -> "tunnel_checkpoint",
    "نصار" -> "iskanat_nssar",
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
    "عابود" -> "aboud",
    "العروب" -> "arroub",
    "الجسر" -> "bridge",
    "عوريتا" -> "awarta",
    "عورتا" -> "awarta",
    "المربعة" -> "al_murabba",
    "قدوميم" -> "qedumin",
    "أماتين" -> "amatain",
    "الفندق" -> "al_funduq",
    "الكونتينر" -> "container",
    "زعترة" -> "zaatara",
    "الجلمة" -> "jalama",
    "قلنديا" -> "qalandia",
    "عناب" -> "annab",
    "يتسهار" -> "yitzhar"
  )

  private val checkpointCache = mutable.Map[String, Seq[(String, String)]]()
  private val MAX_CACHE_SIZE = 1000

  def analyzeMessage(message: Message): Seq[CheckpointStatus] = {
    val text = cleanText(message.text.trim)
    val textLower = text.toLowerCase

    if (text.length < 5) {
      return Seq.empty
    }

    if (spamKeywords.exists(textLower.contains)) {
      return Seq.empty
    }

    if (generalPhrases.exists(textLower.contains)) {
      return Seq.empty
    }

    if (weakGeneralPhrases.exists(textLower.contains)) {
      val hasKnownCheckpoint = checkpointNames.keys.exists(name =>
        textLower.contains(name.toLowerCase)
      )
      if (!hasKnownCheckpoint) {
        return Seq.empty
      }
    }

    val lines = text.split("\n").map(_.trim).filter(_.nonEmpty)

    if (lines.length > 1) {
      val statusList = lines.flatMap { line =>
        analyzeSingleLineIndependent(line, message)
      }.toSeq

      return removeDuplicates(statusList)
    }

    analyzeSingleLineIndependent(text, message).toSeq
  }

  private def cleanText(text: String): String = {
    text
      .replaceAll("[\\p{C}&&[^\n\r\t]]", "")
      .trim
  }

  private def analyzeSingleLineIndependent(line: String, message: Message): Seq[CheckpointStatus] = {
    val lineLower = line.toLowerCase

    if (generalPhrases.exists(lineLower.contains)) {
      return Seq.empty
    }

    if (isHeaderLine(lineLower)) {
      return Seq.empty
    }

    val directionParts = splitByMultipleDirections(line)

    if (directionParts.length > 1) {
      return directionParts.flatMap { part =>
        processLineSegment(part, message)
      }
    }

    val conjunctionParts = splitByConjunctionSmart(line)

    if (conjunctionParts.length > 1) {
      val globalStatus = detectStatusFromEmojis(line)
        .getOrElse(detectStatusFromWordsLastOccurrence(line))
      val globalDirection = detectDirection(line)

      return conjunctionParts.flatMap { part =>
        processLineSegmentWithGlobalStatus(part, message, globalStatus, globalDirection)
      }
    }

    processLineSegment(line, message)
  }

  private def isHeaderLine(line: String): Boolean = {
    val headerKeywords = Set(
      "المنطقة الشمالية", "المنطقة الجنوبية", "منطقة رام الله",
      "المنطقة الوسطى", "الوضع الآن", "تحديث", "الساعة",
      "يرجى", "والله يسهل", "تنبيه"
    )

    val hasHeaderKeyword = headerKeywords.exists(line.contains)
    val hasCheckpoint = checkpointNames.keys.exists(name => line.contains(name.toLowerCase))
    val hasStatus = statusKeywords.exists(line.contains)
    val hasEmoji = line.contains("✅") || line.contains("❌") || line.contains("🔴")

    hasHeaderKeyword && !hasCheckpoint && !hasStatus && !hasEmoji
  }

  private def splitByMultipleDirections(line: String): Seq[String] = {
    val patterns = Seq(
      """(.*?)(للداخل|لداخل)(.*?)(للخارج|لخارج)(.*)""",
      """(.*?)(للخارج|لخارج)(.*?)(للداخل|لداخل)(.*)"""
    )

    patterns.foreach { pattern =>
      val regex = pattern.r
      line match {
        case regex(before, dir1, middle, dir2, after) =>
          val checkpoint = detectFirstCheckpointName(before + middle)
          if (checkpoint.isDefined) {
            val cp = checkpoint.get
            val part1 = s"$cp $dir1 $middle"
            val part2 = s"$cp $dir2 $after"
            return Seq(part1, part2).filter(_.trim.nonEmpty)
          }
        case _ =>
      }
    }

    Seq(line)
  }

  private def detectFirstCheckpointName(text: String): Option[String] = {
    val textLower = text.toLowerCase
    checkpointNames.toSeq.sortBy(-_._1.length).foreach { case (name, _) =>
      if (textLower.contains(name.toLowerCase)) {
        return Some(name)
      }
    }
    None
  }

  private def splitByConjunctionSmart(line: String): Seq[String] = {
    val lineLower = line.toLowerCase

    val parts = line.split("\\s+و\\s+")

    if (parts.length <= 1) {
      return Seq(line)
    }

    val validParts = parts.filter { part =>
      val partLower = part.toLowerCase
      checkpointNames.keys.exists(name => partLower.contains(name.toLowerCase))
    }

    if (validParts.length >= 2) {
      validParts.toSeq
    } else {
      Seq(line)
    }
  }

  private def processLineSegment(segment: String, message: Message): Seq[CheckpointStatus] = {
    val segmentLower = segment.toLowerCase

    val detectedCheckpoints = detectAllCheckpointsWithCache(segmentLower, segment)

    if (detectedCheckpoints.isEmpty) {
      return Seq.empty
    }

    val status = detectStatusFromEmojis(segment)
      .getOrElse(detectStatusFromWordsLastOccurrence(segment))

    if (status == "unknown" && !segment.contains("✅") && !segment.contains("❌") && !segment.contains("🔴")) {
      return Seq.empty
    }

    val direction = detectDirection(segment)
    val confidence = calculateConfidence(segment, segmentLower, status, direction)
    val timestamp = new Timestamp(System.currentTimeMillis())

    detectedCheckpoints.map { case (checkpointName, checkpointId) =>
      direction match {
        case "both" =>
          CheckpointStatus.createWithBothDirections(
            checkpointId = checkpointId,
            checkpointName = checkpointName,
            status = status,
            timestamp = timestamp,
            messageContent = segment,
            confidence = confidence
          )

        case "inbound" | "outbound" =>
          CheckpointStatus.createWithSingleDirection(
            checkpointId = checkpointId,
            checkpointName = checkpointName,
            status = status,
            direction = direction,
            timestamp = timestamp,
            messageContent = segment,
            confidence = confidence
          )
      }
    }
  }

  private def processLineSegmentWithGlobalStatus(
                                                  segment: String,
                                                  message: Message,
                                                  globalStatus: String,
                                                  globalDirection: String
                                                ): Seq[CheckpointStatus] = {
    val segmentLower = segment.toLowerCase

    val detectedCheckpoints = detectAllCheckpointsWithCache(segmentLower, segment)

    if (detectedCheckpoints.isEmpty) {
      return Seq.empty
    }

    val localStatus = detectStatusFromEmojis(segment)
      .getOrElse(detectStatusFromWordsLastOccurrence(segment))

    val localDirection = detectDirection(segment)

    val finalStatusWord = if (localStatus != "unknown") localStatus else globalStatus
    val finalDirection = if (localDirection != "both" && hasExplicitDirection(segment)) {
      localDirection
    } else {
      globalDirection
    }

    val confidence = calculateConfidence(segment, segmentLower, finalStatusWord, finalDirection)
    val timestamp = new Timestamp(System.currentTimeMillis())

    detectedCheckpoints.map { case (checkpointName, checkpointId) =>
      finalDirection match {
        case "both" =>
          CheckpointStatus.createWithBothDirections(
            checkpointId = checkpointId,
            checkpointName = checkpointName,
            status = finalStatusWord,
            timestamp = timestamp,
            messageContent = segment,
            confidence = confidence
          )

        case "inbound" | "outbound" =>
          CheckpointStatus.createWithSingleDirection(
            checkpointId = checkpointId,
            checkpointName = checkpointName,
            status = finalStatusWord,
            direction = finalDirection,
            timestamp = timestamp,
            messageContent = segment,
            confidence = confidence
          )
      }
    }
  }

  private def hasExplicitDirection(text: String): Boolean = {
    val lower = text.toLowerCase
    inboundKeywords.exists(lower.contains) || outboundKeywords.exists(lower.contains)
  }

  private def detectAllCheckpointsWithCache(textLower: String, originalText: String): Seq[(String, String)] = {
    if (checkpointCache.size > MAX_CACHE_SIZE) {
      checkpointCache.clear()
    }

    checkpointCache.getOrElseUpdate(textLower, {
      detectAllCheckpoints(textLower, originalText)
    })
  }

  private def detectAllCheckpoints(textLower: String, originalText: String): Seq[(String, String)] = {
    val checkpoints = mutable.ListBuffer[(String, String)]()
    var remainingText = textLower

    val sortedCheckpoints = checkpointNames.toSeq.sortBy(-_._1.length)

    sortedCheckpoints.foreach { case (name, id) =>
      val nameLower = name.toLowerCase
      if (remainingText.contains(nameLower) && !checkpoints.exists(_._2 == id)) {
        checkpoints += ((name, id))
        remainingText = remainingText.replace(nameLower, "***")
      }
    }

    if (checkpoints.nonEmpty) {
      return checkpoints.toSeq.distinct
    }

    val fuzzyMatch = findSimilarCheckpoint(originalText)
    if (fuzzyMatch.isDefined) {
      return Seq(fuzzyMatch.get)
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
      regex.findAllMatchIn(originalText).foreach { m =>
        val name = m.group(1).trim
        val cleanName = cleanCheckpointName(name)
        if (cleanName.nonEmpty && !checkpoints.exists(_._1 == cleanName)) {
          val id = generateCheckpointId(cleanName)
          checkpoints += ((cleanName, id))
        }
      }
    }

    if (checkpoints.isEmpty) {
      detectCheckpointByContext(originalText).foreach { checkpoint =>
        checkpoints += checkpoint
      }
    }

    checkpoints.toSeq.distinct
  }

  private def findSimilarCheckpoint(text: String): Option[(String, String)] = {
    val words = text.split("\\s+")

    words.foreach { word =>
      val cleanWord = word.replaceAll("[✅❌🔴،.!؟:]", "").trim
      if (cleanWord.length >= 3) {
        checkpointNames.foreach { case (name, id) =>
          val similarity = calculateSimilarity(cleanWord.toLowerCase, name.toLowerCase)
          if (similarity >= 0.75) {
            return Some((name, id))
          }
        }
      }
    }
    None
  }

  private def calculateSimilarity(s1: String, s2: String): Double = {
    val maxLen = math.max(s1.length, s2.length)
    if (maxLen == 0) return 1.0
    val distance = levenshteinDistance(s1, s2)
    1.0 - (distance.toDouble / maxLen)
  }

  private def levenshteinDistance(s1: String, s2: String): Int = {
    val dist = Array.tabulate(s2.length + 1, s1.length + 1) { (j, i) =>
      if (j == 0) i else if (i == 0) j else 0
    }

    for (j <- 1 to s2.length; i <- 1 to s1.length)
      dist(j)(i) = if (s1(i - 1) == s2(j - 1)) dist(j - 1)(i - 1)
      else math.min(math.min(dist(j - 1)(i) + 1, dist(j)(i - 1) + 1), dist(j - 1)(i - 1) + 1)

    dist(s2.length)(s1.length)
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

  private def isValidCheckpointName(word: String): Boolean = {
    val clean = word.replaceAll("[✅❌🔴،.!؟:]", "").trim
    val lower = clean.toLowerCase

    clean.length >= 2 &&
      !statusKeywords.contains(lower) &&
      !inboundKeywords.contains(lower) &&
      !outboundKeywords.contains(lower) &&
      !spamKeywords.contains(lower) &&
      !lower.matches("\\d+")
  }

  private def generateCheckpointId(name: String): String = {
    name.toLowerCase
      .replaceAll("\\s+", "_")
      .replaceAll("[^a-z0-9_\\u0600-\\u06FF]", "")
  }

  private def detectStatusFromEmojis(text: String): Option[String] = {
    val hasCheckmark = text.contains("✅") || text.contains("✓")
    val hasCross = text.contains("❌") || text.contains("✖")
    val hasRedCircle = text.contains("🔴")

    if (hasRedCircle) {
      Some("busy")
    } else if (hasCross) {
      Some("closed")
    } else if (hasCheckmark) {
      Some("open")
    } else {
      None
    }
  }

  private def detectStatusFromWordsLastOccurrence(text: String): String = {
    val words = text.split("\\s+")

    val textLower = text.toLowerCase
    if (textLower.contains("بدون أزمة") || textLower.contains("بدون ازمة") ||
      textLower.contains("بدون أزمه") || textLower.contains("بدون ازمه")) {
      return "open"
    }

    var lastStatus = "unknown"
    var lastIndex = -1

    words.zipWithIndex.foreach { case (word, index) =>
      val lower = word.toLowerCase
      if (openKeywords.contains(lower) && index > lastIndex) {
        lastStatus = "open"
        lastIndex = index
      } else if (closedKeywords.contains(lower) && index > lastIndex) {
        lastStatus = "closed"
        lastIndex = index
      } else if (busyKeywords.contains(lower) && index > lastIndex) {
        lastStatus = "busy"
        lastIndex = index
      }
    }

    lastStatus
  }

  private def detectDirection(text: String): String = {
    val textLower = text.toLowerCase

    val hasInbound = inboundKeywords.exists(keyword => textLower.contains(keyword))
    val hasOutbound = outboundKeywords.exists(keyword => textLower.contains(keyword))

    if (hasInbound && hasOutbound) {
      "both"
    } else if (hasInbound) {
      "inbound"
    } else if (hasOutbound) {
      "outbound"
    } else {
      "both"
    }
  }

  private def calculateConfidence(text: String, textLower: String, status: String, direction: String): Double = {
    var confidence = 0.4

    if (text.contains("✅") || text.contains("❌") || text.contains("🔴")) {
      confidence += 0.3
    }

    val words = textLower.split("\\s+").map(_.toLowerCase).toSet
    val relevantKeywords = status match {
      case "open" => openKeywords
      case "closed" => closedKeywords
      case "busy" => busyKeywords
      case _ => Set.empty[String]
    }

    val matchCount = words.intersect(relevantKeywords).size
    if (matchCount > 0) {
      confidence += 0.2
    }

    if (direction != "both") {
      confidence += 0.1
    }

    math.min(1.0, confidence)
  }

  private def removeDuplicates(statuses: Seq[CheckpointStatus]): Seq[CheckpointStatus] = {
    statuses
      .groupBy(s => (s.checkpointId, s.generalStatus, s.inboundStatus.status, s.outboundStatus.status))
      .map { case (_, group) => group.head }
      .toSeq
  }

  def testAnalyzer(): Unit = {
    val testMessages = Seq(
      "قلنديا سالك و بيت ايل مغلق",
      "حوارة سالك\nقلنديا مغلق\nزعترة أزمة",
      "حاجز النفق بدون أزمة",
      """الوضع الآن:
✅ حوارة: مفتوح
❌ قلنديا: مغلق
🔴 زعترة: أزمة كبيرة
✅ عناب: سالك للداخل""",
      "عناب وعطارة وحوارة كلهم فاتحين",
      "عناب طوابير طويلة",
      "حوارة مفتوح للداخل ومغلق للخارج",
      "بوابة رام الله مغلق",
      "يا جماعة في حد عنده خبر عن حوارة ؟",
      """تحديث شامل للحواجز - الساعة 4:00 مساءً
المنطقة الشمالية: ✅ حوارة: سالك بالاتجاهين، الوضع ممتاز ❌ زعترة: مغلق منذ ساعتين بسبب عملية أمنية 🔴 بيت ايل: أزمة كبيرة، انتظار ساعة تقريباً
المنطقة الجنوبية: ✅ العروب: مفتوح للداخل فقط ❌ الكونتينر: محسوم تماماً 🔴 النفق: زحمة خفيفة، 15 دقيقة انتظار
منطقة رام الله: ✅ قلنديا: سالك الآن بعد ما كان مغلق ✅ كفر عقب: مفتوح بالاتجاهين
يرجى الحذر والانتباه، والله يسهل على الجميع"""
    )

    println("=" * 70)
    println("Message Analyzer - Updated for New CheckpointStatus Structure")
    println("=" * 70)

    testMessages.zipWithIndex.foreach { case (text, idx) =>
      val message = Message(
        messageId = s"test_${idx + 1}",
        text = text,
        timestamp = new Timestamp(System.currentTimeMillis()),
        channelId = "test"
      )

      val results = analyzeMessage(message)

      println(s"\nTest #${idx + 1}:")
      println(s"Message: ${if (text.length > 60) text.take(60) + "..." else text}")
      if (results.isEmpty) {
        println("❌ No checkpoints detected (filtered)")
      } else {
        println(s"✅ Found ${results.size} checkpoint(s):")
        results.foreach { status =>
          val directionInfo = if (status.inboundStatus.status == status.outboundStatus.status) {
            s"${status.generalStatus}"
          } else {
            s"${status.generalStatus} (in: ${status.inboundStatus.status}, out: ${status.outboundStatus.status})"
          }
          println(s"  - ${status.checkpointName}: $directionInfo (${(status.confidence * 100).formatted("%.0f")}%)")
        }
      }
    }

    println("\n" + "=" * 70)
  }
}