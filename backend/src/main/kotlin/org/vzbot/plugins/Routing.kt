package org.vzbot.plugins

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.cache.*
import io.ktor.client.plugins.cache.storage.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import org.vzbot.plugins.routing.*
import java.nio.file.Files
import java.nio.file.Paths

val geoClient = HttpClient(CIO) {
    expectSuccess = true
    install(HttpTimeout) {
        requestTimeoutMillis = 120_000
    }
    install(HttpCache) {
        val cacheFile = Files.createDirectories(Paths.get("cache")).toFile()
        publicStorage(FileStorage(cacheFile))
    }
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }

}


fun Application.configureRouting() {
    routing {
        default()
        health()
        serials()
        printers()
        blogs()
        authors()
        teamRouting()
    }
}
