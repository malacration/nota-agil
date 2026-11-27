package br.andrew.nota.agil.erp.sap

import br.andrew.nota.agil.model.TipoDuplicata
import br.andrew.nota.agil.model.erp.RegistrarConsultaErpRequest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.ExpectedCount
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.*
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** Dados ficticios: CNPJs, numeros e valores nao correspondem a registros reais. */
class SapConsultaErpClientTest {
    private val builder = RestClient.builder()
    private val server = MockRestServiceServer.bindTo(builder).build()
    private val rest = builder.build()
    private val properties = SapContasPagarProperties().apply {
        url = "https://sap-rovema.example.invalid/"
        tokenUrl = "https://sso.example.invalid/realms/teste/protocol/openid-connect/token"
        clientId = "nota-agil-teste"
        clientSecret = "segredo-de-teste"
    }
    private val tokens = SapRovemaTokenProvider(properties, Clock.fixed(Instant.parse("2026-11-16T12:00:00Z"), ZoneOffset.UTC), rest)
    private val client = SapConsultaErpClient(properties, tokens, rest)

    private val requisicao = RegistrarConsultaErpRequest(
        workflowId = "FXP-NA000001", solicitacaoId = "baixa-1",
        cpfCnpjTomador = "12345678000190", cnpjPrestador = "98765432000100",
        numDocFiscal = "000123", valorParcela = BigDecimal("125.5"), serieDocFiscal = "001",
        tipoDoc = TipoDuplicata.Nfse, numeroParcela = "1",
    )

    private val consulta = "https://sap-rovema.example.invalid/contas-pagar/pagamento-parcela/consulta"

    private fun esperaToken(token: String = "token-1") {
        server.expect(requestTo(properties.tokenUrl))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
            .andExpect(content().formDataContains(mapOf(
                "grant_type" to "client_credentials", "client_id" to "nota-agil-teste", "client_secret" to "segredo-de-teste",
            )))
            .andRespond(withSuccess("""{"access_token":"$token","expires_in":300}""", MediaType.APPLICATION_JSON))
    }

