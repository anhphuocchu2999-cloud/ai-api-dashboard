package com.java.myapplication.adapter

import com.java.myapplication.config.ServiceHostMatcher
import java.util.concurrent.ConcurrentHashMap

/**
 * 进程内同 Host 请求协调器。
 *
 * Widget、设置页预览和模型检测共用同一把 Host 锁，避免多个 Widget 实例或页面同时请求
 * 同一个服务。Adapter 内部的多接口顺序仍由 Adapter 自己负责。
 */
object HostRequestCoordinator {
    private data class HostGate(
        val lock: Any = Any(),
        var lastFinishedAt: Long = 0L
    )

    private const val DEFAULT_INTERVAL_MS = 500L
    private val gates = ConcurrentHashMap<String, HostGate>()

    fun <T> withHost(
        apiBase: String,
        minimumIntervalMs: Long = DEFAULT_INTERVAL_MS,
        block: () -> T
    ): T {
        val host = ServiceHostMatcher.hostOf(apiBase) ?: return block()
        val gate = gates.getOrPut(host) { HostGate() }
        synchronized(gate.lock) {
            val waitMs = (minimumIntervalMs - (System.currentTimeMillis() - gate.lastFinishedAt))
                .coerceAtLeast(0L)
            if (gate.lastFinishedAt > 0L && waitMs > 0L) {
                try {
                    Thread.sleep(waitMs)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }
            return try {
                block()
            } finally {
                gate.lastFinishedAt = System.currentTimeMillis()
            }
        }
    }
}
