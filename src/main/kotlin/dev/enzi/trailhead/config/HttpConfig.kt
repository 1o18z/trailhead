package dev.enzi.trailhead.config

import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.web.client.RestClient

@Configuration
class HttpConfig(private val props: TrailheadProperties) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Bean
    fun githubRestClient(): RestClient {
        val token = props.github.token.trim()
        if (token.isEmpty()) {
            log.warn("GITHUB_TOKEN 이 비어 있습니다. 인증 없이 호출하면 rate limit(60/h)에 금방 막힙니다.")
        }
        val builder = RestClient.builder()
            .baseUrl(props.github.baseUrl)
            .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
            .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
            .defaultHeader(HttpHeaders.USER_AGENT, "trailhead-oss-scout")
        if (token.isNotEmpty()) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer $token")
        }
        return builder.build()
    }
}
