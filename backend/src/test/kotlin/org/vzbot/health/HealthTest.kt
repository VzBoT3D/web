package org.vzbot.health

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HealthTest {
    private fun health(vararg components: Component) = Health(components.toList(), uptimeSeconds = 0)

    private fun up(name: String) = Component(name, HealthStatus.UP, "fine")

    @Test
    fun `is UP only when every component is UP`() {
        assertEquals(HealthStatus.UP, health(up("discord"), up("database")).status)
    }

    @Test
    fun `a degraded component degrades the whole bot`() {
        val reconnecting = Component("discord", HealthStatus.DEGRADED, "gateway is RECONNECT_QUEUED")

        assertEquals(HealthStatus.DEGRADED, health(reconnecting, up("database")).status)
    }

    @Test
    fun `down outranks degraded`() {
        val reconnecting = Component("discord", HealthStatus.DEGRADED, "gateway is RECONNECT_QUEUED")
        val unreachable = Component("database", HealthStatus.DOWN, "no answer within 5s")

        assertEquals(HealthStatus.DOWN, health(reconnecting, unreachable).status)
    }

    @Test
    fun `a terminal component marks the whole report terminal`() {
        val shutDown = Component("discord", HealthStatus.DOWN, "gateway is SHUTDOWN", terminal = true)

        assertTrue(health(shutDown, up("database")).terminal)
        assertFalse(health(up("discord"), up("database")).terminal)
    }

    @Test
    fun `restarting does not help when only the database is down`() {
        val unreachable = Component("database", HealthStatus.DOWN, "no answer within 5s", restartable = false)

        assertFalse(health(up("discord"), unreachable).restartWouldHelp)
    }

    @Test
    fun `restarting helps when a restartable component is broken`() {
        val unreachable = Component("database", HealthStatus.DOWN, "no answer within 5s", restartable = false)
        val reconnecting = Component("discord", HealthStatus.DEGRADED, "gateway is RECONNECT_QUEUED")

        assertTrue(health(reconnecting, unreachable).restartWouldHelp)
    }

    @Test
    fun `a healthy bot never asks to be restarted`() {
        assertFalse(health(up("discord"), up("database")).restartWouldHelp)
    }

    @Test
    fun `summary names only what is broken`() {
        val unreachable = Component("database", HealthStatus.DOWN, "no answer within 5s")

        assertEquals("database is DOWN (no answer within 5s)", health(up("discord"), unreachable).summary())
        assertEquals("all components healthy", health(up("discord")).summary())
    }

    @Test
    fun `the response keeps every component, healthy ones included`() {
        val unreachable = Component("database", HealthStatus.DOWN, "no answer within 5s")
        val response = health(up("discord"), unreachable).toResponse()

        assertEquals("DOWN", response.status)
        assertEquals("UP", response.components.getValue("discord").status)
        assertEquals("no answer within 5s", response.components.getValue("database").detail)
    }
}
