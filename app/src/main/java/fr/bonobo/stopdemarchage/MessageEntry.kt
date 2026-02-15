package fr.bonobo.stopdemarchage

data class MessageEntry(
    val content: String,
    val isSent: Boolean, // true = envoyé, false = reçu
    val timestamp: Long
)
