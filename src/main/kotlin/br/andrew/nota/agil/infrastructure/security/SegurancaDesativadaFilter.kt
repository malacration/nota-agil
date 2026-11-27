package br.andrew.nota.agil.infrastructure.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter

/**
 * So existe na cadeia com [SegurancaDesativada.ativa] (teste local/homolog). Roda depois do filtro
 * do Keycloak: requisicao sem `Authorization` e sem autenticacao vira o principal fixo
 * [SegurancaDesativada.PRINCIPAL], com perfil admin. Requisicao com `Authorization` nao e tocada (o filtro
 * do Keycloak ja autenticou ou ja respondeu 401).
 */
class SegurancaDesativadaFilter : OncePerRequestFilter() {

    override fun shouldNotFilter(request: HttpServletRequest): Boolean = request.method == "OPTIONS"

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
        if (SecurityContextHolder.getContext().authentication == null && request.getHeader(HttpHeaders.AUTHORIZATION) == null) {
            val context = SecurityContextHolder.createEmptyContext()
            context.authentication = UsernamePasswordAuthenticationToken(SegurancaDesativada.PRINCIPAL, null,
                listOf(org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_${Perfis.ADMIN}")))
            SecurityContextHolder.setContext(context)
            // Somente metodo e caminho (sem query string nem corpo); controle de linha neutralizado.
            log.warn("SEGURANCA DESATIVADA: {} {} aceita sem autenticacao como '{}'",
                request.method, request.requestURI.replace(CONTROLE, "_"), SegurancaDesativada.PRINCIPAL)
        }
        filterChain.doFilter(request, response)
    }

    private companion object {
        val log = LoggerFactory.getLogger(SegurancaDesativadaFilter::class.java)
        val CONTROLE = Regex("[\\r\\n\\t]")
    }
}
