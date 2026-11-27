package br.andrew.nota.agil.infrastructure.security

import org.springframework.security.core.Authentication

/**
 * Perfis do Nota Agil (roles do Keycloak, no client `nota-agil` ou no realm):
 * - `admin`: acesso a tudo;
 * - `operador`: so a tela Notas ERP, e so nas filiais que o SAP/Dealer liberam para ele;
 * - service account de integracao (azp em `keycloak.allowed-client-ids`): so as rotas `/integracoes/...`.
 * Usuario do front sem nenhum dos perfis recebe 403.
 */
enum class Perfil { ADMIN, OPERADOR, INTEGRACAO }

object Perfis {
    const val ADMIN = "admin"
    const val OPERADOR = "operador"
    const val INTEGRACAO = "INTEGRACAO"
    const val ROLE_INTEGRACAO = "ROLE_$INTEGRACAO"

    fun de(auth: Authentication?): Perfil? {
        val roles = auth?.authorities?.map { it.authority }?.toSet() ?: return null
        return when {
            // Integracao primeiro: nunca vale como admin/operador, mesmo se vier com essas roles.
            ROLE_INTEGRACAO in roles -> Perfil.INTEGRACAO
            "ROLE_$ADMIN" in roles -> Perfil.ADMIN
            "ROLE_$OPERADOR" in roles -> Perfil.OPERADOR
            else -> null
        }
    }
}
