package org.vzbot.io

import com.ktbot.api.util.EnvType

enum class EnvVariables(
    val type: EnvType,
    val default: String? = null,
    val requiresNonEmpty: Boolean = true,
    val isHidden: Boolean = true,
) {
    VZ_TOKEN(EnvType.STRING),
    VZ_LOG_CHANNEL(EnvType.LONG),
    VZ_ADMIN_ROLE(EnvType.STRING),
    VZ_MODERATOR_ROLE(EnvType.STRING),
    VZ_CONTRIBUTOR_ROLE(EnvType.STRING),
    VZ_SERIAL_CATEGORY(EnvType.LONG),
    VZ_TEAM_ROLE(EnvType.LONG),
    VZ_SERIAL_ANNOUNCEMENT_CHANNEL(EnvType.LONG),

    VZ_OWNERS_ROLE(EnvType.LONG),

    VZ_SERIAL_BASE_PLATE_LOCATION(EnvType.STRING),
    VZ_SERIAL_NUMBER_PLATES_LOCATION(EnvType.STRING),

    VZ_WEBSITE_URL(EnvType.STRING),

    /** dead man's switch pinged while the bot is healthy — see [org.vzbot.health.Heartbeat] */
    VZ_HEARTBEAT_URL(EnvType.STRING, default = "", requiresNonEmpty = false),

    /** Discord webhook for outage alerts — see [org.vzbot.health.AlertNotifier] */
    VZ_ALERT_WEBHOOK_URL(EnvType.STRING, default = "", requiresNonEmpty = false),

    VZ_DB_USER(EnvType.STRING),
    VZ_DB_PASSWORD(EnvType.STRING, requiresNonEmpty = true),
    VZ_DB_HOST(EnvType.STRING),
    VZ_DB_DATABASE(EnvType.STRING),
    VZ_DB_PORT(EnvType.NUMBER),
}