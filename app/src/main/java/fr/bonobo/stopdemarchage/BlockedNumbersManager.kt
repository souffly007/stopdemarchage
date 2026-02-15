package fr.bonobo.stopdemarchage

object BlockedNumbersManager {

    private val blockedNumbers = mutableSetOf<String>()

    fun isBlocked(number: String): Boolean {
        return blockedNumbers.contains(number) || number.isEmpty()
    }

    fun addBlocked(number: String) {
        blockedNumbers.add(number)
    }

    fun removeBlocked(number: String) {
        blockedNumbers.remove(number)
    }

    fun getAllBlocked(): Set<String> = blockedNumbers
}
