package org.vzbot.health

import com.ktbot.api.KtBot
import net.dv8tion.jda.api.JDA
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Answers "is this bot actually working?".
 *
 * The old `/status` route only asked whether the database had rows in it, which stayed true no
 * matter what the Discord connection was doing — so a bot with a dead gateway still looked fine.
 */
object HealthCheck {
    /** JDA states that never recover by themselves */
    private val TERMINAL_DISCORD_STATES =
        setOf(
            JDA.Status.SHUTDOWN,
            JDA.Status.SHUTTING_DOWN,
            JDA.Status.FAILED_TO_LOGIN,
        )

    private const val DB_TIMEOUT_SECONDS = 5L

    private val startedAt = System.currentTimeMillis()

    /**
     * Hikari waits 30s before giving up on a connection, which is longer than the interval we probe
     * at, so the database probe runs on its own threads and is abandoned after [DB_TIMEOUT_SECONDS].
     */
    private val dbProbePool =
        Executors.newCachedThreadPool { runnable ->
            Thread(runnable, "health-db-probe").apply { isDaemon = true }
        }

    fun run(): Health = Health(listOf(discord(), database()), uptimeSeconds())

    private fun uptimeSeconds(): Long = (System.currentTimeMillis() - startedAt) / 1000

    private fun discord(): Component {
        val jda = KtBot.bot ?: return Component("discord", HealthStatus.DOWN, "JDA was never created", terminal = true)
        val status = jda.status

        return when {
            status in TERMINAL_DISCORD_STATES ->
                Component("discord", HealthStatus.DOWN, "gateway is $status", terminal = true)

            status != JDA.Status.CONNECTED ->
                Component("discord", HealthStatus.DEGRADED, "gateway is $status")

            !KtBot.isReady() ->
                Component("discord", HealthStatus.DEGRADED, "connected, but startup never finished")

            else ->
                Component("discord", HealthStatus.UP, "connected, gateway ping ${jda.gatewayPing}ms")
        }
    }

    private fun database(): Component {
        val probe = dbProbePool.submit(Callable { transaction { exec("SELECT 1") } })

        return try {
            probe.get(DB_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            Component("database", HealthStatus.UP, "reachable")
        } catch (timeout: TimeoutException) {
            probe.cancel(true)
            Component("database", HealthStatus.DOWN, "no answer within ${DB_TIMEOUT_SECONDS}s", restartable = false)
        } catch (e: Exception) {
            val cause = e.cause ?: e
            Component(
                name = "database",
                status = HealthStatus.DOWN,
                detail = cause.message ?: cause::class.simpleName ?: "unknown error",
                restartable = false,
            )
        }
    }
}
