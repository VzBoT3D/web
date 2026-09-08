package org.vzbot.health

import com.ktbot.api.KtBot
import com.ktbot.api.util.scheduling.runAsync
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.vzbot.SERVER_PORT
import kotlin.system.exitProcess
import kotlin.time.Duration.Companion.seconds

/**
 * Watches the bot's own health and does something about it.
 *
 * Docker already ran a healthcheck against this container, but Docker never acts on the result — an
 * unhealthy container just sits there wearing a label nobody reads, and `restart: unless-stopped`
 * only fires when the process actually exits. A JDA gateway can die without the JVM noticing, which
 * is why the bot appeared to "slowly die" with nothing restarting it and nobody being told.
 *
 * So this loop closes both gaps: it exits the process once the bot is unrecoverable (letting the
 * restart policy do its job) and it reports outwards, both by alerting a Discord webhook and by
 * withholding the [Heartbeat] ping.
 */
object Watchdog {
    private val logger = KotlinLogging.logger { }

    /** how often health is evaluated */
    private val INTERVAL = 30.seconds

    /** long enough that a normal, slow startup is never mistaken for a dead bot */
    private val STARTUP_GRACE = 90.seconds

    /** consecutive bad checks before the team is told — rides out brief gateway reconnects */
    private const val ALERT_AFTER = 2

    /** consecutive bad checks before the process restarts itself (~3 minutes) */
    private const val RESTART_AFTER = 6

    /** a component that cannot recover on its own gets far less rope */
    private const val RESTART_AFTER_TERMINAL = 2

    private const val EXIT_CODE_UNHEALTHY = 1

    private var badChecks = 0
    private var alerted = false

    fun start() {
        logger.info {
            "Health watchdog starting: checking every ${INTERVAL.inWholeSeconds}s after a " +
                "${STARTUP_GRACE.inWholeSeconds}s grace period " +
                "(heartbeat ${enabledLabel(Heartbeat.enabled)}, alert webhook ${enabledLabel(AlertNotifier.enabled)})"
        }

        runAsync("health-watchdog") {
            delay(STARTUP_GRACE)

            while (isActive) {
                tick()
                delay(INTERVAL)
            }
        }
    }

    private suspend fun tick() {
        val health = currentHealth()

        if (health.status == HealthStatus.UP) {
            if (alerted) {
                logger.info { "Bot is healthy again after $badChecks failed checks" }
                AlertNotifier.recovered(health)
            }

            badChecks = 0
            alerted = false
            Heartbeat.ping()
            return
        }

        badChecks++
        logger.warn { "Health check failed ($badChecks in a row): ${health.summary()}" }

        if (!alerted && (badChecks >= ALERT_AFTER || health.terminal)) {
            AlertNotifier.unhealthy(health, badChecks)
            alerted = true
        }

        val restartAfter = if (health.terminal) RESTART_AFTER_TERMINAL else RESTART_AFTER
        if (badChecks < restartAfter) return

        // Nothing a restart can fix — a down database outlives this process, so exiting would only
        // crash-loop the container for the length of the outage. Alerts and the withheld heartbeat
        // still carry the news; recovery is someone else's job.
        if (!health.restartWouldHelp) {
            logger.warn { "Not restarting: ${health.summary()} cannot be fixed by restarting the bot" }
            return
        }

        restart(health)
    }

    /**
     * [HealthCheck] deliberately knows nothing about HTTP, but a wedged web server is its own
     * outage — the site goes blank while the bot itself is fine — and the watchdog is the only
     * thing positioned to notice, since it is the one caller that is not already an HTTP request.
     */
    private suspend fun currentHealth(): Health {
        val base = withContext(Dispatchers.IO) { HealthCheck.run() }
        return Health(base.components + api(), base.uptimeSeconds)
    }

    private suspend fun api(): Component {
        val response =
            runCatching {
                monitoringClient.get("http://127.0.0.1:$SERVER_PORT$HEALTH_PING_PATH")
            }.getOrElse {
                return Component("api", HealthStatus.DOWN, "web server did not answer: ${it.message}")
            }

        return if (response.status.isSuccess()) {
            Component("api", HealthStatus.UP, "web server answering")
        } else {
            Component("api", HealthStatus.DOWN, "web server answered ${response.status}")
        }
    }

    private suspend fun restart(health: Health): Nothing {
        val downFor = badChecks * INTERVAL.inWholeSeconds

        logger.error { "Unhealthy for ${downFor}s, exiting so the container restarts: ${health.summary()}" }
        AlertNotifier.restarting(health)

        // best effort — a wedged gateway may not shut down cleanly, which is why we exit regardless
        runCatching { KtBot.bot?.shutdownNow() }

        exitProcess(EXIT_CODE_UNHEALTHY)
    }

    private fun enabledLabel(enabled: Boolean) = if (enabled) "enabled" else "not configured"
}
