package br.andrew.nota.agil.infrastructure.security

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component

/**
 * Chave de teste LOCAL/HOMOLOG que dispensa o Keycloak: `nota-agil.seguranca.desativada=true` ou a
 * variavel `NOTA_AGIL_SECURITY_DISABLED=true`. Padrao desligado; nunca deve ir para properties versionado.
 * O nome e especifico da aplicacao de proposito: um `SEGURANCA_DESATIVADA` generico (relaxed binding de
 * `seguranca.desativada`) NAO liga a chave.
 *
 * Ligada:
 * - requisicoes SEM `Authorization` passam como o principal fixo [PRINCIPAL] (com WARN por requisicao,
 *   so metodo e caminho). Um Bearer enviado continua validado: token invalido segue recebendo 401;
 * - a consulta ao sap-rovema dispensa as credenciais de service account (vai sem token quando elas
 *   nao estao configuradas; ver SapRovemaTokenProvider), para o sap-rovema local com a chave dele.
 *
 * Abrange a API inteira: o Nota Agil nao tem autorizacao por rota (qualquer token valido alcanca
 * todas), entao o principal sintetico tem o mesmo alcance de um token valido, sem roles.
 *
 * Com profile de producao ([PERFIS_PRODUCAO]) ativo ou padrao (`spring.profiles.default`) a aplicacao NAO sobe.
 */
@Component
class SegurancaDesativada(
    @Value(PROPRIEDADE) val ativa: Boolean,
    environment: Environment,
) {
    init {
        if (ativa) {
            val producao = perfisProducao(environment)
            check(producao.isEmpty()) {
                "nota-agil.seguranca.desativada / NOTA_AGIL_SECURITY_DISABLED=true nao e permitido com o profile " +
                    "$producao: a chave e somente para teste local/homologacao. Remova a variavel."
            }
            log.warn("**********************************************************************************")
            log.warn("* SEGURANCA DESATIVADA (NOTA_AGIL_SECURITY_DISABLED): requisicoes sem token passam *")
            log.warn("* como '{}'. SOMENTE teste local/homologacao. NUNCA em producao.     *", PRINCIPAL)
            log.warn("**********************************************************************************")
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(SegurancaDesativada::class.java)

        /**
         * Propriedade ou, na falta dela, a variavel de ambiente; padrao desligado. Nomes especificos do
         * Nota Agil: nada generico como `seguranca.desativada` / `SEGURANCA_DESATIVADA`.
         */
        const val PROPRIEDADE = "\${nota-agil.seguranca.desativada:\${NOTA_AGIL_SECURITY_DISABLED:false}}"

        /** Principal das requisicoes aceitas sem token. */
        const val PRINCIPAL = "auth-disabled-local"

        /** Profiles (sem diferenciar maiusculas) com os quais a chave impede a subida. */
        val PERFIS_PRODUCAO = setOf("prd", "prod", "production", "producao")

        /**
         * Profiles de producao entre os ativos E os padrao (`spring.profiles.default` /
         * `SPRING_PROFILES_DEFAULT`, que valem quando nenhum e ativado). Os padrao entram mesmo com
         * profile ativo: falha a mais, nunca a menos.
         */
        fun perfisProducao(environment: Environment): List<String> =
            (environment.activeProfiles + environment.defaultProfiles)
                .filter { it.lowercase() in PERFIS_PRODUCAO }.distinct()
    }
}
