package fr.bonobo.stopdemarchage.data.models

data class BlockedCall(
    val number: String,
    val type: String,
    val time: String,
    val duration: String,
    val status: String,
    val category: String,
    val reports: Int
)