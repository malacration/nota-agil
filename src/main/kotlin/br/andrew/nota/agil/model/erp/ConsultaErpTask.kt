package br.andrew.nota.agil.model.erp

import br.andrew.nota.agil.model.TipoDuplicata
import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Digits
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.math.BigDecimal
import java.time.Instant

private const val CNPJ_PATTERN = "(?:[0-9]{14}|[0-9]{2}\\.[0-9]{3}\\.[0-9]{3}/[0-9]{4}-[0-9]{2})"

data class RegistrarConsultaErpRequest(
    @field:NotBlank @field:Size(max = 100)
    val workflowId: String,
    @field:NotBlank @field:Size(max = 100)
    val solicitacaoId: String,
    @field:Pattern(regexp = CNPJ_PATTERN)
    @JsonProperty("cpfcnpjtomador") @JsonAlias("cpfCnpjTomador", "cnpjTomador")
    val cpfCnpjTomador: String,
    @field:Pattern(regexp = CNPJ_PATTERN)
    @JsonProperty("cnpjprestador") @JsonAlias("cpfCnpjPrestador", "cnpjFornecedor")
    val cnpjPrestador: String,
    @field:NotBlank @field:Size(max = 100)
    @JsonProperty("numdocfiscal") @JsonAlias("numeroNota")
    val numDocFiscal: String,
    @field:DecimalMin("0.01") @field:Digits(integer = 15, fraction = 2)
    @JsonProperty("valorparcela") @JsonAlias("valorParcela", "valorliquido")
    val valorParcela: BigDecimal,
    @field:Size(max = 30)
    @JsonProperty("seriedocfiscal") @JsonAlias("serieNota")
    val serieDocFiscal: String? = null,
    @JsonProperty("tipodoc") @JsonAlias("tipoNota")
    val tipoDoc: TipoDuplicata? = null,
    @field:Size(max = 30)
    @JsonProperty("numeroparcela") @JsonAlias("numeroParcela")
    val numeroParcela: String? = null,
    @field:Min(1)
    val activityOrder: Int? = null,
    /**
     * Chave de acesso da nota (44 NFe/CTe; 50 NFS-e nacional, com ou sem "NFS"). Opcional: o
     * sap-rovema procura primeiro por ela (OPCH.U_ChaveAcesso) e confere com numero/serie/tipo.
     */
    @field:Size(max = 60)
    @JsonProperty("chaveacesso") @JsonAlias("chaveAcesso")
    val chaveAcesso: String? = null,
) {
    fun normalizada() = copy(
        workflowId = workflowId.trim(),
        solicitacaoId = solicitacaoId.trim(),
        cpfCnpjTomador = normalizaCnpj(cpfCnpjTomador),
        cnpjPrestador = normalizaCnpj(cnpjPrestador),
        numDocFiscal = numDocFiscal.trim(),
        valorParcela = valorParcela.stripTrailingZeros(),
        serieDocFiscal = serieDocFiscal?.trim()?.takeIf { it.isNotEmpty() },
        numeroParcela = numeroParcela?.trim()?.takeIf { it.isNotEmpty() },
        chaveAcesso = chaveAcesso?.filterNot(Char::isWhitespace)?.takeIf { it.isNotEmpty() },
    )
}

fun normalizaCnpj(cnpj: String): String = cnpj.filter(Char::isDigit)

enum class Erp { SAP, DEALER }
/**
 * - `PENDENTE`: sem resultado final (primeira consulta, reconsulta ou nova tentativa tecnica).
 * - `RETORNO_PENDENTE`: resultado final salvo, entrega ao SE em andamento/nova tentativa.
 * - `RETORNO_FALHOU`: entrega ao SE esgotou as novas tentativas (`integracao.erp.retorno-se`).
 *   Nao e reservada pelo job; a task nativa fica `FAILED` ate alguem voltar para `READY`.
 * - `CONCLUIDA`: SE confirmou o avanco.
 */
enum class StatusConsultaErp { PENDENTE, RETORNO_PENDENTE, RETORNO_FALHOU, CONCLUIDA }

data class ResultadoConsultaErp(
    val sucesso: Boolean,
    val codigo: String,
    val mensagem: String,
    val dados: Map<String, String> = emptyMap(),
)

