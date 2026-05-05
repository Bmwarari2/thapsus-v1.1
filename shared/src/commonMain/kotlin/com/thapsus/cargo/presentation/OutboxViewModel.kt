package com.thapsus.cargo.presentation

import com.thapsus.cargo.data.local.ThapsusLocalCache
import com.thapsus.cargo.data.repository.LastMileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Drives the rider's "outbox" tab: how many mutations are pending and a manual
 * flush button. The worker also flushes automatically when connectivity returns.
 */
class OutboxViewModel(
    private val cache: ThapsusLocalCache,
    private val lastMile: LastMileRepository
) : SharedViewModel() {

    private val _pending = MutableStateFlow(0L)
    val pending: StateFlow<Long> = _pending.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _lastFlushed = MutableStateFlow<Int?>(null)
    val lastFlushed: StateFlow<Int?> = _lastFlushed.asStateFlow()

    /**
     * Snapshot of the most-recently-bumped retry's `last_error` for the
     * oldest pending row. Surfaces the network/server reason a row hasn't
     * cleared so the rider isn't staring at a silent counter.
     */
    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    init { refresh() }

    fun refresh() {
        scope.launch {
            _pending.value = cache.pendingCount()
            _lastError.value = cache.dequeueAll(limit = 1).firstOrNull()?.last_error
        }
    }

    /**
     * User-facing "Flush now" — bypass exponential backoff so the manual
     * tap always tries every queued row immediately. Background flushes
     * still honour the schedule via [LastMileRepository.flushOutbox].
     */
    fun flushNow() {
        scope.launch {
            _busy.value = true
            val sent = lastMile.flushOutboxForce()
            _lastFlushed.value = sent
            _pending.value = cache.pendingCount()
            _lastError.value = cache.dequeueAll(limit = 1).firstOrNull()?.last_error
            _busy.value = false
        }
    }
}
