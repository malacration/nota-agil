package br.andrew.nota.agil.erp

import br.andrew.nota.agil.model.erp.Erp
import jakarta.validation.constraints.Min
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import org.springframework.validation.annotation.Validated
import java.time.Duration

@Component
@Validated
@ConfigurationProperties(prefix = "integracao.erp")
class ConsultaErpConfiguration {
    var atividadeId: String = ""
    @field:Min(1) var acaoSucesso: Int = 1
    @field:Min(1) var acaoFalha: Int = 2
    var cnpjs: Map<String, Erp> = emptyMap()
    // Chave logica do resultado -> identificador do campo no formulario do SE.
    var camposRetorno: Map<String, String> = emptyMap()

    /** Reconsulta de resultados ainda nao finais (parcela em aberto, nota ainda nao lancada...). */
    var esperaPagamento: EsperaPagamento = EsperaPagamento()

    /** Novas tentativas apos falha tecnica (ERRO_CONSULTA_ERP: rede, timeout, HTTP 5xx...). */
    var falhaTecnica: FalhaTecnica = FalhaTecnica()

    /** Novas tentativas da entrega ao SE (campos, comentario, executeActivity) recusada/com erro. */
    var retornoSe: RetornoSe = RetornoSe()

    class EsperaPagamento {
        /**
         * Prazo maximo de espera, contado do aceite da solicitacao (POST). Gravado na tarefa no
         * aceite: mudar depois nao altera tarefas ja aceitas. Zero desliga a espera.
         */
        var prazo: Duration = Duration.ofHours(96)

        /** Intervalo entre reconsultas. A ultima reconsulta acontece no fim do prazo. */
        var intervalo: Duration = Duration.ofHours(3)

        /** Codigos que ainda podem mudar no ERP e por isso aguardam reconsulta. */
        var codigos: Set<String> = CODIGOS_ESPERA_PADRAO
    }

    class FalhaTecnica {
        /** Novas tentativas apos a primeira falha tecnica seguida. */
        var tentativas: Int = 3

        /** Espera antes da primeira nova tentativa; as seguintes multiplicam por [multiplicador]. */
        var intervaloInicial: Duration = Duration.ofMinutes(15)
        var multiplicador: Int = 4

        /** Janela total das novas tentativas, contada da primeira falha seguida. Maximo de 24 h. */
        var janelaMaxima: Duration = Duration.ofHours(24)
    }

    /**
     * Entrega ao SE: intervalo `intervalo-inicial * multiplicador^(n-1)`, limitado a
     * `intervalo-maximo`; para quando passaria de `janela-maxima` (contada da primeira falha
     * seguida) ou de `tentativas` novas tentativas. Esgotou -> `RETORNO_FALHOU` + task `FAILED`.
     */
    class RetornoSe {
        var intervaloInicial: Duration = Duration.ofMinutes(1)
        var multiplicador: Int = 4
        var intervaloMaximo: Duration = Duration.ofHours(6)
        var tentativas: Int = 10
        var janelaMaxima: Duration = Duration.ofHours(24)
    }

    /** Falha de configuracao vira erro na subida, nao comportamento silencioso no fluxo financeiro. */
    fun validarReconsulta() {
        val espera = esperaPagamento
        require(!espera.prazo.isNegative && espera.prazo <= Duration.ofDays(30)) {
            "integracao.erp.espera-pagamento.prazo deve estar entre 0 e 30 dias"
        }
        require(espera.intervalo >= Duration.ofMinutes(1) && espera.intervalo <= Duration.ofDays(1)) {
            "integracao.erp.espera-pagamento.intervalo deve estar entre 1 minuto e 1 dia"
        }
        require(CODIGO_FALHA_TECNICA !in espera.codigos) {
            "$CODIGO_FALHA_TECNICA segue integracao.erp.falha-tecnica, nao a espera de pagamento"
        }
        val tecnica = falhaTecnica
        require(tecnica.tentativas in 0..10) { "integracao.erp.falha-tecnica.tentativas deve estar entre 0 e 10" }
        require(tecnica.intervaloInicial >= Duration.ofMinutes(1) && tecnica.intervaloInicial <= JANELA_TECNICA_MAXIMA) {
            "integracao.erp.falha-tecnica.intervalo-inicial deve estar entre 1 minuto e 24 horas"
        }
        require(tecnica.multiplicador in 1..10) { "integracao.erp.falha-tecnica.multiplicador deve estar entre 1 e 10" }
        require(tecnica.janelaMaxima > Duration.ZERO && tecnica.janelaMaxima <= JANELA_TECNICA_MAXIMA) {
            "integracao.erp.falha-tecnica.janela-maxima deve estar entre 1 segundo e 24 horas"
        }
        val retorno = retornoSe
        require(retorno.intervaloInicial >= Duration.ofMinutes(1) && retorno.intervaloInicial <= Duration.ofDays(1)) {
            "integracao.erp.retorno-se.intervalo-inicial deve estar entre 1 minuto e 1 dia"
        }
        require(retorno.multiplicador in 1..10) { "integracao.erp.retorno-se.multiplicador deve estar entre 1 e 10" }
        require(retorno.intervaloMaximo >= retorno.intervaloInicial && retorno.intervaloMaximo <= Duration.ofDays(1)) {
            "integracao.erp.retorno-se.intervalo-maximo deve estar entre o intervalo-inicial e 1 dia"
        }
        require(retorno.tentativas in 0..100) { "integracao.erp.retorno-se.tentativas deve estar entre 0 e 100" }
        require(retorno.janelaMaxima >= Duration.ofMinutes(1) && retorno.janelaMaxima <= Duration.ofDays(7)) {
            "integracao.erp.retorno-se.janela-maxima deve estar entre 1 minuto e 7 dias"
        }
    }

    companion object {
        /** Codigo de falha tecnica gerado pelos clients e pelo proprio servico. */
        const val CODIGO_FALHA_TECNICA = "ERRO_CONSULTA_ERP"
        val JANELA_TECNICA_MAXIMA: Duration = Duration.ofHours(24)

        /**
         * Aguardam: parcela ainda nao paga (EM_ABERTO, PAGO_PARCIAL) e nota que pode ainda nao ter
         * sido lancada no SAP (NOTA_NAO_ENCONTRADA, e FORNECEDOR_NAO_ENCONTRADO, porque o fornecedor
         * novo costuma ser cadastrado junto com o lancamento da nota). Os demais sao finais.
         */
        val CODIGOS_ESPERA_PADRAO = setOf("EM_ABERTO", "PAGO_PARCIAL", "NOTA_NAO_ENCONTRADA", "FORNECEDOR_NAO_ENCONTRADO")
    }
}