@Document("consulta_erp_task")
data class ConsultaErpTask(
    @Id val id: String,
    val requisicao: RegistrarConsultaErpRequest,
    val atividadeId: String,
    val acaoSucesso: Int,
    val acaoFalha: Int,
    val tabelaFormulario: String,
    val camposRetorno: Map<String, String> = emptyMap(),
    val status: StatusConsultaErp = StatusConsultaErp.PENDENTE,
    val erp: Erp? = null,
    val resultado: ResultadoConsultaErp? = null,
    val respostaRegistrada: Boolean = false,
    val tentativasRetorno: Int = 0,
    val ultimoErro: String? = null,
    val criadaEm: Instant = Instant.now(),
    val atualizadaEm: Instant = criadaEm,
    val proximaTentativa: Instant = criadaEm,
    val concluidaEm: Instant? = null,
    val bloqueadoAte: Instant? = null,
    val reserva: String? = null,
    /** Consultas feitas ao ERP (a primeira e as reconsultas). */
    val consultas: Int = 0,
    /**
     * Ultimo resultado de NEGOCIO do ERP (final ou nao). Falha tecnica nao sobrescreve: vai para
     * [ultimaFalhaTecnica]. [resultado] so e preenchido quando final.
     */
    val ultimaConsulta: ResultadoConsultaErp? = null,
    val ultimaConsultaEm: Instant? = null,
    /** Ultima falha tecnica da consulta ao ERP (ERRO_CONSULTA_ERP), guardada a parte. */
    val ultimaFalhaTecnica: ResultadoConsultaErp? = null,
    val ultimaFalhaTecnicaEm: Instant? = null,
    /** Fim da espera por pagamento, fixado no aceite (criadaEm + espera-pagamento.prazo). */
    val esperaPagamentoAte: Instant? = null,
    /** Falhas tecnicas seguidas; zera com qualquer resultado nao tecnico. */
    val falhasTecnicas: Int = 0,
    val primeiraFalhaTecnicaEm: Instant? = null,
    /** Falhas seguidas da entrega ao SE (campos/comentario/avanco); zera ao reativar manualmente. */
    val falhasRetorno: Int = 0,
    val primeiraFalhaRetornoEm: Instant? = null,
)

data class ConsultaErpResponse(
    val id: String,
    val workflowId: String,
    val solicitacaoId: String,
    val status: StatusConsultaErp,
    val erp: Erp?,
    val resultado: ResultadoConsultaErp?,
    val tentativasRetorno: Int,
    val ultimoErro: String?,
    val consultas: Int = 0,
    val ultimaConsulta: ResultadoConsultaErp? = null,
    val ultimaConsultaEm: Instant? = null,
    /** Proxima consulta/entrega agendada; null quando concluida ou com retorno esgotado. */
    val proximaTentativa: Instant? = null,
    val esperaPagamentoAte: Instant? = null,
    val ultimaFalhaTecnica: ResultadoConsultaErp? = null,
    val ultimaFalhaTecnicaEm: Instant? = null,
    val falhasTecnicas: Int = 0,
    val falhasRetorno: Int = 0,
) {
    companion object {
        fun from(task: ConsultaErpTask) = ConsultaErpResponse(
            task.id, task.requisicao.workflowId, task.requisicao.solicitacaoId,
            task.status, task.erp, task.resultado, task.tentativasRetorno, task.ultimoErro,
            task.consultas, task.ultimaConsulta, task.ultimaConsultaEm, task.proximaAgendada(), task.esperaPagamentoAte,
            task.ultimaFalhaTecnica, task.ultimaFalhaTecnicaEm, task.falhasTecnicas, task.falhasRetorno,
        )
    }
}

/** Dados para acompanhamento, sem expor reservas ou configuracao do retorno ao SE. */
data class ConsultaErpListItem(
    val id: String,
    val requisicao: RegistrarConsultaErpRequest,
    val status: StatusConsultaErp,
    val erp: Erp?,
    val resultado: ResultadoConsultaErp?,
    val tentativasRetorno: Int,
    val ultimoErro: String?,
    val criadaEm: Instant,
    val atualizadaEm: Instant,
    val concluidaEm: Instant?,
    val consultas: Int = 0,
    val ultimaConsulta: ResultadoConsultaErp? = null,
    val ultimaConsultaEm: Instant? = null,
    val proximaTentativa: Instant? = null,
    val esperaPagamentoAte: Instant? = null,
    val ultimaFalhaTecnica: ResultadoConsultaErp? = null,
    val ultimaFalhaTecnicaEm: Instant? = null,
    val falhasTecnicas: Int = 0,
    val falhasRetorno: Int = 0,
) {
    companion object {
        fun from(task: ConsultaErpTask) = ConsultaErpListItem(
            task.id, task.requisicao, task.status, task.erp, task.resultado,
            task.tentativasRetorno, task.ultimoErro, task.criadaEm, task.atualizadaEm, task.concluidaEm,
            task.consultas, task.ultimaConsulta, task.ultimaConsultaEm, task.proximaAgendada(), task.esperaPagamentoAte,
            task.ultimaFalhaTecnica, task.ultimaFalhaTecnicaEm, task.falhasTecnicas, task.falhasRetorno,
        )
    }
}

private fun ConsultaErpTask.proximaAgendada(): Instant? =
    proximaTentativa.takeIf { status == StatusConsultaErp.PENDENTE || status == StatusConsultaErp.RETORNO_PENDENTE }
