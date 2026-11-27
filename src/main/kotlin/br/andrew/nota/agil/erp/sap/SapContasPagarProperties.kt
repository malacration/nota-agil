package br.andrew.nota.agil.erp.sap

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import org.springframework.validation.annotation.Validated

/**
 * Acesso do Nota Agil ao sap-rovema para conferir pagamento de nota de entrada.
 *
 * Tudo vem de variavel de ambiente (relaxed binding), nada fica em properties versionado:
 * `INTEGRACAO_ERP_SAP_URL`, `INTEGRACAO_ERP_SAP_TOKENURL`, `INTEGRACAO_ERP_SAP_CLIENTID`,
 * `INTEGRACAO_ERP_SAP_CLIENTSECRET`. Sem todos preenchidos a consulta SAP fica desabilitada
 * (fora do catalogo de ERPs), que e o padrao em hmg e prd.
 *
 * Timeouts: no pior caso ha token + consulta + (401) novo token + nova consulta, cada um ate
 * connect + read. Os tetos garantem 4 x (30 + 110) s = 560 s, abaixo da reserva de 10 minutos
 * da tarefa ERP.
 */
@Component
@Validated
@ConfigurationProperties(prefix = "integracao.erp.sap")
class SapContasPagarProperties {
    /** URL base do sap-rovema, ex.: https://sap-rovema.exemplo.invalid */
    var url: String = ""
    /** Endpoint de token do realm Keycloak aceito pelo sap-rovema (client_credentials). */
    var tokenUrl: String = ""
    var clientId: String = ""
    var clientSecret: String = ""
    @field:Min(1) @field:Max(30) var connectTimeoutSeconds: Int = 10
    @field:Min(5) @field:Max(110) var readTimeoutSeconds: Int = 90

    val configurada: Boolean
        get() = listOf(url, tokenUrl, clientId, clientSecret).all { it.isNotBlank() }
}
