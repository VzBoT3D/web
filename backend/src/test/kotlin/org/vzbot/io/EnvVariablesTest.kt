package org.vzbot.io

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class EnvVariablesTest {
    /**
     * Monitoring is opt-in. Every other variable is mandatory — [Env] refuses to start without it —
     * and these two must not drift into that group, or an existing deployment would fail to boot
     * on upgrade just because nobody has signed up for a heartbeat provider yet.
     */
    private val optional = listOf(EnvVariables.VZ_HEARTBEAT_URL, EnvVariables.VZ_ALERT_WEBHOOK_URL)

    @Test
    fun `monitoring urls may be left unset`() {
        optional.forEach {
            // a non-null default is what makes Env skip the "not found in environment" check
            assertEquals("", it.default, "${it.name} must default to empty so it can be left unset")
        }
    }

    @Test
    fun `monitoring urls may be left empty`() {
        optional.forEach {
            assertFalse(it.requiresNonEmpty, "${it.name} must tolerate an empty value")
        }
    }
}
