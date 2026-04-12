package com.maksimowiczm.foodyou.stash.domain.usecase

data class ManualStashAction(
    val reason: String,
    val note: String? = null,
) {
    init {
        require(reason.isNotBlank()) { "Manual stash action reason must not be blank." }
    }

    private val normalizedReason = reason.trim()
    private val normalizedNote = note?.trim()?.takeIf(String::isNotEmpty)

    fun toMovementNote(): String =
        normalizedNote?.let { "$normalizedReason\n$it" } ?: normalizedReason
}
