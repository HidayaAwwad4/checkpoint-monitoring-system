package com.checkpoint.models

import java.sql.Timestamp

case class DirectionStatus(
                            status: String,           // open, closed, busy
                            lastUpdated: Timestamp,
                            isRecent: Boolean = true
                          )

case class CheckpointStatus(
                             checkpointId: String,
                             checkpointName: String,
                             generalStatus: String,    // open, closed, busy, mixed, partial
                             inboundStatus: DirectionStatus,
                             outboundStatus: DirectionStatus,
                             location: Option[String] = None,
                             lastUpdated: Timestamp,
                             messageContent: String,
                             confidence: Double = 1.0
                           )

object CheckpointStatus {

  def fromJson(json: Map[String, Any]): CheckpointStatus = {
    val now = new Timestamp(System.currentTimeMillis())

    CheckpointStatus(
      checkpointId = json.getOrElse("checkpoint_id", "unknown").toString,
      checkpointName = json.getOrElse("checkpoint_name", "unknown").toString,
      generalStatus = json.getOrElse("general_status", "unknown").toString,
      inboundStatus = DirectionStatus("unknown", now, false),
      outboundStatus = DirectionStatus("unknown", now, false),
      location = json.get("location").map(_.toString),
      lastUpdated = now,
      messageContent = json.getOrElse("message", "").toString,
      confidence = json.getOrElse("confidence", 1.0).toString.toDouble
    )
  }

  def toJson(status: CheckpointStatus): String = {
    s"""
    {
      "checkpoint_id": "${status.checkpointId}",
      "checkpoint_name": "${status.checkpointName}",
      "general_status": "${status.generalStatus}",
      "inbound": {
        "status": "${status.inboundStatus.status}",
        "last_updated": "${status.inboundStatus.lastUpdated}",
        "is_recent": ${status.inboundStatus.isRecent}
      },
      "outbound": {
        "status": "${status.outboundStatus.status}",
        "last_updated": "${status.outboundStatus.lastUpdated}",
        "is_recent": ${status.outboundStatus.isRecent}
      },
      "location": "${status.location.getOrElse("")}",
      "last_updated": "${status.lastUpdated}",
      "message_content": "${status.messageContent}",
      "confidence": ${status.confidence}
    }
    """
  }

  def createWithBothDirections(
                                checkpointId: String,
                                checkpointName: String,
                                status: String,
                                timestamp: Timestamp,
                                messageContent: String,
                                confidence: Double
                              ): CheckpointStatus = {
    CheckpointStatus(
      checkpointId = checkpointId,
      checkpointName = checkpointName,
      generalStatus = status,
      inboundStatus = DirectionStatus(status, timestamp, isRecent = true),
      outboundStatus = DirectionStatus(status, timestamp, isRecent = true),
      location = None,
      lastUpdated = timestamp,
      messageContent = messageContent,
      confidence = confidence
    )
  }

  def createWithSingleDirection(
                                 checkpointId: String,
                                 checkpointName: String,
                                 status: String,
                                 direction: String,  // "inbound" or "outbound"
                                 timestamp: Timestamp,
                                 messageContent: String,
                                 confidence: Double,
                                 otherDirectionStatus: Option[DirectionStatus] = None
                               ): CheckpointStatus = {
    val (inbound, outbound) = direction match {
      case "inbound" =>
        (
          DirectionStatus(status, timestamp, isRecent = true),
          otherDirectionStatus.getOrElse(DirectionStatus("unknown", timestamp, isRecent = false))
        )
      case "outbound" =>
        (
          otherDirectionStatus.getOrElse(DirectionStatus("unknown", timestamp, isRecent = false)),
          DirectionStatus(status, timestamp, isRecent = true)
        )
      case _ =>
        (
          DirectionStatus(status, timestamp, isRecent = true),
          DirectionStatus(status, timestamp, isRecent = true)
        )
    }

    val general = if (inbound.status == outbound.status) {
      inbound.status
    } else if (inbound.status == "unknown" || outbound.status == "unknown") {
      "partial"
    } else {
      "mixed"
    }

    CheckpointStatus(
      checkpointId = checkpointId,
      checkpointName = checkpointName,
      generalStatus = general,
      inboundStatus = inbound,
      outboundStatus = outbound,
      location = None,
      lastUpdated = timestamp,
      messageContent = messageContent,
      confidence = confidence
    )
  }

  def mergeWithExisting(
                         existing: CheckpointStatus,
                         newStatus: String,
                         newDirection: String,
                         timestamp: Timestamp,
                         messageContent: String,
                         confidence: Double
                       ): CheckpointStatus = {

    val oneHourAgo = new Timestamp(System.currentTimeMillis() - 3600000) // ساعة

    val (newInbound, newOutbound) = newDirection match {
      case "inbound" =>
        (
          DirectionStatus(newStatus, timestamp, isRecent = true),
          existing.outboundStatus.copy(isRecent = existing.outboundStatus.lastUpdated.after(oneHourAgo))
        )
      case "outbound" =>
        (
          existing.inboundStatus.copy(isRecent = existing.inboundStatus.lastUpdated.after(oneHourAgo)),
          DirectionStatus(newStatus, timestamp, isRecent = true)
        )
      case _ => // both
        (
          DirectionStatus(newStatus, timestamp, isRecent = true),
          DirectionStatus(newStatus, timestamp, isRecent = true)
        )
    }

    val newGeneral = if (newInbound.status == newOutbound.status) {
      newInbound.status
    } else if (newInbound.status == "unknown" || newOutbound.status == "unknown") {
      "partial"
    } else {
      "mixed"
    }

    CheckpointStatus(
      checkpointId = existing.checkpointId,
      checkpointName = existing.checkpointName,
      generalStatus = newGeneral,
      inboundStatus = newInbound,
      outboundStatus = newOutbound,
      location = existing.location,
      lastUpdated = timestamp,
      messageContent = messageContent,
      confidence = confidence
    )
  }
}