package br.andrew.nota.agil.infrastructure.security.keycloak

import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.IncorrectClaimException
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.MissingClaimException
import io.jsonwebtoken.security.SignatureException
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.web.filter.OncePerRequestFilter
import br.andrew.nota.agil.infrastructure.security.Perfis

class KeycloakAuthenticationFilter(
    private val service: KeycloakJwtService,
    private val properties: KeycloakProperties,
    private val entryPoint: AuthenticationEntryPoint,
) : OncePerRequestFilter() {
    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        request.method == "OPTIONS" || request.requestURI.removePrefix(request.contextPath) in setOf("/auth/config", "/error")

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
        val authorization = request.getHeader(HttpHeaders.AUTHORIZATION)
        if (authorization == null) {
            filterChain.doFilter(request, response)
            return
        }
        try {
            if (!authorization.startsWith("Bearer ", ignoreCase = true)) throw BadCredentialsException("Bearer obrigatorio")
            val claims = service.validar(authorization.substring(7).trim())
            val realmAccess = claims["realm_access"] as? Map<*, *>
            val resourceAccess = claims["resource_access"] as? Map<*, *>
            val clientAccess = resourceAccess?.get(properties.clientId) as? Map<*, *>
            val roles = ((realmAccess?.get("roles") as? List<*>) ?: emptyList<Any>()) +
                ((clientAccess?.get("roles") as? List<*>) ?: emptyList<Any>())
            // Service account de integracao (azp em allowed-client-ids, nao o client do front): SO o perfil
            // INTEGRACAO, que so alcanca /integracoes/** (ver Perfis). As roles do token (admin/operador no
            // realm ou no client) sao descartadas: integracao nunca herda acesso de usuario humano.
            val integracao = claims["azp"] != properties.clientId
            val authorities = if (integracao) listOf(SimpleGrantedAuthority(Perfis.ROLE_INTEGRACAO))
                else roles.filterIsInstance<String>().distinct().map { SimpleGrantedAuthority("ROLE_$it") }
            val login = (claims["preferred_username"] as? String)?.takeIf { it.isNotBlank() } ?: claims.subject
            val context = SecurityContextHolder.createEmptyContext()
            // O token do usuario fica como credencial so para repassar ao sap-rovema/dealerlenium na
            // consulta das filiais do proprio usuario (FiliaisUsuarioService). Nunca e logado.
            context.authentication = UsernamePasswordAuthenticationToken(login, authorization.substring(7).trim(), authorities)
            SecurityContextHolder.setContext(context)
        } catch (e: Exception) {
            SecurityContextHolder.clearContext()
            log.warn("Bearer recusado em {} {}: {}", request.method, request.requestURI, motivo(e))
            entryPoint.commence(request, response, BadCredentialsException("Access token invalido ou expirado"))
            return
        }
        filterChain.doFilter(request, response)
    }

    companion object {
        private val log = LoggerFactory.getLogger(KeycloakAuthenticationFilter::class.java)

        /**
         * Motivo da recusa para diagnostico, sem o token nem valores de claims.
         * Mensagens livres so sao usadas quando vem das validacoes deste pacote
         * (JwtException exata), que tem textos fixos.
         */
        internal fun motivo(e: Exception): String = when (e) {
            is ExpiredJwtException -> "token expirado"
            is IncorrectClaimException -> "claim '${e.claimName}' com valor diferente do esperado"
            is MissingClaimException -> "claim '${e.claimName}' ausente"
            is SignatureException -> "assinatura invalida"
            is BadCredentialsException -> "cabecalho Authorization sem esquema Bearer"
            else -> if (e.javaClass == JwtException::class.java) e.message ?: "JwtException" else e.javaClass.simpleName
        }
    }
}
