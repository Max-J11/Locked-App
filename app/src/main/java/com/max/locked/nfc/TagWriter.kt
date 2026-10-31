package com.max.locked.nfc

import android.content.Context
import android.nfc.FormatException
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.Tag
import android.nfc.TagLostException
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.util.Log
import java.io.IOException

sealed interface WriteResult {
    /** Written and read back identically. */
    data class Success(val tagUid: String) : WriteResult

    /** A blank tag was formatted. It needs to be held to the phone again to finish. */
    data object FormattedTapAgain : WriteResult

    data class Failure(val reason: WriteError) : WriteResult
}

enum class WriteError { NOT_SUPPORTED, READ_ONLY, TOO_SMALL, TAG_LOST, VERIFY_FAILED, IO }

/**
 * Writes a puck: a URI record `brickdiy://key/<secret>` plus an Android Application Record.
 * Never makes the tag read-only (we need to re-pair during development).
 * Blocking I/O: call off the main thread.
 */
object TagWriter {

    private const val TAG = "Locked"

    fun buildMessage(context: Context, secret: String): NdefMessage = NdefMessage(
        NdefRecord.createUri(KeyUri.build(secret)),
        NdefRecord.createApplicationRecord(context.packageName),
    )

    /**
     * The Locked key already on [tag], if any, without writing. Uses the message Android read when
     * the tag was discovered, so no extra I/O. A puck paired on another phone has one; using it as
     * is lets both phones accept the same puck.
     */
    fun readKey(tag: Tag): String? = Ndef.get(tag)?.cachedNdefMessage?.let(::keyIn)

    /** The Locked key in [message], if it has one. */
    private fun keyIn(message: NdefMessage): String? =
        message.records.firstNotNullOfOrNull { record -> record.toUri()?.toString()?.let(KeyUri::parse) }

    fun write(tag: Tag, message: NdefMessage): WriteResult {
        val uid = tag.id.toHex()
        return try {
            Ndef.get(tag)?.let { return writeNdef(it, message, uid) }
            NdefFormatable.get(tag)?.let { return format(it, message) }
            Log.d(TAG, "tag $uid has no NDEF support: ${tag.techList.joinToString()}")
            WriteResult.Failure(WriteError.NOT_SUPPORTED)
        } catch (e: TagLostException) {
            Log.d(TAG, "tag lost during write")
            WriteResult.Failure(WriteError.TAG_LOST)
        } catch (e: IOException) {
            Log.d(TAG, "tag I/O error: $e")
            WriteResult.Failure(WriteError.IO)
        } catch (e: FormatException) {
            Log.d(TAG, "tag format error: $e")
            WriteResult.Failure(WriteError.IO)
        }
    }

    private fun writeNdef(ndef: Ndef, message: NdefMessage, uid: String): WriteResult = ndef.use {
        it.connect()
        if (!it.isWritable) return WriteResult.Failure(WriteError.READ_ONLY)
        if (it.maxSize < message.byteArrayLength) {
            Log.d(TAG, "tag too small: max=${it.maxSize} need=${message.byteArrayLength}")
            return WriteResult.Failure(WriteError.TOO_SMALL)
        }
        it.writeNdefMessage(message)

        // Read back over the same connection to confirm before saving anything. Check that the key
        // reads back, rather than every byte: some tags return the message slightly re-encoded,
        // which made good writes look like failures.
        val readBack = it.ndefMessage
        val expected = keyIn(message)
        val found = readBack?.let(::keyIn)
        if (expected == null || found != expected) {
            Log.d(TAG, "read-back mismatch on tag $uid (key found=${found != null})")
            return WriteResult.Failure(WriteError.VERIFY_FAILED)
        }
        if (!readBack.toByteArray().contentEquals(message.toByteArray())) {
            Log.d(TAG, "tag $uid re-encoded the message (${readBack.byteArrayLength} vs ${message.byteArrayLength} bytes); key matches")
        }
        Log.d(TAG, "wrote and verified tag $uid (${message.byteArrayLength}/${it.maxSize} bytes)")
        WriteResult.Success(uid)
    }

    private fun format(formatable: NdefFormatable, message: NdefMessage): WriteResult = formatable.use {
        it.connect()
        it.format(message)
        Log.d(TAG, "formatted blank tag; asking for a second tap to write and verify")
        WriteResult.FormattedTapAgain
    }
}
