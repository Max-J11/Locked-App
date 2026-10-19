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
) {
    companion object {
        const val SINGLE_ROW_ID = 0
    }
}
