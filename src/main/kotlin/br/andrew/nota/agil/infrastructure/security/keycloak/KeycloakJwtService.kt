package br.andrew.nota.agil.infrastructure.security.keycloak

import io.jsonwebtoken.Claims
import io.jsonwebtoken.JwsHeader
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.Locator
import org.springframework.stereotype.Service
import java.security.Key

@Service
class KeycloakJwtService(
    private val properties: KeycloakProperties,
    private val jwks: KeycloakJwksProvider,
) {
    fun validar(token: String): Claims {
        val locator = Locator<Key> { header ->
            val signed = header as? JwsHeader ?: throw JwtException("Token deve ser assinado")
            if (signed.algorithm != "RS256") throw JwtException("Algoritmo do token nao permitido")
            jwks.chave(signed.keyId ?: throw JwtException("Token sem kid"))
        }
        val claims = Jwts.parser().keyLocator(locator)
            .sig().clear().add(Jwts.SIG.RS256).and()
            .requireIssuer(properties.issuer()).require("typ", "Bearer")
            .clockSkewSeconds(30).build().parseSignedClaims(token).payload
        if (claims.expiration == null || claims.subject.isNullOrBlank()) throw JwtException("Access token incompleto")
        if (claims["azp"] !in properties.clientesPermitidos()) throw JwtException("Client do token nao permitido")
        return claims
    }
}
