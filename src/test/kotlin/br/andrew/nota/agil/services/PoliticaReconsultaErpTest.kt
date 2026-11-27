package br.andrew.nota.agil.services

import br.andrew.nota.agil.erp.ConsultaErpConfiguration
import br.andrew.nota.agil.model.erp.ConsultaErpTask
import br.andrew.nota.agil.model.erp.RegistrarConsultaErpRequest
import br.andrew.nota.agil.model.erp.ResultadoConsultaErp
import br.andrew.nota.agil.services.PoliticaReconsultaErp.Decisao
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant

class PoliticaReconsultaErpTest {
    private val config = ConsultaErpConfiguration()
    private val politica = PoliticaReconsultaErp(config)
    private val aceite = Instant.parse("2026-11-17T12:00:00Z")
    private val task = ConsultaErpTask(
        id = "t", atividadeId = "a", acaoSucesso = 1, acaoFalha = 2, tabelaFormulario = "fxpna",
        requisicao = RegistrarConsultaErpRequest("WF", "S", "12345678000190", "98765432000100", "1", BigDecimal.TEN),
        criadaEm = aceite, esperaPagamentoAte = aceite.plus(Duration.ofHours(96)),
    )

    private fun resultado(codigo: String) = ResultadoConsultaErp(codigo == "PAGO", codigo, codigo)
    private val erro = resultado("ERRO_CONSULTA_ERP")

    @Test
    fun `PAGO e final mesmo no inicio da espera e zera falhas tecnicas`() {
        val d = politica.decidir(task.copy(falhasTecnicas = 2, primeiraFalhaTecnicaEm = aceite), resultado("PAGO"), aceite)
        assertEquals(Decisao.Entregar(resultado("PAGO"), 0, null), d)
    }

    @Test
    fun `codigo de espera reconsulta no intervalo, sem passar do fim do prazo`() {
        assertEquals(Decisao.Reconsultar(aceite.plus(Duration.ofHours(3)), 0, null),
            politica.decidir(task, resultado("EM_ABERTO"), aceite))
        val quaseFim = aceite.plus(Duration.ofHours(95))
        assertEquals(Decisao.Reconsultar(aceite.plus(Duration.ofHours(96)), 0, null),
            politica.decidir(task, resultado("NOTA_NAO_ENCONTRADA"), quaseFim))
        assertEquals(Decisao.Entregar(resultado("PAGO_PARCIAL"), 0, null),
            politica.decidir(task, resultado("PAGO_PARCIAL"), aceite.plus(Duration.ofHours(96))))
    }

    @Test
    fun `lista de codigos de espera e configuravel`() {
        config.esperaPagamento.codigos = setOf("EM_ABERTO")
        assertEquals(Decisao.Entregar(resultado("NOTA_NAO_ENCONTRADA"), 0, null), politica.decidir(task, resultado("NOTA_NAO_ENCONTRADA"), aceite))
        assertTrue(politica.decidir(task, resultado("EM_ABERTO"), aceite) is Decisao.Reconsultar)
    }

    @Test
    fun `intervalo tecnico cresce e nao estoura com valores extremos`() {
        assertEquals(Duration.ofMinutes(15), politica.intervaloTecnico(1))
        assertEquals(Duration.ofHours(1), politica.intervaloTecnico(2))
        assertEquals(Duration.ofHours(4), politica.intervaloTecnico(3))
        config.falhaTecnica.multiplicador = 10
        assertTrue(politica.intervaloTecnico(11) > config.falhaTecnica.janelaMaxima)
    }

    @Test
    fun `falha tecnica conta a partir da primeira falha seguida`() {
        val primeira = politica.decidir(task, resultado("ERRO_CONSULTA_ERP"), aceite)
        assertEquals(Decisao.Reconsultar(aceite.plus(Duration.ofMinutes(15)), 1, aceite), primeira)
        val depois = aceite.plus(Duration.ofMinutes(15))
        val segunda = politica.decidir(task.copy(falhasTecnicas = 1, primeiraFalhaTecnicaEm = aceite),
            resultado("ERRO_CONSULTA_ERP"), depois)
        assertEquals(Decisao.Reconsultar(depois.plus(Duration.ofHours(1)), 2, aceite), segunda)
        val quarta = politica.decidir(task.copy(falhasTecnicas = 3, primeiraFalhaTecnicaEm = aceite),
            resultado("ERRO_CONSULTA_ERP"), aceite.plus(Duration.ofHours(6)))
        assertEquals(Decisao.Entregar(erro, 4, aceite), quarta)
    }

    @Test
    fun `sem novas tentativas configuradas a falha tecnica e final`() {
        config.falhaTecnica.tentativas = 0
        assertEquals(Decisao.Entregar(erro, 1, aceite), politica.decidir(task, resultado("ERRO_CONSULTA_ERP"), aceite))
    }

    // ---- Falha tecnica durante a espera (ja ha resultado de negocio) ----

    private val emEspera = task.copy(ultimaConsulta = resultado("EM_ABERTO"), ultimaConsultaEm = aceite)

    @Test
    fun `falha tecnica durante a espera nao encerra e respeita backoff limitado ao intervalo da espera`() {
        val t1 = aceite.plus(Duration.ofHours(3))
        assertEquals(Decisao.Reconsultar(t1.plus(Duration.ofMinutes(15)), 1, t1), politica.decidir(emEspera, erro, t1))
        // Bem alem das 3 tentativas / 24 h da regra das primeiras consultas: continua aguardando.
        val muitas = emEspera.copy(falhasTecnicas = 9, primeiraFalhaTecnicaEm = t1)
        val t2 = aceite.plus(Duration.ofHours(40))
        assertEquals(Decisao.Reconsultar(t2.plus(Duration.ofHours(3)), 10, t1), politica.decidir(muitas, erro, t2),
            "intervalo tecnico (4 h, 16 h...) limitado a espera-pagamento.intervalo (3 h)")
    }

    @Test
    fun `falha tecnica perto do fim agenda a ultima tentativa no prazo e no prazo entrega o ultimo resultado de negocio`() {
        val prazo = aceite.plus(Duration.ofHours(96))
        val quaseFim = prazo.minus(Duration.ofMinutes(5))
        assertEquals(Decisao.Reconsultar(prazo, 1, quaseFim), politica.decidir(emEspera, erro, quaseFim))
        val noPrazo = emEspera.copy(falhasTecnicas = 1, primeiraFalhaTecnicaEm = quaseFim)
        assertEquals(Decisao.Entregar(resultado("EM_ABERTO"), 2, quaseFim), politica.decidir(noPrazo, erro, prazo))
        assertEquals(Decisao.Entregar(resultado("EM_ABERTO"), 2, quaseFim),
            politica.decidir(noPrazo, erro, prazo.plus(Duration.ofHours(1))))
    }

    @Test
    fun `registro antigo com erro tecnico em ultimaConsulta segue a regra das primeiras consultas`() {
        val antigo = task.copy(ultimaConsulta = erro, falhasTecnicas = 3, primeiraFalhaTecnicaEm = aceite)
        assertNull(politica.ultimoResultadoNegocio(antigo))
        assertEquals(Decisao.Entregar(erro, 4, aceite), politica.decidir(antigo, erro, aceite.plus(Duration.ofHours(6))))
    }
}
