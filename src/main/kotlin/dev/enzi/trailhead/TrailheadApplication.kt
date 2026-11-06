package dev.enzi.trailhead

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class TrailheadApplication

fun main(args: Array<String>) {
    runApplication<TrailheadApplication>(*args)
}
