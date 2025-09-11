package br.andrew.nota.agil.softexpert.infrastructure

import jakarta.validation.constraints.NotBlank
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

@Validated
@ConfigurationProperties(prefix = "softexpert.api")
data class SoftExpertConfiguration(
    @field:NotBlank val url: String,
    @field:NotBlank val authorization: String
)