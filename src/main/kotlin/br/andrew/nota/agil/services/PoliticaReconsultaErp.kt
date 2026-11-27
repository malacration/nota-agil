package br.andrew.nota.agil.services

import br.andrew.nota.agil.erp.ConsultaErpConfiguration
import br.andrew.nota.agil.erp.ConsultaErpConfiguration.Companion.CODIGO_FALHA_TECNICA
import br.andrew.nota.agil.model.erp.ConsultaErpTask
import br.andrew.nota.agil.model.erp.ResultadoConsultaErp
import java.time.Duration
import java.time.Instant

/**
 * Decide, depois de cada consulta ao ERP, se o resultado ja e final (vai ao SE) ou se a tarefa
 * volta para a fila para reconsulta. Sem efeito colateral: so calcula.
 *
 * 1. `sucesso` (PAGO): final na hora, mesmo que a espera ainda nao tenha acabado.
 * 2. Codigo de espera (`espera-pagamento.codigos`): reconsulta a cada `espera-pagamento.intervalo`
 *    ate [ConsultaErpTask.esperaPagamentoAte]; a ultima reconsulta cai exatamente no fim do prazo.
 *    Consulta feita no fim do prazo ou depois -> final com esse resultado.
 * 3. Falha tecnica ([CODIGO_FALHA_TECNICA]) quando a tarefa JA TEM resultado de negocio em espera
 *    ([ConsultaErpTask.ultimaConsulta]): nao encerra a espera. Nova tentativa em
 *    `min(intervalo tecnico, espera-pagamento.intervalo)`, nunca depois do fim do prazo; falha no
 *    fim do prazo ou depois -> final com o ULTIMO RESULTADO DE NEGOCIO (nao com o erro tecnico).
 * 4. Falha tecnica sem resultado de negocio ainda (primeiras consultas): ate
 *    `falha-tecnica.tentativas` novas tentativas, com intervalo `intervalo-inicial *
 *    multiplicador^(n-1)`, todas dentro de `falha-tecnica.janela-maxima` (<= 24 h) a partir da
 *    primeira falha seguida. Esgotou -> final (fluxo manual) com o resultado tecnico.
 * 5. Qualquer outro codigo: final na hora.
 *
 * Qualquer resultado de negocio zera a contagem de falhas tecnicas seguidas.
 */
class PoliticaReconsultaErp(private val configuration: ConsultaErpConfiguration) {

    sealed interface Decisao {
        val falhasTecnicas: Int
        val primeiraFalhaTecnicaEm: Instant?

        /** Resultado final: grava [resultado] em `resultado` e segue o retorno ao SE. */
        data class Entregar(
            val resultado: ResultadoConsultaErp,
            override val falhasTecnicas: Int,
            override val primeiraFalhaTecnicaEm: Instant?,
        ) : Decisao

        /** Ainda nao final: reconsulta em [em]. Nada vai ao SE. */
        data class Reconsultar(
            val em: Instant,
            override val falhasTecnicas: Int,
            override val primeiraFalhaTecnicaEm: Instant?,
        ) : Decisao
    }

    fun decidir(task: ConsultaErpTask, resultado: ResultadoConsultaErp, agora: Instant): Decisao {
        if (resultado.sucesso) return Decisao.Entregar(resultado, 0, null)
        if (resultado.codigo == CODIGO_FALHA_TECNICA) return falhaTecnica(task, resultado, agora)
        return aguardarOuEntregar(task, resultado, agora, configuration.esperaPagamento.intervalo, 0, null)
    }

    /** Fim da espera. Tarefa gravada antes deste campo existir usa o prazo atual da configuracao. */
    fun prazoEspera(task: ConsultaErpTask): Instant =
        task.esperaPagamentoAte ?: task.criadaEm.plus(configuration.esperaPagamento.prazo)

    /** Ultimo resultado de negocio guardado (registro antigo podia guardar o erro tecnico ali). */
    fun ultimoResultadoNegocio(task: ConsultaErpTask): ResultadoConsultaErp? =
        task.ultimaConsulta?.takeUnless { !it.sucesso && it.codigo == CODIGO_FALHA_TECNICA }

    private fun aguardarOuEntregar(
        task: ConsultaErpTask, negocio: ResultadoConsultaErp, agora: Instant,
        intervalo: Duration, falhas: Int, primeira: Instant?,
    ): Decisao {
        if (negocio.codigo !in configuration.esperaPagamento.codigos) return Decisao.Entregar(negocio, falhas, primeira)
        val prazo = prazoEspera(task)
        if (!agora.isBefore(prazo)) return Decisao.Entregar(negocio, falhas, primeira)
        val proxima = agora.plus(intervalo)
        return Decisao.Reconsultar(if (proxima.isAfter(prazo)) prazo else proxima, falhas, primeira)
    }

    private fun falhaTecnica(task: ConsultaErpTask, resultado: ResultadoConsultaErp, agora: Instant): Decisao {
        val config = configuration.falhaTecnica
        val falhas = task.falhasTecnicas + 1
        val primeira = task.primeiraFalhaTecnicaEm ?: agora
        val negocio = ultimoResultadoNegocio(task)
        if (negocio != null) {
            // Ja em espera: a indisponibilidade do ERP nao encerra a espera nem troca o resultado.
            val intervalo = minOf(intervaloTecnico(falhas), configuration.esperaPagamento.intervalo)
            return aguardarOuEntregar(task, negocio, agora, intervalo, falhas, primeira)
        }
        if (falhas > config.tentativas) return Decisao.Entregar(resultado, falhas, primeira)
        val limite = primeira.plus(config.janelaMaxima)
        val proxima = agora.plus(intervaloTecnico(falhas))
        if (proxima.isAfter(limite)) return Decisao.Entregar(resultado, falhas, primeira)
        return Decisao.Reconsultar(proxima, falhas, primeira)
    }

    /** intervalo-inicial * multiplicador^(falha-1), sem estourar: para ao passar da janela. */
    internal fun intervaloTecnico(falha: Int): Duration {
        val config = configuration.falhaTecnica
        var intervalo = config.intervaloInicial
        repeat(falha - 1) {
            if (intervalo > config.janelaMaxima) return intervalo
            intervalo = intervalo.multipliedBy(config.multiplicador.toLong())
        }
        return intervalo
    }
}
