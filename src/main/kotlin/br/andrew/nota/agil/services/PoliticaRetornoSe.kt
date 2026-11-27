package br.andrew.nota.agil.services

import br.andrew.nota.agil.erp.ConsultaErpConfiguration
import br.andrew.nota.agil.model.erp.ConsultaErpTask
import java.time.Duration
import java.time.Instant

/**
 * Decide o que fazer quando a entrega ao SE falha (campos, comentario ou executeActivity
 * recusado). Sem efeito colateral: so calcula.
 *
 * Falhas seguidas da mesma entrega: nova tentativa em
 * `min(intervalo-inicial * multiplicador^(n-1), intervalo-maximo)`, enquanto houver
 * `tentativas` e a proxima cair dentro de `janela-maxima` a partir da primeira falha seguida.
 * Senao, [Decisao.Esgotar]: a tarefa vai para `RETORNO_FALHOU` e para de chamar o SE.
 */
class PoliticaRetornoSe(private val configuration: ConsultaErpConfiguration) {

    sealed interface Decisao {
        val falhas: Int
        val primeiraFalhaEm: Instant

        data class NovaTentativa(val em: Instant, override val falhas: Int, override val primeiraFalhaEm: Instant) : Decisao
        data class Esgotar(override val falhas: Int, override val primeiraFalhaEm: Instant) : Decisao
    }

    fun decidir(task: ConsultaErpTask, agora: Instant): Decisao {
        val config = configuration.retornoSe
        val falhas = task.falhasRetorno + 1
        val primeira = task.primeiraFalhaRetornoEm ?: agora
        if (falhas > config.tentativas) return Decisao.Esgotar(falhas, primeira)
        val proxima = agora.plus(intervalo(falhas))
        if (proxima.isAfter(primeira.plus(config.janelaMaxima))) return Decisao.Esgotar(falhas, primeira)
        return Decisao.NovaTentativa(proxima, falhas, primeira)
    }

    /** Espera antes da nova tentativa apos a falha [falha] (1 = primeira), sem estourar. */
    internal fun intervalo(falha: Int): Duration {
        val config = configuration.retornoSe
        var intervalo = config.intervaloInicial
        repeat(falha - 1) {
            if (intervalo >= config.intervaloMaximo) return config.intervaloMaximo
            intervalo = intervalo.multipliedBy(config.multiplicador.toLong())
        }
        return minOf(intervalo, config.intervaloMaximo)
    }
}
