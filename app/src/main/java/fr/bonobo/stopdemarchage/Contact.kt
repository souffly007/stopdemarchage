package fr.bonobo.stopdemarchage

data class Contact(
    val id: Long = 0,
    val name: String? = null,
    var phoneNumber: String? = null,
    val email: String? = null
)