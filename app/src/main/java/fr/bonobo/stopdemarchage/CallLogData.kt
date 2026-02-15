package fr.bonobo.stopdemarchage

import android.provider.CallLog

data class CallStats(
    val receivedCount: Int = 0,
    val outgoingCount: Int = 0,
    val missedCount: Int = 0,
    val blockedCount: Int = 0
)