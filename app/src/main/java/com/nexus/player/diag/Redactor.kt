package com.nexus.player.diag

object Redactor {
    private const val R = "[REDACTED]"
    private val headerLine = Regex(
        "(?i)\\b(authorization|proxy-authorization|cookie|set-cookie|x-api-key|api-key|swid|espn_s2)(\\s*[:=]\\s*)([^\\r\\n,;&]+)",
    )
    private val queryParam = Regex(
        "(?i)([?&](?:token|access_token|api_key|apikey|key|sig|signature|auth|password|pass)=)([^&#\\s]+)",
    )
    private val bearer = Regex("(?i)\\b(bearer|basic)\\s+[A-Za-z0-9._~+/=-]+")
    private val userInfo = Regex("(?i)(\\b[a-z][a-z0-9+.-]*://)[^/@\\s]+@")

    fun redact(s: String): String = s
        .replace(headerLine) { "${it.groupValues[1]}${it.groupValues[2]}$R" }
        .replace(bearer) { "${it.groupValues[1]} $R" }
        .replace(queryParam) { "${it.groupValues[1]}$R" }
        .replace(userInfo) { "${it.groupValues[1]}$R@" }
}
