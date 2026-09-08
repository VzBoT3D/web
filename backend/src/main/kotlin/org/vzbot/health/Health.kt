package org.vzbot.health

import kotlinx.serialization.Serializable

const val HEALTH_PATH = "/health"
const val HEALTH_PING_PATH = "/health/ping"

enum class HealthStatus {
    /** working as expected */
    UP,

    /** not working right now, but expected to recover on its own (e.g. JDA is reconnecting) */
    DEGRADED,

    /** not working */
    DOWN,
}

/**
 * A single thing that has to work for the bot to be considered alive.
 *
 * @param terminal the component cannot recover on its own — only a process restart will fix it.
 *                 Terminal components get far less rope from the [Watchdog] before it restarts.
 * @param restartable whether restarting this process could plausibly fix the component. A down
 *                    database cannot be fixed by restarting the bot, so treating it like a dead
 *                    gateway would only produce a crash loop that outlasts the outage.
 */
data class Component(
    val name: String,
    val status: HealthStatus,
    val detail: String,
    val terminal: Boolean = false,
    val restartable: Boolean = true,
)

data class Health(
    val components: List<Component>,
    val uptimeSeconds: Long,
) {
    val status: HealthStatus = when {
        components.any { it.status == HealthStatus.DOWN } -> HealthStatus.DOWN
        components.any { it.status == HealthStatus.DEGRADED } -> HealthStatus.DEGRADED
        else -> HealthStatus.UP
    }

    val terminal: Boolean = components.any { it.terminal }

    /** true when at least one broken component is one a restart could plausibly fix */
    val restartWouldHelp: Boolean = components.any { it.status != HealthStatus.UP && it.restartable }

    /** one line naming only what is broken, for log lines */
    fun summary(): String =
        components
            .filter { it.status != HealthStatus.UP }
            .joinToString(", ") { "${it.name} is ${it.status} (${it.detail})" }
            .ifEmpty { "all components healthy" }

    /** every component, for alert bodies where the healthy ones are useful context */
    fun detailLines(): String = components.joinToString("\n") { "• **${it.name}** — ${it.status}: ${it.detail}" }
}

@Serializable
data class ComponentResponse(
    val status: String,
    val detail: String,
)

@Serializable
data class HealthResponse(
    val status: String,
    val uptimeSeconds: Long,
    val components: Map<String, ComponentResponse>,
)

fun Health.toResponse(): HealthResponse =
    HealthResponse(
        status = status.name,
        uptimeSeconds = uptimeSeconds,
        components = components.associate { it.name to ComponentResponse(it.status.name, it.detail) },
    )
