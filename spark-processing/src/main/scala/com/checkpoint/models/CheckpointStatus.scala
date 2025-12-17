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
