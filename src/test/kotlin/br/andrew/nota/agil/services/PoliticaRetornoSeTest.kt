package br.andrew.nota.agil.services

import br.andrew.nota.agil.erp.ConsultaErpConfiguration
import br.andrew.nota.agil.model.erp.ConsultaErpTask
import br.andrew.nota.agil.model.erp.RegistrarConsultaErpRequest
import br.andrew.nota.agil.services.PoliticaRetornoSe.Decisao
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant

class PoliticaRetornoSeTest {
    private val config = ConsultaErpConfiguration()
    private val politica = PoliticaRetornoSe(config)
    private val inicio = Instant.parse("2026-11-17T12:00:00Z")
    private val task = ConsultaErpTask(
        id = "t", atividadeId = "a", acaoSucesso = 1, acaoFalha = 2, tabelaFormulario = "fxpna",
        requisicao = RegistrarConsultaErpRequest("WF", "S", "12345678000190", "98765432000100", "1", BigDecimal.TEN),
        criadaEm = inicio,
    )

    /** Aplica a politica como o servico faz, ate esgotar; devolve as esperas agendadas. */
    private fun simula(): Pair<List<Duration>, Decisao.Esgotar> {
        var t = task
        var agora = inicio
        val esperas = mutableListOf<Duration>()
        while (true) {
            when (val d = politica.decidir(t, agora)) {
                is Decisao.NovaTentativa -> {
                    esperas += Duration.between(agora, d.em)
                    t = t.copy(falhasRetorno = d.falhas, primeiraFalhaRetornoEm = d.primeiraFalhaEm)
                    agora = d.em
                }
                is Decisao.Esgotar -> return esperas to d
            }
        }
    }

    @Test
    fun `backoff exponencial limitado a 6 h e esgota na janela de 24 h`() {
        val (esperas, fim) = simula()
        assertEquals(listOf(1L, 4, 16, 64, 256, 360, 360, 360).map(Duration::ofMinutes), esperas)
        assertEquals(9, fim.falhas, "9 tentativas de entrega; a 10a cairia em 29h41 da primeira falha")
        assertEquals(inicio, fim.primeiraFalhaEm)
    }

    @Test
    fun `limite de tentativas tambem esgota`() {
        config.retornoSe.tentativas = 2
        val (esperas, fim) = simula()
        assertEquals(listOf(Duration.ofMinutes(1), Duration.ofMinutes(4)), esperas)
        assertEquals(3, fim.falhas)
    }

    @Test
    fun `intervalo nao estoura com muitas falhas`() {
        config.retornoSe.multiplicador = 10
        assertEquals(Duration.ofHours(6), politica.intervalo(1000))
        config.retornoSe.multiplicador = 1
        assertEquals(Duration.ofMinutes(1), politica.intervalo(50))
    }
}