    @Test
    fun `envia token e corpo da consulta e PAGO e o unico sucesso`() {
        esperaToken()
        server.expect(requestTo(consulta))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("Authorization", "Bearer token-1"))
            .andExpect(jsonPath("$.cnpjFilial").value("12345678000190"))
            .andExpect(jsonPath("$.cnpjFornecedor").value("98765432000100"))
            .andExpect(jsonPath("$.numeroNota").value("000123"))
            .andExpect(jsonPath("$.serieNota").value("001"))
            .andExpect(jsonPath("$.numeroParcela").value("1"))
            .andExpect(jsonPath("$.valorParcela").value(125.5))
            .andRespond(withSuccess(PAGO, MediaType.APPLICATION_JSON))
        val r = client.consultar(requisicao)
        server.verify()
        assertTrue(r.sucesso)
        assertEquals("PAGO", r.codigo)
        assertEquals(SapConsultaErpClient.CHAVES_DADOS, r.dados.keys.toList())
        assertEquals("125.50", r.dados["parcelaValor"])
        assertEquals("REQUISICAO", r.dados[SapConsultaErpClient.ORIGEM_DADOS_FISCAIS], "sem resolver: tudo do pedido")
        assertEquals("R$", r.dados["notaMoeda"])
        assertEquals("0.00", r.dados["parcelaSaldo"])
        assertEquals("125.50", r.dados["pagamentosValor"])
        assertEquals("2", r.dados["pagamentosQuantidade"])
        assertEquals("9002", r.dados["pagamentoDocNum"], "ultimo pagamento")
        assertEquals("2026-11-10", r.dados["pagamentoData"])
        assertEquals("DocNum 9001 (DocEntry 501) em 2026-11-01: pago 25.50; DocNum 9002 (DocEntry 502) em 2026-11-10: pago 100.00",
            r.dados["pagamentos"])
        assertEquals("125.50", r.dados["parcelaValorLiquido"])
        assertEquals("0.00", r.dados["parcelaRetencao"])
        assertTrue(client.consultaPagamentoImplementada)
    }

    @Test
    fun `envia chave de acesso e tipo e devolve o criterio de localizacao`() {
        val chave = "1".repeat(44)
        esperaToken()
        server.expect(requestTo(consulta))
            .andExpect(jsonPath("$.chaveAcesso").value(chave))
            .andExpect(jsonPath("$.tipoNota").value("NFSE"))
            .andRespond(withSuccess("""{"codigo":"EM_ABERTO","mensagem":"aberta","nota":{"docEntry":10,"criterio":"CHAVE_ACESSO"}}""",
                MediaType.APPLICATION_JSON))
        val r = client.consultar(requisicao.copy(chaveAcesso = chave))
        server.verify()
        assertEquals("EM_ABERTO", r.codigo)
        assertEquals("CHAVE_ACESSO", r.dados["notaCriterio"])
    }

    @Test
    fun `sem chave nem tipo nao consulta o SAP e e DADOS_NOTA_INSUFICIENTES`() {
        for (tipo in listOf(null, TipoDuplicata.NaoDetectado)) {
            val r = client.consultar(requisicao.copy(tipoDoc = tipo, chaveAcesso = null))
            assertFalse(r.sucesso)
            assertEquals("DADOS_NOTA_INSUFICIENTES", r.codigo)
            assertEquals(SapConsultaErpClient.CHAVES_DADOS, r.dados.keys.toList())
        }
        server.verify()
    }

    @Test
    fun `dados que faltam no pedido vem do resolver e so a chave basta sem tipo`() {
        val chave = "2".repeat(44)
        val resolver = object : DadosFiscaisNotaResolver(org.mockito.Mockito.mock(br.andrew.nota.agil.repository.TaskRepository::class.java), null) {
            override fun completar(requisicao: RegistrarConsultaErpRequest) = DadosFiscais(chave, "002", null, "CREATE_TASK")
        }
        val comResolver = SapConsultaErpClient(properties, tokens, rest, resolver)
        esperaToken()
        server.expect(requestTo(consulta))
            .andExpect(jsonPath("$.chaveAcesso").value(chave))
            .andExpect(jsonPath("$.serieNota").value("002"))
            .andExpect(jsonPath("$.tipoNota").doesNotExist())
            .andRespond(withSuccess("""{"codigo":"DADOS_NOTA_DIVERGENTES","mensagem":"chave diverge"}""", MediaType.APPLICATION_JSON))
        val r = comResolver.consultar(requisicao.copy(tipoDoc = null, serieDocFiscal = null))
        server.verify()
        assertFalse(r.sucesso)
        assertEquals("DADOS_NOTA_DIVERGENTES", r.codigo)
        assertEquals("CREATE_TASK", r.dados[SapConsultaErpClient.ORIGEM_DADOS_FISCAIS])
        assertEquals(SapConsultaErpClient.CHAVES_DADOS, r.dados.keys.toList())
    }

    @Test
    fun `CTe de mesmo numero achado pela task nao vira tipo nem chave - DADOS_NOTA_INSUFICIENTES com a origem`() {
        val tasks = org.mockito.Mockito.mock(br.andrew.nota.agil.repository.TaskRepository::class.java)
        val cte = br.andrew.nota.agil.model.Duplicata(java.util.Date(), "98765432000100", "P", "12345678000190", "T",
            BigDecimal.TEN, BigDecimal.TEN, "123", TipoDuplicata.Cte, "11261098765432000100570010000001231123456780")
        org.mockito.Mockito.`when`(tasks.findAllByDuplicataCpfCnpjPrestadorInAndDuplicataNumeroIn(
            org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.anyList(),
        )).thenReturn(listOf(br.andrew.nota.agil.model.tasks.Task("t1", "n", br.andrew.nota.agil.model.tasks.TaskStatus.FINISHED,
            br.andrew.nota.agil.model.tasks.TaskTypes.CreateTask, cte)))
        val comResolver = SapConsultaErpClient(properties, tokens, rest, DadosFiscaisNotaResolver(tasks, null))
        val r = comResolver.consultar(requisicao.copy(tipoDoc = null, serieDocFiscal = null, chaveAcesso = null))
        server.verify()
        assertEquals("DADOS_NOTA_INSUFICIENTES", r.codigo)
        assertEquals("NENHUMA", r.dados[SapConsultaErpClient.ORIGEM_DADOS_FISCAIS])
        assertEquals(SapConsultaErpClient.CHAVES_DADOS, r.dados.keys.toList())
    }

    @Test
    fun `token fica em cache entre consultas`() {
        esperaToken()
        server.expect(ExpectedCount.twice(), requestTo(consulta))
            .andExpect(header("Authorization", "Bearer token-1"))
            .andRespond(withSuccess("""{"codigo":"EM_ABERTO","mensagem":"Parcela em aberto no SAP"}""", MediaType.APPLICATION_JSON))
        client.consultar(requisicao)
        client.consultar(requisicao)
        server.verify()
    }

    @Test
    fun `demais codigos do SAP sao falha com o mesmo conjunto de chaves`() {
        for (codigo in SapConsultaErpClient.CODIGOS_SAP - "PAGO") {
            val r = client.traduzir(ConsultaPagamentoParcelaSapResponse(codigo, "msg"))
            assertFalse(r.sucesso, codigo)
            assertEquals(codigo, r.codigo)
            assertEquals(SapConsultaErpClient.CHAVES_DADOS, r.dados.keys.toList())
            assertTrue(r.dados.values.all { it.isEmpty() })
        }
    }

    @Test
    fun `moeda nao suportada e falha e devolve a moeda da nota`() {
        val r = client.traduzir(ConsultaPagamentoParcelaSapResponse("MOEDA_NAO_SUPORTADA", "estrangeira",
            nota = ConsultaPagamentoParcelaSapResponse.Nota(docEntry = 10, moeda = "USD")))
        assertFalse(r.sucesso)
        assertEquals("MOEDA_NAO_SUPORTADA", r.codigo)
        assertEquals("USD", r.dados["notaMoeda"])
        assertEquals("", r.dados["parcelaValor"])
        assertEquals(SapConsultaErpClient.CHAVES_DADOS, r.dados.keys.toList())
    }

    @Test
    fun `PAGO sem evidencia de pagamento positivo vira falha tecnica`() {
        val parcela = ConsultaPagamentoParcelaSapResponse.Parcela(numero = 1, valor = BigDecimal("0.01"),
            valorBaixado = BigDecimal("0.01"), saldo = BigDecimal.ZERO, valorPagamentos = BigDecimal("0.01"))
        val pagamento = ConsultaPagamentoParcelaSapResponse.Pagamento(501, 9001, "2026-11-10", BigDecimal("0.01"))
        val casos = listOf(
            ConsultaPagamentoParcelaSapResponse("PAGO", "sem parcela"),
            ConsultaPagamentoParcelaSapResponse("PAGO", "sem pagamentos", parcela = parcela),
            ConsultaPagamentoParcelaSapResponse("PAGO", "total zero", parcela = parcela.copy(valor = BigDecimal.ZERO),
                pagamentos = listOf(pagamento)),
            ConsultaPagamentoParcelaSapResponse("PAGO", "total ausente", parcela = parcela.copy(valor = null),
                pagamentos = listOf(pagamento)),
            ConsultaPagamentoParcelaSapResponse("PAGO", "pago zero", parcela = parcela.copy(valorPagamentos = BigDecimal.ZERO),
                pagamentos = listOf(pagamento.copy(valorAplicado = BigDecimal.ZERO))),
        )
        for (caso in casos) {
            val r = client.traduzir(caso)
            assertFalse(r.sucesso, caso.mensagem)
            assertEquals("ERRO_CONSULTA_ERP", r.codigo, caso.mensagem)
            assertEquals(SapConsultaErpClient.CHAVES_DADOS, r.dados.keys.toList())
        }
        val ok = client.traduzir(ConsultaPagamentoParcelaSapResponse("PAGO", "um centavo pago", parcela = parcela,
            pagamentos = listOf(pagamento)))
        assertTrue(ok.sucesso)
    }

    @Test
    fun `retencao no pagamento - dados mostram bruto, retencao, liquido e o pago sem a retencao`() {
        val r = client.traduzir(ConsultaPagamentoParcelaSapResponse("PAGO", "ok",
            parcela = ConsultaPagamentoParcelaSapResponse.Parcela(numero = 1, valor = BigDecimal("100"),
                valorBaixado = BigDecimal("100"), saldo = BigDecimal.ZERO, valorPagamentos = BigDecimal("90"),
                retencaoPagamento = BigDecimal("10"), valorLiquido = BigDecimal("90")),
            pagamentos = listOf(ConsultaPagamentoParcelaSapResponse.Pagamento(501, 9001, "2026-11-10",
                valorAplicado = BigDecimal("100"), valorRetido = BigDecimal("10"), valorPago = BigDecimal("90")))))
        assertTrue(r.sucesso)
        assertEquals("100.00", r.dados["parcelaValor"])
        assertEquals("10.00", r.dados["parcelaRetencao"])
        assertEquals("90.00", r.dados["parcelaValorLiquido"])
        assertEquals("90.00", r.dados["pagamentosValor"])
        assertEquals("DocNum 9001 (DocEntry 501) em 2026-11-10: pago 90.00, retido 10.00", r.dados["pagamentos"])
    }

    @Test
    fun `PAGO com pago abaixo do liquido vira falha tecnica`() {
        val r = client.traduzir(ConsultaPagamentoParcelaSapResponse("PAGO", "inconsistente",
            parcela = ConsultaPagamentoParcelaSapResponse.Parcela(numero = 1, valor = BigDecimal("100"),
                valorPagamentos = BigDecimal("89.89"), valorLiquido = BigDecimal("90")),
            pagamentos = listOf(ConsultaPagamentoParcelaSapResponse.Pagamento(501, 9001, "2026-11-10",
                valorAplicado = BigDecimal("89.89"), valorPago = BigDecimal("89.89")))))
        assertEquals("ERRO_CONSULTA_ERP", r.codigo)
        assertFalse(r.sucesso)
    }

    @Test
    fun `retencao nao suportada e falha`() {
        val r = client.traduzir(ConsultaPagamentoParcelaSapResponse("RETENCAO_NAO_SUPORTADA", "nao fecha"))
        assertFalse(r.sucesso)
        assertEquals("RETENCAO_NAO_SUPORTADA", r.codigo)
    }

    @Test
    fun `codigo desconhecido nao e confundido com sucesso`() {
        val r = client.traduzir(ConsultaPagamentoParcelaSapResponse("pago", "minusculo nao vale"))
        assertFalse(r.sucesso)
        assertEquals("ERRO_CONSULTA_ERP", r.codigo)
        assertEquals("ERRO_CONSULTA_ERP", client.traduzir(ConsultaPagamentoParcelaSapResponse(null, null)).codigo)
    }

    @Test
    fun `401 renova o token uma vez e repete`() {
        esperaToken("token-1")
        server.expect(requestTo(consulta)).andExpect(header("Authorization", "Bearer token-1"))
            .andRespond(withStatus(HttpStatus.UNAUTHORIZED))
        esperaToken("token-2")
        server.expect(requestTo(consulta)).andExpect(header("Authorization", "Bearer token-2"))
            .andRespond(withSuccess(PAGO, MediaType.APPLICATION_JSON))
        assertEquals("PAGO", client.consultar(requisicao).codigo)
        server.verify()
    }

    @Test
    fun `erro HTTP do sap-rovema vira ERRO_CONSULTA_ERP sem sucesso e sem dado sensivel na mensagem`() {
        esperaToken()
        server.expect(requestTo(consulta)).andRespond(withStatus(HttpStatus.BAD_GATEWAY)
            .body("""{"mensagem":"detalhe interno com SQL"}""").contentType(MediaType.APPLICATION_JSON))
        val r = client.consultar(requisicao)
        assertFalse(r.sucesso)
        assertEquals("ERRO_CONSULTA_ERP", r.codigo)
        assertEquals("Falha tecnica na consulta ao SAP: sap-rovema respondeu HTTP 502", r.mensagem)
        assertFalse(r.mensagem.contains("SQL"))
        assertEquals(SapConsultaErpClient.CHAVES_DADOS, r.dados.keys.toList())
    }

    @Test
    fun `falha no token vira ERRO_CONSULTA_ERP`() {
        server.expect(requestTo(properties.tokenUrl)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE))
        val r = client.consultar(requisicao)
        assertEquals("ERRO_CONSULTA_ERP", r.codigo)
        assertFalse(r.sucesso)
    }

    @Test
    fun `sem configuracao fica fora do catalogo e nao chama nada`() {
        val semConfig = SapConsultaErpClient()
        assertFalse(semConfig.consultaPagamentoImplementada)
        assertEquals("ERP_NAO_CONFIGURADO", semConfig.consultar(requisicao).codigo)
        val parcial = SapConsultaErpClient(SapContasPagarProperties().apply { url = "https://x.invalid" }, tokens, rest)
        assertFalse(parcial.consultaPagamentoImplementada, "URL sem credenciais nao habilita")
        server.verify()
    }

    companion object {
        val PAGO = """
            {"codigo":"PAGO","mensagem":"Parcela quitada por pagamento efetuado no SAP",
             "nota":{"docEntry":10,"docNum":1010,"numero":123,"serie":"1","dataDocumento":"2026-10-01","cardCode":"FOR0000001","filial":2,"moeda":"R$"},
             "parcela":{"numero":1,"vencimento":"2026-11-10","valor":125.5,"valorBaixado":125.50,"saldo":0,"status":"C","valorPagamentos":125.50,
                         "retencaoPagamento":0,"valorLiquido":125.50},
             "pagamentos":[{"docEntry":501,"docNum":9001,"data":"2026-11-01","valorAplicado":25.5,"valorRetido":0,"valorPago":25.5},
                           {"docEntry":502,"docNum":9002,"data":"2026-11-10","valorAplicado":100.00,"valorRetido":0,"valorPago":100.00}]}
        """.trimIndent()
    }
}
