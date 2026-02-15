package fr.bonobo.stopdemarchage.data

data class SmsMessageEntry(
    val address: String,
    val body: String,
    val date: Long,
    val isSent: Boolean
)