/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package mozilla.telemetry.glean.private

import androidx.annotation.VisibleForTesting
import android.os.SystemClock
import com.sun.jna.StringArray
import mozilla.telemetry.glean.Glean
import mozilla.telemetry.glean.rust.LibGleanFFI
import mozilla.telemetry.glean.rust.toByte
import mozilla.telemetry.glean.rust.RustError

import mozilla.telemetry.glean.Dispatchers
import mozilla.telemetry.glean.rust.toBoolean

/**
 * This implements the developer facing API for recording timespans.
 *
 * Instances of this class type are automatically generated by the parsers at build time,
 * allowing developers to record values that were previously registered in the metrics.yaml file.
 *
 * The timespans API exposes the [start], [stop] and [cancel] methods.
 */
class TimespanMetricType internal constructor(
    private var handle: Long,
    private val sendInPings: List<String>
) {
    /**
     * The public constructor used by automatically generated metrics.
     */
    constructor(
        disabled: Boolean,
        category: String,
        lifetime: Lifetime,
        name: String,
        sendInPings: List<String>,
        timeUnit: TimeUnit = TimeUnit.Minute
    ) : this(handle = 0, sendInPings = sendInPings) {
        val ffiPingsList = StringArray(sendInPings.toTypedArray(), "utf-8")
        this.handle = LibGleanFFI.INSTANCE.glean_new_timespan_metric(
            category = category,
            name = name,
            send_in_pings = ffiPingsList,
            send_in_pings_len = sendInPings.size,
            lifetime = lifetime.ordinal,
            disabled = disabled.toByte(),
            time_unit = timeUnit.ordinal
        )
    }

    protected fun finalize() {
        if (this.handle != 0L) {
            val error = RustError.ByReference()
            LibGleanFFI.INSTANCE.glean_destroy_timespan_metric(this.handle, error)
        }
    }

    private fun shouldRecord(): Boolean {
        // Don't record metrics if we aren't initialized
        if (!Glean.isInitialized()) {
            return false
        }

        return LibGleanFFI.INSTANCE.glean_timespan_should_record(Glean.handle, this.handle).toBoolean()
    }

    /**
     * Start tracking time for the provided metric.
     * This records an error if it’s already tracking time (i.e. start was already
     * called with no corresponding [stop]): in that case the original
     * start time will be preserved.
     */
    fun start() {
        if (!shouldRecord()) {
            return
        }

        val startTime = SystemClock.elapsedRealtimeNanos()

        @Suppress("EXPERIMENTAL_API_USAGE")
        Dispatchers.API.launch {
            LibGleanFFI.INSTANCE.glean_timespan_set_start(Glean.handle, this@TimespanMetricType.handle, startTime)
        }
    }

    /**
     * Stop tracking time for the provided metric.
     * Sets the metric to the elapsed time.
     * This will record an error if no [start] was called.
     */
    fun stop() {
        if (!shouldRecord()) {
            return
        }

        val stopTime = SystemClock.elapsedRealtimeNanos()

        @Suppress("EXPERIMENTAL_API_USAGE")
        Dispatchers.API.launch {
            LibGleanFFI.INSTANCE.glean_timespan_set_stop(Glean.handle, this@TimespanMetricType.handle, stopTime)
        }
    }

    /**
     * Abort a previous [start] call. No error is recorded if no [start] was called.
     */
    fun cancel() {
        if (!shouldRecord()) {
            return
        }

        @Suppress("EXPERIMENTAL_API_USAGE")
        Dispatchers.API.launch {
            LibGleanFFI.INSTANCE.glean_timespan_cancel(this@TimespanMetricType.handle)
        }
    }

    /**
     * Explicitly set the timespan value, in nanoseconds.
     *
     * This API should only be used if your library or application requires recording
     * times in a way that can not make use of [start]/[stop]/[cancel].
     *
     * [setRawNanos] does not overwrite a running timer or an already existing value.
     *
     * @param elapsedNanos The elapsed time to record, in nanoseconds.
     */
    fun setRawNanos(elapsedNanos: Long) {
        if (!shouldRecord()) {
            return
        }

        @Suppress("EXPERIMENTAL_API_USAGE")
        Dispatchers.API.launch {
            LibGleanFFI.INSTANCE.glean_timespan_set_raw_nanos(
                Glean.handle,
                this@TimespanMetricType.handle,
                elapsedNanos)
        }
    }

    /**
     * Tests whether a value is stored for the metric for testing purposes only
     *
     * @param pingName represents the name of the ping to retrieve the metric for.  Defaults
     *                 to the either the first value in [defaultStorageDestinations] or the first
     *                 value in [sendInPings]
     * @return true if metric value exists, otherwise false
     */
    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    fun testHasValue(pingName: String = sendInPings.first()): Boolean {
        @Suppress("EXPERIMENTAL_API_USAGE")
        Dispatchers.API.assertInTestingMode()

        return LibGleanFFI
            .INSTANCE.glean_timespan_test_has_value(Glean.handle, this.handle, pingName)
            .toBoolean()
    }

    /**
     * Returns the stored value for testing purposes only
     *
     * @param pingName represents the name of the ping to retrieve the metric for.  Defaults
     *                 to the either the first value in [defaultStorageDestinations] or the first
     *                 value in [sendInPings]
     * @return value of the stored metric
     * @throws [NullPointerException] if no value is stored
     */
    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    fun testGetValue(pingName: String = sendInPings.first()): Long {
        @Suppress("EXPERIMENTAL_API_USAGE")
        Dispatchers.API.assertInTestingMode()

        if (!testHasValue(pingName)) {
            throw NullPointerException()
        }
        return LibGleanFFI.INSTANCE.glean_timespan_test_get_value(Glean.handle, this.handle, pingName)
    }
}
