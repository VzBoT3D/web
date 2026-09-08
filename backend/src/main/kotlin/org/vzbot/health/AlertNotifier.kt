package org.vzbot.health

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.vzbot.io.EnvVariables
import org.vzbot.io.env

/**
 * Posts alerts straight to a Discord webhook over plain HTTPS.
 *
 * This deliberately does not go through JDA. [org.vzbot.io.BotLogger] needs a working bot to
 * deliver its message, so it goes silent in exactly the situation we want to hear about. A webhook
 * URL keeps working while the gateway is dead.
 */
object AlertNotifier {
    private val logger = KotlinLogging.logger { }
    private val json = Json { encodeDefaults = true }

    private const val RED = 0xE74C3C
    private const val GREEN = 0x2ECC71
    private const val ORANGE = 0xE67E22

    private val webhookUrl = env[EnvVariables.VZ_ALERT_WEBHOOK_URL]

    val enabled: Boolean = webhookUrl.isNotBlank()

    suspend fun unhealthy(
        health: Health,
        failedChecks: Int,
    ) = send(
        title = "Bot is unhealthy",
        description = "Failed $failedChecks health ${"check".pluralize(failedChecks)} in a row.\n\n${health.detailLines()}",
        color = RED,
    )

    suspend fun recovered(health: Health) =
        send(
            title = "Bot recovered",
            description = "Everything is answering again.\n\n${health.detailLines()}",
            color = GREEN,
        )

    suspend fun restarting(health: Health) =
        send(
            title = "Bot is restarting itself",
            description = "Still unhealthy after repeated checks, so the process is exiting to let " +
                "Docker restart it.\n\n${health.detailLines()}",
            color = ORANGE,
        )

    suspend fun started() =
        send(
            title = "Bot is online",
            description = "Startup finished and the gateway is connected.",
            color = GREEN,
        )

    private suspend fun send(
        title: String,
        description: String,
        color: Int,
    ) {
        if (!enabled) return

        val payload = WebhookPayload(embeds = listOf(WebhookEmbed(title, description, color)))

        runCatching {
            val response =
                monitoringClient.post(webhookUrl) {
                    contentType(ContentType.Application.Json)
                    setBody(json.encodeToString(WebhookPayload.serializer(), payload))
                }

            if (!response.status.isSuccess()) {
                logger.warn { "Alert webhook answered ${response.status} for \"$title\"" }
            }
        }.onFailure {
            // never let a broken alert channel take down the watchdog that is using it
            logger.warn(it) { "Could not deliver alert \"$title\"" }
        }
    }

    private fun String.pluralize(count: Int) = if (count == 1) this else "${this}s"

    @Serializable
    private data class WebhookEmbed(
        val title: String,
        val description: String,
        val color: Int,
    )

    @Serializable
    private data class WebhookPayload(
        val username: String = "VZBot Monitoring",
        val embeds: List<WebhookEmbed>,
    )
}
