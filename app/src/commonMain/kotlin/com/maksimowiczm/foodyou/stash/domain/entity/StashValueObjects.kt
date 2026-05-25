package com.maksimowiczm.foodyou.stash.domain.entity

import kotlin.jvm.JvmInline

@JvmInline value class StashDefinitionId(val value: Long)

@JvmInline value class StashEntryId(val value: Long)

@JvmInline value class StashMovementId(val value: Long)

@JvmInline value class LinkedDiaryEntryId(val value: Long)

@JvmInline
value class StashOwnerId(val value: String) {
    companion object {
        val Local = StashOwnerId("local-offline-owner")
    }
}

@JvmInline value class StashName(val value: String) {
    companion object {
        fun from(value: String): StashName {
            val trimmed = value.trim()
            require(trimmed.isNotEmpty()) { "Stash name must not be blank" }
            return StashName(trimmed)
        }
    }
}
