package com.checkpoint.models

import java.sql.Timestamp

case class CheckpointStatus(
                             checkpointId: String,
                             checkpointName: String,
                             status: String,
                             location: Option[String] = None,
                             lastUpdated: Timestamp,
                             messageContent: String,
                             confidence: Double = 1.0
                           )

object CheckpointStatus {

  def fromJson(json: Map[String, Any]): CheckpointStatus = {
    CheckpointStatus(
      checkpointId = json.getOrElse("checkpoint_id", "unknown").toString,
      checkpointName = json.getOrElse("checkpoint_name", "unknown").toString,
      status = json.getOrElse("status", "unknown").toString,
      location = json.get("location").map(_.toString),
      lastUpdated = new Timestamp(System.currentTimeMillis()),
      messageContent = json.getOrElse("message", "").toString,
      confidence = json.getOrElse("confidence", 1.0).toString.toDouble
    )
  }

  def toJson(status: CheckpointStatus): String = {
    s"""
    {
      "checkpoint_id": "${status.checkpointId}",
      "checkpoint_name": "${status.checkpointName}",
      "status": "${status.status}",
      "location": "${status.location.getOrElse("")}",
      "last_updated": "${status.lastUpdated}",
      "message_content": "${status.messageContent}",
      "confidence": ${status.confidence}
    }
    """
  }
}
