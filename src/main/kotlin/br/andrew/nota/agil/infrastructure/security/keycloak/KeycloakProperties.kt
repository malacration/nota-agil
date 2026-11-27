package br.andrew.nota.agil.infrastructure.security.keycloak

import jakarta.validation.constraints.NotBlank
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import org.springframework.validation.annotation.Validated
import java.time.Duration

@Component
@Validated
@ConfigurationProperties(prefix = "keycloak")
class KeycloakProperties {
    @field:NotBlank var url: String = "https://sso.gruporovema.com.br"
    @field:NotBlank var realm: String = "rovema"
    @field:NotBlank var clientId: String = "nota-agil"
    /** Clients adicionais para integracoes servidor-servidor, quando cadastrados no SSO. */
    var allowedClientIds: Set<String> = emptySet()
    /**
     * Intervalo minimo entre consultas ao JWKS (sucesso ou falha). Um `kid` desconhecido
     * dentro desse intervalo e recusado sem chamar o SSO, impedindo que tokens forjados
     * forcem recargas. Uma rotacao real de chave e aceita no maximo apos esse intervalo.
     */
    var jwksRefreshMinInterval: Duration = Duration.ofSeconds(30)

    fun issuer(): String = "${url.trimEnd('/')}/realms/$realm"
    fun jwksUri(): String = "${issuer()}/protocol/openid-connect/certs"
    fun clientesPermitidos(): Set<String> = allowedClientIds + clientId
}
