package dev.neonfire.qnote.pebble

import android.content.Context
import android.util.Log
import io.rebble.pebblekit2.client.DefaultPebbleAndroidAppPicker
import io.rebble.pebblekit2.client.DefaultPebbleSender
import io.rebble.pebblekit2.common.model.PebbleDictionaryItem
import io.rebble.pebblekit2.common.model.TransmissionResult
import io.rebble.pebblekit2.common.model.WatchIdentifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Everything qnote sends toward the watch.
 *
 * One sender is kept for the process lifetime: it holds a binding to the Pebble
 * app, and binding per message would churn a connection that exists to keep the
 * companion awake.
 */
class PebbleRepository(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sender by lazy { DefaultPebbleSender(appContext) }

    /**
     * Auto-select is off by default here. It would otherwise talk to any app
     * claiming to be a Pebble app, and notes are personal text, so the user
     * picks once through [io.rebble.pebblekit2.client.ui] instead.
     */
    val appPicker by lazy {
        DefaultPebbleAndroidAppPicker.getInstance(appContext).apply { enableAutoSelect = false }
    }

    fun hasSelectedPebbleApp(): Boolean = appPicker.getCurrentlySelectedApp() != null

    /** Tells the watch a note is safely stored, so it may drop it from its cache. */
    fun acknowledge(recordId: Long, watch: WatchIdentifier? = null) = send(
        mapOf(QNotePebble.KEY_ACK_ID to PebbleDictionaryItem.UInt32(recordId)),
        watch,
        "ack $recordId",
    )

    /** Removes a note from the watch's cache after it was deleted here. */
    fun deleteOnWatch(recordId: Long, watch: WatchIdentifier? = null) = send(
        mapOf(QNotePebble.KEY_DELETE_ID to PebbleDictionaryItem.UInt32(recordId)),
        watch,
        "delete $recordId",
    )

    /** Asks the watch to resend anything it still holds unacknowledged. */
    fun requestSync(watch: WatchIdentifier? = null) = send(
        mapOf(QNotePebble.KEY_SYNC_REQUEST to PebbleDictionaryItem.UInt8(1)),
        watch,
        "sync request",
    )

    /** Opens qnote on the watch so the user can start dictating from the phone. */
    fun openOnWatch(onResult: (Boolean) -> Unit = {}) {
        scope.launch {
            val results = runCatching { sender.startAppOnTheWatch(QNotePebble.APP_UUID) }
                .onFailure { Log.w(TAG, "startAppOnTheWatch failed", it) }
                .getOrNull()
            onResult(results?.values?.any { it is TransmissionResult.Success } == true)
        }
    }

    private fun send(
        data: Map<UInt, PebbleDictionaryItem>,
        watch: WatchIdentifier?,
        label: String,
    ) {
        scope.launch {
            val results = runCatching {
                sender.sendDataToPebble(
                    QNotePebble.APP_UUID,
                    data,
                    watch?.let { listOf(it) },
                )
            }.onFailure { Log.w(TAG, "$label failed", it) }.getOrNull()

            if (results == null) {
                // Null means the Pebble app is unreachable, not that the watch
                // refused. Nothing to retry against, so just record it.
                Log.i(TAG, "$label: no Pebble app reachable")
                return@launch
            }
            results.forEach { (target, result) ->
                if (result !is TransmissionResult.Success) {
                    Log.i(TAG, "$label to ${target.value}: $result")
                }
            }
        }
    }

    private companion object {
        const val TAG = "QNotePebble"
    }
}
