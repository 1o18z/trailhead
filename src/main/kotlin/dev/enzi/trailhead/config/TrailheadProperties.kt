package dev.enzi.trailhead.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "trailhead")
data class TrailheadProperties(
    val github: GitHub = GitHub(),
    val discovery: Discovery = Discovery(),
) {
    data class GitHub(
        val token: String = "",
        val baseUrl: String = "https://api.github.com",
    )

    data class Discovery(
        val languages: List<String> = listOf("kotlin", "java"),
        val labels: List<String> = listOf("good first issue", "help wanted"),
        val perQueryLimit: Int = 20,
        val updatedWithinDays: Long = 180,
        val reportDir: String = "./reports",
    )
}
