package br.andrew.nota.agil.infrastructure

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "jobs")
data class JobsConfiguration(
    var disableAll: Boolean = false
)
