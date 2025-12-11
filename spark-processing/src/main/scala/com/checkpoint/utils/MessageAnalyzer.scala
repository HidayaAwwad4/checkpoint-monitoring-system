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
}
