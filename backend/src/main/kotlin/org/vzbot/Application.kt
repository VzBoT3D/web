package org.vzbot

import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import org.vzbot.health.Watchdog
import org.vzbot.plugins.*

const val SERVER_PORT = 8080

fun main() {
    embeddedServer(CIO, port = SERVER_PORT, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    configureHTTP()
    configureMonitoring()
    configureSerialization()
    configureDatabases()
    configureMiddlewares()
    configureRouting()
    configureBot()

    // deliberately outside of KtBot.onReady: a bot that never connects at all is the failure we
    // most need to hear about, and a watchdog started from the ready callback would never run
    Watchdog.start()
}
