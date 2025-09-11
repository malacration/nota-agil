package br.andrew.nota.agil.qive.infrastructure

import jakarta.validation.constraints.NotBlank
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

@Validated
@ConfigurationProperties(prefix = "qive.api")
data class QiveConfiguration(
    @field:NotBlank val url: String,
    @field:NotBlank val id: String,
    @field:NotBlank val key: String,
    val permission: String? = null
)