package br.andrew.nota.agil.infrastructure.security

import org.springframework.beans.factory.annotation.Value
import org.springframework.security.config.Customizer;
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configurers.CorsConfigurer
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.UrlBasedCorsConfigurationSource


@Configuration
class CorsConfig(@Value("\${cors.origins:http://localhost:4200}") val corsAppendAllow : List<String> = arrayListOf()) {

    val allowedOrigins = mutableListOf(
        "http://localhost:[*]",
        "http://localhost:4200/",
        "http://localhost:4200",
        "http://*localhost:[*]",
        "http://172.18.30.147:4200/*",
        "http://172.18.30.147:4200",
        "http://172.18.30.147:4200/"
    ).also {
        it.addAll(corsAppendAllow)
    }

    val allowedMethods = mutableListOf("HEAD", "GET", "POST", "PUT", "DELETE", "PATCH")

    val allowedHeaders = mutableListOf("Authorization",
        "Cache-Control",
        "Content-Type",
        "cache",
        "pragma",
        "traceparent",
        "tracestate")

    val exposedHeaders = mutableListOf(
        "Authorization",
        "error",
        "arquivo",
        "info",
        "cache",
        "Content-Type")

    fun getCorsConfig(): CorsConfiguration {
        val configuration = CorsConfiguration()
        configuration.allowedOrigins = allowedOrigins
        configuration.allowedMethods = allowedMethods
        configuration.allowedHeaders = allowedHeaders
        configuration.exposedHeaders = exposedHeaders
        configuration.allowCredentials = true
        return configuration
    }

    val customizer: Customizer<CorsConfigurer<HttpSecurity>> =
        Customizer { cors ->
            val source = UrlBasedCorsConfigurationSource().apply {
                registerCorsConfiguration("/**", getCorsConfig())
            }
            cors.configurationSource(source)
        }
}