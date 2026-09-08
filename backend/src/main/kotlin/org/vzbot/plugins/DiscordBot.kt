package org.vzbot.plugins

import com.ktbot.api.KtBot
import com.ktbot.api.util.Token
import com.ktbot.api.util.scheduling.ExceptionHandler
import com.ktbot.api.util.scheduling.runAsync
import io.github.oshai.kotlinlogging.KotlinLogging
import org.vzbot.discord.App
import org.vzbot.health.AlertNotifier
import org.vzbot.io.BotLogger
import org.vzbot.io.EnvVariables
import org.vzbot.io.TeamLoader
import org.vzbot.io.env

private val logger = KotlinLogging.logger { }

fun configureBot() {
    val token = env[EnvVariables.VZ_TOKEN]
    KtBot.registerApplication(App())

    KtBot.onReady {
        BotLogger.logInfo("Bot is online!")
        TeamLoader.startScheduler()

        // makes restarts visible even when the bot recovers on its own — a stream of these is a
        // crash loop, which used to look identical to a healthy bot from the outside
        runAsync("startup-alert") { AlertNotifier.started() }
    }

    ExceptionHandler.onException { t, e ->
        logger.error(e) { "Uncaught error on thread ${t.name}" }

        // BotLogger delivers through the bot, so it is the first thing to go in an outage. Its own
        // failure must not swallow the error we were trying to report.
        runCatching {
            BotLogger.logError("There was an error with the bot in thread ${t.name}: ${e.message}")
        }.onFailure { logger.warn(it) { "Could not report the error to the Discord log channel" } }
    }

    KtBot.startBot(Token(token), {}, {
        error("Failed to start bot: ${it.reason}")
    }, loadEnv = false)
}
