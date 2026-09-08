package org.vzbot.middleware

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.vzbot.health.HEALTH_PATH
import org.vzbot.health.HEALTH_PING_PATH
import org.vzbot.models.APIToken
import org.vzbot.models.APITokens
import statix.org.Middleware
import statix.org.MiddlewareData

class APITokenMiddleware: Middleware {

    /**
     * Health routes must answer without a token. Validating one is itself a database query, so a
     * gated healthcheck cannot tell "database is down" apart from "token is wrong" — and it fails
     * outright while the tokens table is empty. They expose nothing beyond up/down per component.
     */
    private val publicPaths = setOf(HEALTH_PATH, HEALTH_PING_PATH)

    override suspend fun handleCall(call: ApplicationCall, receives: MiddlewareData?): MiddlewareData {
        if (call.request.path() in publicPaths) return MiddlewareData.empty()

        val auth = call.request.header("token") ?: run {
            call.respondText("This route requires authorization", status = HttpStatusCode.Forbidden)
            return MiddlewareData.empty()
        }

        val isAPITokenValid = transaction {
            APIToken.find { APITokens.token eq auth }.firstOrNull() != null
        }

        if (!isAPITokenValid) {
            call.respondText("This route requires authorization", status = HttpStatusCode.Forbidden)
            return MiddlewareData.empty()
        }

        return MiddlewareData.empty()
    }
}