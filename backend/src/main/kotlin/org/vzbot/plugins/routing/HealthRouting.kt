package org.vzbot.plugins.routing

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.vzbot.health.HEALTH_PATH
import org.vzbot.health.HEALTH_PING_PATH
import org.vzbot.health.HealthCheck
import org.vzbot.health.HealthStatus
import org.vzbot.health.toResponse

/**
 * Both routes are exempt from the API token middleware (see
 * [org.vzbot.middleware.APITokenMiddleware]). Checking the token is itself a database query, so a
 * token-gated healthcheck cannot tell "the database is down" apart from "the token is wrong", and
 * it fails outright whenever the tokens table is empty.
 */
fun Route.health() {
    get(HEALTH_PATH) {
        val health = withContext(Dispatchers.IO) { HealthCheck.run() }

        // DEGRADED stays a 200: JDA reconnects by itself, and flapping the container healthcheck
        // over a few seconds of gateway churn would take the frontend down with it.
        val code = if (health.status == HealthStatus.DOWN) HttpStatusCode.ServiceUnavailable else HttpStatusCode.OK

        call.respond(code, health.toResponse())
    }

    // cheapest possible liveness probe: proves the web server is still accepting connections
    get(HEALTH_PING_PATH) {
        call.respondText("pong")
    }
}
