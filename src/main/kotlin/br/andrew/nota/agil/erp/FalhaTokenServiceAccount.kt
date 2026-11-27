package br.andrew.nota.agil.erp

/**
 * O Keycloak recusou o client_credentials da service account (client id/secret errado, client
 * desativado ou sem service account) ou nao respondeu. Separado do 401/403 do servico chamado
 * para a mensagem apontar a causa certa. Nunca carrega o token nem o segredo.
 */
class FalhaTokenServiceAccount(val status: Int?, cause: Throwable? = null) : RuntimeException(
    "Keycloak recusou a service account" + (status?.let { " (HTTP $it)" } ?: ""), cause,
) {
    companion object {
        const val MENSAGEM = "Keycloak recusou a service account do Nota Agil: confira client id e secret " +
            "(INTEGRACAO_ERP_SAP_CLIENTID / INTEGRACAO_ERP_SAP_CLIENTSECRET)"
    }
}
