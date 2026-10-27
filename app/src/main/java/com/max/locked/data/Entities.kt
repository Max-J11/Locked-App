package com.max.locked.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "mode")
data class ModeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val blockNotifications: Boolean = false,
)

@Entity(
    tableName = "mode_app",
    primaryKeys = ["modeId", "packageName"],
    foreignKeys = [
        ForeignKey(
            entity = ModeEntity::class,
            parentColumns = ["id"],
            childColumns = ["modeId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("modeId")],
)
data class ModeAppEntity(
    val modeId: Long,
    val packageName: String,
)

/**
 * A paired puck. Matched on tag UID AND the SHA-256 of the secret written to it.
 * The secret itself is never stored on the phone.
 */
@Entity(
    tableName = "paired_key",
    indices = [Index(value = ["tagUid"], unique = true)],
)
data class PairedKeyEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val tagUid: String,
    val secretHash: String,
    val modeId: Long,
    val createdAt: Long,
)

/** Single row (id = 0) holding the current lock. All columns from CLAUDE.md, some unused until later. */
@Entity(tableName = "brick_state")
data class BrickStateEntity(
    @PrimaryKey val id: Int = SINGLE_ROW_ID,
    val isBricked: Boolean = false,
    val activeModeId: Long? = null,
    val startedAt: Long? = null,
    val endsAt: Long? = null,
    val source: String? = null,
    val cooldownEndsAt: Long? = null,
    val passPackage: String? = null,
    val passEndsAt: Long? = null,
    val passesUsed: Int = 0,
    /** When the locked stretch that [passesUsed] counts began. */
    val passSessionStart: Long? = null,
) {
    companion object {
        const val SINGLE_ROW_ID = 0
    }
}

/** A mode plus how many apps it blocks, for list screens. Not a table. */
data class ModeSummary(
    val id: Long,
    val name: String,
    val blockNotifications: Boolean,
    val appCount: Int,
)

/**
 * A repeating lock window. [days] is a bitmask, Monday = bit 0 ... Sunday = bit 6, of the days a
 * window starts on. Minutes are after midnight. Not tied to [ModeEntity] by a foreign key:
 * deleting a mode moves its schedules to Default instead.
 */
@Entity(tableName = "schedule", indices = [Index("modeId")])
data class ScheduleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val modeId: Long,
    val days: Int,
    val startMinute: Int,
    val endMinute: Int,
    val enabled: Boolean = true,
    /** A puck ended the window that ends at this time. */
    val skippedUntil: Long? = null,
)
