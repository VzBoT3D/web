package org.vzbot.health

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*

/**
 * Shared client for everything that reports the bot's health outwards.
 *
 * Timeouts are short on purpose: a monitoring call that hangs would stall the [Watchdog] loop that
 * is supposed to be noticing the outage.
 */
internal val monitoringClient =
    HttpClient(CIO) {
        expectSuccess = false
        install(HttpTimeout) {
            requestTimeoutMillis = 10_000
            connectTimeoutMillis = 5_000
            socketTimeoutMillis = 10_000
        }
    }
