package br.andrew.nota.agil.infrastructure.security

import br.andrew.nota.agil.infrastructure.security.keycloak.KeycloakAuthenticationFilter
import br.andrew.nota.agil.infrastructure.security.keycloak.KeycloakJwtService
import br.andrew.nota.agil.infrastructure.security.keycloak.KeycloakProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.web.DefaultSecurityFilterChain
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfigurationSource


@Configuration
@EnableWebSecurity
class SecurityWebConf(
    private val corsConfigurationSource: CorsConfigurationSource,
    private val keycloak: KeycloakProperties,
    private val tokens: KeycloakJwtService,
    /** Opcional (nullable = nao obrigatorio): sem o bean, ou com ele desligado, nada muda. Ver [SegurancaDesativada]. */
    private val segurancaDesativada: SegurancaDesativada?,
) {

    @Bean
    fun authorizationRequestRepository(http : HttpSecurity): DefaultSecurityFilterChain {
        val entryPoint = AuthenticationEntryPoint { _, response, _ ->
            response.status = 401
            response.contentType = "application/json"
            response.characterEncoding = "UTF-8"
            response.setHeader("WWW-Authenticate", "Bearer")
            response.writer.write("""{"mensagem":"Autenticacao Keycloak obrigatoria ou token invalido"}""")
        }
        return http
            .csrf {
                it.disable()
            }
            .cors { it.configurationSource(corsConfigurationSource) }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .exceptionHandling {
                it.authenticationEntryPoint(entryPoint)
                it.accessDeniedHandler { _, response, _ ->
                    response.status = 403
                    response.contentType = "application/json"
                    response.characterEncoding = "UTF-8"
                    response.writer.write("""{"mensagem":"Seu perfil no Nota Agil nao permite este acesso (perfis: admin, operador)"}""")
                }
            }
            .addFilterBefore(KeycloakAuthenticationFilter(tokens, keycloak, entryPoint), UsernamePasswordAuthenticationFilter::class.java)
            .apply {
                // Teste local/homolog: depois do Keycloak, requisicao sem token vira principal fixo.
                if (segurancaDesativada?.ativa == true)
                    addFilterAfter(SegurancaDesativadaFilter(), KeycloakAuthenticationFilter::class.java)
            }
            .authorizeHttpRequests {
                it.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                it.requestMatchers("/auth/config", "/error").permitAll()
                // Perfis: ver Perfis.kt. Integracao (SoftExpert) so nas rotas de integracao.
                it.requestMatchers("/integracoes/**").hasAnyRole(Perfis.INTEGRACAO, Perfis.ADMIN)
                it.requestMatchers("/me", "/me/**", "/erp/notas-entrada/**").hasAnyRole(Perfis.ADMIN, Perfis.OPERADOR)
                it.anyRequest().hasRole(Perfis.ADMIN)
            }.build()
    }

    /**
     * Sem este bean o Spring Boot cria um usuario em memoria e registra a senha gerada
     * no log ("Using generated security password"). A API so aceita tokens do Keycloak,
     * entao nenhum usuario local deve existir.
     */
    @Bean
    fun semUsuariosLocais(): UserDetailsService = UserDetailsService { _ ->
        throw UsernameNotFoundException("Autenticacao local desabilitada; use o Keycloak")
    }
}
