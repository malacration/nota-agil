package br.andrew.nota.agil.infrastructure.security

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import org.springframework.web.filter.CorsFilter

@Configuration
class CorsConfig(
    @Value("\${cors.origins:http://localhost:4200}") private val corsOrigins: List<String> = emptyList()
) {

    private val allowedOriginPatterns = buildList {
        add("*")
        addAll(corsOrigins.filter { it.isNotBlank() })
    }

    private val allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
    private val allowedHeaders = listOf("*")
    private val exposedHeaders = listOf(
        "Authorization",
        "error",
        "arquivo",
        "info",
        "cache",
        "Content-Type"
    )

    private fun newCorsConfiguration(): CorsConfiguration =
        CorsConfiguration().apply {
            allowedOriginPatterns = this@CorsConfig.allowedOriginPatterns
            allowedMethods = this@CorsConfig.allowedMethods
            allowedHeaders = this@CorsConfig.allowedHeaders
            exposedHeaders = this@CorsConfig.exposedHeaders
            allowCredentials = true
        }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource =
        UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration("/**", newCorsConfiguration())
        }

    @Bean
    fun corsFilterBean(
        @Qualifier("corsConfigurationSource") source: CorsConfigurationSource
    ): FilterRegistrationBean<CorsFilter> =
        FilterRegistrationBean(CorsFilter(source)).apply {
            order = Ordered.HIGHEST_PRECEDENCE
        }
}
