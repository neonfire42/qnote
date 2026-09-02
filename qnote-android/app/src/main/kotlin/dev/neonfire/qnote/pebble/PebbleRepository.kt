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
import kotlinx.coroutines.delay
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

    /**
     * Whether the user has already granted a specific Pebble app access.
     * Suspending because the picker reads it from DataStore.
     */
    suspend fun hasSelectedPebbleApp(): Boolean = appPicker.getCurrentlySelectedApp() != null

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

    /**
     * Opens qnote on the watch and asks it to start dictating.
     *
     * Two steps, because the watch cannot infer intent from `launch_reason()`:
     * that would also fire for someone opening the app from the Pebble app's
     * locker, and nobody wants a microphone they did not ask for. The explicit
     * START_CAPTURE message makes the request unambiguous.
     *
     * The message can only land once the watchapp is running and has opened its
     * AppMessage inbox, and there is no event for that, so this retries briefly
     * rather than firing once into a gap.
     */
    fun startCaptureOnWatch(onResult: (Boolean) -> Unit = {}) {
        scope.launch {
            val launched = runCatching { sender.startAppOnTheWatch(QNotePebble.APP_UUID) }
                .onFailure { Log.w(TAG, "startAppOnTheWatch failed", it) }
                .getOrNull()

            if (launched?.values?.any { it is TransmissionResult.Success } != true) {
                onResult(false)
                return@launch
            }

            val data = mapOf(QNotePebble.KEY_START_CAPTURE to PebbleDictionaryItem.UInt8(1))
            repeat(CAPTURE_ATTEMPTS) { attempt ->
                delay(CAPTURE_RETRY_MS)
                val results = runCatching {
                    sender.sendDataToPebble(QNotePebble.APP_UUID, data)
                }.onFailure { Log.w(TAG, "start capture failed", it) }.getOrNull()

                if (results?.values?.any { it is TransmissionResult.Success } == true) {
                    onResult(true)
                    return@launch
                }
                Log.i(TAG, "start capture attempt ${attempt + 1} did not land")
            }
            // The app is open on the watch either way, so the user can still
            // press Select. Report the failure so the UI can say so.
            onResult(false)
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

        /** Roughly how long the watchapp takes to open its AppMessage inbox. */
        const val CAPTURE_RETRY_MS = 500L
        const val CAPTURE_ATTEMPTS = 4
    }
}
