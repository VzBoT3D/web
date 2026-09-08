package org.vzbot.health

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.request.*
import io.ktor.http.*
import org.vzbot.io.EnvVariables
import org.vzbot.io.env

/**
 * Dead man's switch.
 *
 * The bot pings an external URL only while it is fully healthy. If the pings stop the external
 * service is the one that raises the alarm, which is what makes this catch the failures nothing
 * inside this process can report: a killed JVM, a wedged container, a dead host, a lost uplink.
 *
 * The URL is provider agnostic — anything that treats an inbound GET as "still alive" works
 * (healthchecks.io, an Uptime Kuma push monitor, BetterStack, ...). Give the monitor a grace
 * period of at least 5 minutes so a short gateway reconnect does not page anyone.
 */
object Heartbeat {
    private val logger = KotlinLogging.logger { }

    private val url = env[EnvVariables.VZ_HEARTBEAT_URL]

    val enabled: Boolean = url.isNotBlank()

    suspend fun ping() {
        if (!enabled) return

        runCatching {
            val response = monitoringClient.get(url)

            if (!response.status.isSuccess()) {
                logger.warn { "Heartbeat endpoint answered ${response.status}" }
            }
        }.onFailure {
            logger.warn(it) { "Could not deliver heartbeat" }
        }
    }
}
