package br.andrew.nota.agil.infrastructure.security

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.web.DefaultSecurityFilterChain


@Configuration
@EnableWebSecurity
class SecurityWebConf {

    @Bean
    fun authorizationRequestRepository(http : HttpSecurity): DefaultSecurityFilterChain {
        return http
            .csrf {
                it.disable()
            }
            .cors(CorsConfig().customizer)
            .authorizeHttpRequests {
                it.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                it.anyRequest().permitAll()
            }.build()
    }
}