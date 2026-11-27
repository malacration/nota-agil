package br.andrew.nota.agil.services

import br.andrew.nota.agil.erp.*
import br.andrew.nota.agil.erp.sap.SapConsultaErpClient
import br.andrew.nota.agil.model.erp.*
import br.andrew.nota.agil.repository.ConsultaErpRepository
import br.andrew.nota.agil.repository.CompanyRepository
import br.andrew.nota.agil.softexpert.service.DocumentService
import br.andrew.nota.agil.softexpert.service.WorkFlowEnvrioment
import br.andrew.nota.agil.softexpert.service.WorkFlowService
import br.andrew.wsdl.document.DocumentoPortType
import br.andrew.wsdl.workflow.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentCaptor
import br.andrew.nota.agil.support.anyValue as any
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.*
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.math.BigDecimal
import java.math.BigInteger
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Optional
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest

class ConsultaErpServiceTest {
    private val documentos = mock(DocumentoPortType::class.java)
    private val soap = mock(WorkflowPortType::class.java)
    private val repository = mock(ConsultaErpRepository::class.java)
    private val empresas = mock(CompanyRepository::class.java)
    private val queue = mock(ConsultaErpTaskQueue::class.java)
    private val tasks = mutableMapOf<String, ConsultaErpTask>()
    private val config = ConsultaErpConfiguration().apply {
        atividadeId = "integracao-erp"
        acaoSucesso = 3
        acaoFalha = 5
    }
    private val requisicao = RegistrarConsultaErpRequest(
        "FXP-NA001", "baixa-1", "12345678000190", "98765432000100", "000123", BigDecimal("125.50"),
        serieDocFiscal = "001", activityOrder = 2,
    )

    init {
        `when`(empresas.findById(anyString())).thenReturn(Optional.empty())
        `when`(repository.buscar(anyString())).thenAnswer { tasks[it.getArgument(0)] }
        `when`(repository.inserirOuRecuperar(any())).thenAnswer {
            val task = it.getArgument<ConsultaErpTask>(0)
            tasks.getOrPut(task.id) { task }
        }
        `when`(repository.reservarProxima(any(), any())).thenAnswer {
            val agora = it.getArgument<Instant>(0)
            val id = it.getArgument<String?>(1)
            tasks.values.firstOrNull { task ->
                (id == null || task.id == id) &&
                    task.status in setOf(StatusConsultaErp.PENDENTE, StatusConsultaErp.RETORNO_PENDENTE) &&
                    !task.proximaTentativa.isAfter(agora) &&
                    (task.bloqueadoAte == null || !task.bloqueadoAte.isAfter(agora))
            }?.let { task ->
                task.copy(reserva = "reserva", bloqueadoAte = agora.plusSeconds(600)).also { reservada -> tasks[task.id] = reservada }
            }
        }
        `when`(repository.atualizar(any(), anyBoolean())).thenAnswer {
            val task = it.getArgument<ConsultaErpTask>(0)
            val liberar = it.getArgument<Boolean>(1)
            (if (liberar) task.copy(reserva = null, bloqueadoAte = null) else task).also { salva -> tasks[task.id] = salva }
        }
        `when`(repository.reativarRetorno(anyString(), any())).thenAnswer {
            val agora = it.getArgument<Instant>(1)
            tasks[it.getArgument<String>(0)]?.takeIf { task -> task.status == StatusConsultaErp.RETORNO_FALHOU }
                ?.copy(status = StatusConsultaErp.RETORNO_PENDENTE, falhasRetorno = 0, primeiraFalhaRetornoEm = null,
                    proximaTentativa = agora, atualizadaEm = agora, reserva = null, bloqueadoAte = null)
                ?.also { task -> tasks[task.id] = task }
        }
        `when`(soap.newComment(any())).thenReturn(NewCommentResponseType().apply { status = "SUCCESS" })
        `when`(soap.executeActivity(any())).thenReturn(ExecuteActivityResponseType().apply { status = "SUCCESS" })
        `when`(soap.editEntityRecord(any())).thenReturn(EditEntityRecordResponseType().apply { status = "SUCCESS" })
    }

    /** Relogio controlado pelo teste: as esperas sao de horas/dias. */
    private class Relogio(var agora: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?): Clock = this
        override fun instant(): Instant = agora
        fun avanca(duracao: Duration) { agora = agora.plus(duracao) }
    }

    private val relogio = Relogio(Instant.now())

    private fun service(clients: List<ConsultaErpClient> = listOf(SapConsultaErpClient(), DealerConsultaErpClient())) =
        ConsultaErpService(repository, ConsultaErpResolver(config, clients, empresas), config,
            WorkFlowEnvrioment("FXP-NA", "fxpna", "doc", "upload"),
            ConsultaErpRetornoService(WorkFlowService(soap, DocumentService(documentos))), queue, relogio)

    /** Client SAP que devolve os codigos em sequencia (o ultimo se repete). "EXCECAO" lanca. */
    private inner class Roteiro(vararg val codigos: String) : ConsultaErpClient {
        var consultas = 0
        override val erp = Erp.SAP
        override fun consultar(requisicao: RegistrarConsultaErpRequest): ResultadoConsultaErp {
            val codigo = codigos[minOf(consultas, codigos.size - 1)]
            consultas++
            if (codigo == "EXCECAO") error("ERP offline")
            return ResultadoConsultaErp(codigo == "PAGO", codigo, "msg $codigo", mapOf("parcelaValor" to "125.50"))
        }
    }

    private fun roteiro(vararg codigos: String): Pair<ConsultaErpService, Roteiro> {
        config.cnpjs = mapOf(requisicao.cpfCnpjTomador to Erp.SAP)
        val client = Roteiro(*codigos)
        return service(listOf(client)) to client
    }

    private fun acoes(): List<ExecuteActivityRequestType> {
        val captor = ArgumentCaptor.forClass(ExecuteActivityRequestType::class.java)
        verify(soap, atLeastOnce()).executeActivity(captor.capture())
        return captor.allValues
    }

    @Test
    fun `listagem deve validar paginacao sem consultar banco`() {
        for ((page, size) in listOf(-1 to 20, 0 to 0, 0 to 101)) {
            val error = assertThrows<ResponseStatusException> { service().listar(page, size, null, null) }
            assertEquals(HttpStatus.BAD_REQUEST, error.statusCode)
        }
        assertThrows<ResponseStatusException> { service().listar(0, 20, null, "x".repeat(101)) }
        verifyNoInteractions(repository, soap)
    }

    @Test
    fun `listagem deve usar filtros normalizados e ordem recente sem processar`() {
        `when`(repository.listar(any(), any(), any())).thenReturn(Page.empty())
        service().listar(1, 10, StatusConsultaErp.PENDENTE, " WF001 ")
        val page = ArgumentCaptor.forClass(org.springframework.data.domain.Pageable::class.java)
        verify(repository).listar(page.capture() ?: PageRequest.of(1, 10), org.mockito.ArgumentMatchers.eq(StatusConsultaErp.PENDENTE), org.mockito.ArgumentMatchers.eq("WF001"))
        assertEquals(1, page.value.pageNumber)
        assertEquals(10, page.value.pageSize)
        assertTrue(page.value.sort.getOrderFor("criadaEm")!!.isDescending)
        verifyNoInteractions(soap)
    }

    @Test
    fun `deve persistir e deduplicar chamada sem consultar ERP nem SE no POST`() {
        val service = service()
        val primeira = service.registrar(requisicao)
        val repetida = service.registrar(requisicao.copy(
            cpfCnpjTomador = "12.345.678/0001-90", cnpjPrestador = "98.765.432/0001-00", valorParcela = BigDecimal("125.5"),
        ))
        assertEquals(primeira.id, repetida.id)
        assertEquals(1, tasks.size)
        assertEquals(StatusConsultaErp.PENDENTE, primeira.status)
        verify(queue, times(2)).sincronizar(primeira)
        verifyNoInteractions(soap)
    }

    @Test
    fun `worker deve executar somente protocolo solicitado e sincronizar conclusao`() {
        val service = service()
        val primeira = service.registrar(requisicao)
        val segunda = service.registrar(requisicao.copy(solicitacaoId = "outra"))
        service.executar(segunda.id)
        assertEquals(StatusConsultaErp.PENDENTE, tasks[primeira.id]!!.status)
        assertEquals(StatusConsultaErp.CONCLUIDA, tasks[segunda.id]!!.status)
        verify(queue).sincronizar(tasks[segunda.id]!!)
        assertEquals(1, acoes().size)
    }

    @Test
    fun `deve rejeitar repeticao com dados diferentes e permitir nova ocorrencia`() {
        val service = service()
        service.registrar(requisicao)
        val erro = assertThrows<ResponseStatusException> { service.registrar(requisicao.copy(numDocFiscal = "outra")) }
        assertEquals(HttpStatus.CONFLICT, erro.statusCode)
        service.registrar(requisicao.copy(solicitacaoId = "baixa-2", activityOrder = 3))
        assertEquals(2, tasks.size)
    }

    @Test
    fun `deve recusar novas solicitacoes enquanto fluxo nao estiver configurado`() {
        config.atividadeId = ""
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE,
            assertThrows<ResponseStatusException> { service().registrar(requisicao) }.statusCode)
        assertTrue(tasks.isEmpty())
    }

    @Test
    fun `deve impedir acoes de sucesso e falha iguais`() {
        config.acaoFalha = config.acaoSucesso
        assertThrows<ResponseStatusException> { service().registrar(requisicao) }
        assertTrue(tasks.isEmpty())
    }

    @Test
    fun `cnpj nao mapeado deve retornar falha ao SE e concluir a entrega`() {
        val service = service()
        val task = service.registrar(requisicao)
        service.processarPendentes()
        val concluida = service.buscar(task.id)
        assertEquals(StatusConsultaErp.CONCLUIDA, concluida.status)
        assertEquals("CNPJ_NAO_MAPEADO", concluida.resultado?.codigo)
        assertEquals(false, concluida.resultado?.sucesso)
        val acao = acoes().single()
        assertEquals(BigInteger.valueOf(5), acao.actionSequence)
        assertEquals("FXP-NA001", acao.workflowID)
        assertEquals("integracao-erp", acao.activityID)
        assertEquals(BigInteger.TWO, acao.activityOrder)
        assertNotNull(concluida.concluidaEm)
        service.processarPendentes()
        verify(soap, times(1)).executeActivity(any())
    }

    @Test
    fun `sap sem configuracao e dealer esqueleto devem produzir falha`() {
        config.cnpjs = mapOf("12.345.678/0001-90" to Erp.SAP, "11111111000111" to Erp.DEALER)
        val service = service()
        val sap = service.registrar(requisicao)
        val dealer = service.registrar(requisicao.copy(solicitacaoId = "dealer", cpfCnpjTomador = "11111111000111"))
        service.processarPendentes()
        assertEquals(Erp.SAP, service.buscar(sap.id).erp)
        assertEquals(Erp.DEALER, service.buscar(dealer.id).erp)
        assertEquals("ERP_NAO_CONFIGURADO", service.buscar(sap.id).resultado?.codigo)
        assertEquals("ERP_NAO_IMPLEMENTADO", service.buscar(dealer.id).resultado?.codigo)
        listOf(sap, dealer).forEach {
            assertEquals(false, service.buscar(it.id).resultado?.sucesso)
            assertEquals(StatusConsultaErp.CONCLUIDA, service.buscar(it.id).status)
        }
        assertTrue(acoes().all { it.actionSequence == BigInteger.valueOf(5) })
    }

    @Test
    fun `sucesso deve atualizar campos e registrar comentario antes de executar a acao correta`() {
        config.cnpjs = mapOf(requisicao.cpfCnpjTomador to Erp.SAP)
        config.camposRetorno = mapOf("sucesso" to "erpok", "idTitulo" to "erptitulo")
        val client = object : ConsultaErpClient {
            override val erp = Erp.SAP
            override fun consultar(requisicao: RegistrarConsultaErpRequest) =
                ResultadoConsultaErp(true, "PAGAMENTO_ENCONTRADO", "Pagamento localizado", mapOf("idTitulo" to "1234"))
        }
        val service = service(listOf(client))
        val task = service.registrar(requisicao)
        service.processarPendentes()
        assertEquals(true, service.buscar(task.id).resultado?.sucesso)
        assertEquals(BigInteger.valueOf(3), acoes().single().actionSequence)
        val ordem = inOrder(soap)
        val formulario = ArgumentCaptor.forClass(EditEntityRecordRequestType::class.java)
        ordem.verify(soap).editEntityRecord(formulario.capture())
        ordem.verify(soap).newComment(any())
        ordem.verify(soap).executeActivity(any())
        assertEquals("fxpna", formulario.value.entityID)
        assertEquals(mapOf("erpok" to "true", "erptitulo" to "1234"),
            formulario.value.entityAttributeList.associate { it.entityAttributeID to it.entityAttributeValue })
    }

    @Test
    fun `falha tecnica no ERP agenda nova tentativa sem interromper outras tarefas`() {
        val (service, _) = roteiro("EXCECAO")
        val primeira = service.registrar(requisicao)
        val segunda = service.registrar(requisicao.copy(solicitacaoId = "outra", cpfCnpjTomador = "11111111000111"))
        service.processarPendentes()
        val aguardando = service.buscar(primeira.id)
        assertEquals(StatusConsultaErp.PENDENTE, aguardando.status)
        assertNull(aguardando.resultado, "falha tecnica com tentativas sobrando nao e resultado final")
        assertNull(aguardando.ultimaConsulta, "falha tecnica nao e resultado de negocio")
        assertEquals("ERRO_CONSULTA_ERP", aguardando.ultimaFalhaTecnica?.codigo)
        assertEquals(1, aguardando.falhasTecnicas)
        assertNull(aguardando.reserva)
        assertEquals(StatusConsultaErp.CONCLUIDA, service.buscar(segunda.id).status)
        assertEquals(1, acoes().size, "so a outra tarefa foi ao SE")
    }

    @Test
    fun `falha no avanco deve repetir somente a entrega sem nova consulta nem comentario`() {
        config.cnpjs = mapOf(requisicao.cpfCnpjTomador to Erp.SAP)
        var consultas = 0
        val client = object : ConsultaErpClient {
            override val erp = Erp.SAP
            override fun consultar(requisicao: RegistrarConsultaErpRequest): ResultadoConsultaErp {
                consultas++
                return ResultadoConsultaErp(false, "NAO_ENCONTRADO", "Nao encontrado")
            }
        }
        `when`(soap.executeActivity(any())).thenReturn(
            ExecuteActivityResponseType().apply { status = "FAILURE"; detail = "Atividade ainda nao disponivel" },
            ExecuteActivityResponseType().apply { status = "SUCCESS" },
        )
        val service = service(listOf(client))
        val task = service.registrar(requisicao)
        service.processarPendentes()
        val pendente = service.buscar(task.id)
        assertEquals(StatusConsultaErp.RETORNO_PENDENTE, pendente.status)
        assertTrue(pendente.respostaRegistrada)
        assertNotNull(pendente.ultimoErro)
        assertNull(pendente.reserva)
        assertTrue(pendente.proximaTentativa.isAfter(Instant.now()))
        tasks[task.id] = pendente.copy(proximaTentativa = Instant.EPOCH)
        service.processarPendentes()
        assertEquals(StatusConsultaErp.CONCLUIDA, service.buscar(task.id).status)
        assertEquals(2, service.buscar(task.id).tentativasRetorno)
        assertEquals(1, consultas)
        verify(soap, times(1)).newComment(any())
        verify(soap, times(2)).executeActivity(any())
    }

    @Test
    fun `falha no registro do resultado nao pode avancar o fluxo`() {
        `when`(soap.newComment(any())).thenReturn(NewCommentResponseType().apply { status = "FAILURE"; detail = "Sem permissao" })
        val service = service()
        val task = service.registrar(requisicao)
        service.processarPendentes()
        assertEquals(StatusConsultaErp.RETORNO_PENDENTE, service.buscar(task.id).status)
        assertFalse(service.buscar(task.id).respostaRegistrada)
        verify(soap, never()).executeActivity(any())
    }

    // ---- Espera por pagamento (TTL) e falhas tecnicas ----

    /** Roda o job ate a tarefa nao ter mais nada a fazer agora, avancando o relogio ate a proxima. */
    private fun rodaAte(service: ConsultaErpService, id: String, limite: Duration) {
        val fim = relogio.agora.plus(limite)
        service.processarPendentes()
        while (service.buscar(id).status != StatusConsultaErp.CONCLUIDA) {
            val proxima = service.buscar(id).proximaTentativa
            if (proxima.isAfter(fim)) return
            if (proxima.isAfter(relogio.agora)) relogio.agora = proxima
            service.processarPendentes()
        }
    }

    @Test
    fun `parcela em aberto aguarda sem chamar o SE e PAGO dentro do prazo vai para o sucesso uma unica vez`() {
        val (service, client) = roteiro("EM_ABERTO", "PAGO_PARCIAL", "PAGO")
        val aceite = relogio.agora
        val task = service.registrar(requisicao)
        assertEquals(aceite.plus(Duration.ofHours(96)), task.esperaPagamentoAte)
        service.processarPendentes()
        val aguardando = service.buscar(task.id)
        assertEquals(StatusConsultaErp.PENDENTE, aguardando.status)
        assertNull(aguardando.resultado)
        assertEquals("EM_ABERTO", aguardando.ultimaConsulta?.codigo)
        assertEquals(aceite.plus(Duration.ofHours(3)), aguardando.proximaTentativa)
        assertEquals(1, aguardando.consultas)
        assertEquals(0, aguardando.tentativasRetorno)
        verify(queue, atLeastOnce()).sincronizar(aguardando)
        verifyNoInteractions(soap)

        // Antes do intervalo, nem o job nem o Run consultam de novo.
        relogio.avanca(Duration.ofHours(2))
        service.processarPendentes()
        service.executar(task.id)
        assertEquals(1, client.consultas)

        relogio.avanca(Duration.ofHours(1))
        service.processarPendentes()
        assertEquals("PAGO_PARCIAL", service.buscar(task.id).ultimaConsulta?.codigo)
        verifyNoInteractions(soap)

        relogio.avanca(Duration.ofHours(3))
        service.processarPendentes()
        val concluida = service.buscar(task.id)
        assertEquals(StatusConsultaErp.CONCLUIDA, concluida.status)
        assertEquals("PAGO", concluida.resultado?.codigo)
        assertEquals(3, concluida.consultas)
        assertEquals(BigInteger.valueOf(3), acoes().single().actionSequence)
        val comentario = ArgumentCaptor.forClass(NewCommentRequestType::class.java)
        verify(soap, times(1)).newComment(comentario.capture())
        assertTrue(comentario.value.text.contains("Consultas ao ERP: 3"))
        relogio.avanca(Duration.ofDays(5))
        service.processarPendentes()
        assertEquals(3, client.consultas)
        verify(soap, times(1)).executeActivity(any())
    }

    @Test
    fun `prazo de espera encerrado sem PAGO vai para o fluxo manual com o ultimo resultado`() {
        config.esperaPagamento.intervalo = Duration.ofHours(5)
        val (service, client) = roteiro("NOTA_NAO_ENCONTRADA", "EM_ABERTO", "PAGO_PARCIAL")
        val aceite = relogio.agora
        val task = service.registrar(requisicao)
        rodaAte(service, task.id, Duration.ofHours(95))
        val antes = service.buscar(task.id)
        assertEquals(StatusConsultaErp.PENDENTE, antes.status)
        assertEquals(aceite.plus(Duration.ofHours(96)), antes.proximaTentativa, "ultima reconsulta cai no fim do prazo")
        verifyNoInteractions(soap)

        relogio.agora = aceite.plus(Duration.ofHours(96))
        service.processarPendentes()
        val concluida = service.buscar(task.id)
        assertEquals(StatusConsultaErp.CONCLUIDA, concluida.status)
        assertEquals("PAGO_PARCIAL", concluida.resultado?.codigo)
        assertEquals(false, concluida.resultado?.sucesso)
        // 0h, 5h, ..., 95h (20 consultas) + a do fim do prazo.
        assertEquals(21, client.consultas)
        assertEquals(21, concluida.consultas)
        assertEquals(BigInteger.valueOf(5), acoes().single().actionSequence)
        verify(soap, times(1)).newComment(any())
    }

    @Test
    fun `prazo fica gravado no aceite e mudar a configuracao depois nao altera a tarefa`() {
        val (service, _) = roteiro("EM_ABERTO")
        val task = service.registrar(requisicao)
        config.esperaPagamento.prazo = Duration.ZERO
        service.processarPendentes()
        assertEquals(StatusConsultaErp.PENDENTE, service.buscar(task.id).status)
        assertEquals(task.esperaPagamentoAte, service.buscar(task.id).esperaPagamentoAte)
    }

    @Test
    fun `prazo zero desliga a espera e tarefa antiga sem prazo usa a configuracao atual`() {
        config.esperaPagamento.prazo = Duration.ZERO
        val (service, _) = roteiro("EM_ABERTO")
        val task = service.registrar(requisicao)
        service.processarPendentes()
        assertEquals("EM_ABERTO", service.buscar(task.id).resultado?.codigo)
        assertEquals(StatusConsultaErp.CONCLUIDA, service.buscar(task.id).status)

        config.esperaPagamento.prazo = Duration.ofHours(96)
        val antiga = service.registrar(requisicao.copy(solicitacaoId = "antiga"))
        tasks[antiga.id] = antiga.copy(esperaPagamentoAte = null, criadaEm = relogio.agora.minus(Duration.ofHours(97)))
        service.processarPendentes()
        assertEquals(StatusConsultaErp.CONCLUIDA, service.buscar(antiga.id).status, "criadaEm + prazo atual ja venceu")
    }

    @Test
    fun `codigos finais vao ao SE na hora, sem espera`() {
        for (codigo in listOf("AMBIGUO", "VALOR_DIVERGENTE", "MOEDA_NAO_SUPORTADA", "RETENCAO_NAO_SUPORTADA",
            "PARCELA_NAO_ENCONTRADA", "FILIAL_NAO_ENCONTRADA", "BAIXADA_SEM_PAGAMENTO")) {
            val (service, client) = roteiro(codigo)
            val task = service.registrar(requisicao.copy(solicitacaoId = codigo))
            service.processarPendentes()
            assertEquals(StatusConsultaErp.CONCLUIDA, service.buscar(task.id).status, codigo)
            assertEquals(codigo, service.buscar(task.id).resultado?.codigo)
            assertEquals(1, client.consultas, codigo)
        }
    }

    @Test
    fun `falha tecnica tenta mais tres vezes com intervalo crescente e depois vai ao fluxo manual`() {
        val (service, client) = roteiro("ERRO_CONSULTA_ERP")
        val inicio = relogio.agora
        val task = service.registrar(requisicao)
        val agendas = mutableListOf<Duration>()
        service.processarPendentes()
        while (service.buscar(task.id).status == StatusConsultaErp.PENDENTE) {
            val proxima = service.buscar(task.id).proximaTentativa
            agendas += Duration.between(relogio.agora, proxima)
            relogio.agora = proxima
            service.processarPendentes()
        }
        assertEquals(listOf(Duration.ofMinutes(15), Duration.ofHours(1), Duration.ofHours(4)), agendas)
        assertEquals(4, client.consultas, "a primeira e mais tres")
        val concluida = service.buscar(task.id)
        assertEquals("ERRO_CONSULTA_ERP", concluida.resultado?.codigo)
        assertEquals(StatusConsultaErp.CONCLUIDA, concluida.status)
        assertEquals(4, concluida.falhasTecnicas)
        assertEquals(inicio, concluida.primeiraFalhaTecnicaEm)
        assertEquals(BigInteger.valueOf(5), acoes().single().actionSequence)
        verify(soap, times(1)).newComment(any())
    }

    @Test
    fun `falhas tecnicas respeitam a janela de 24 horas`() {
        config.falhaTecnica.intervaloInicial = Duration.ofHours(6)
        val (service, client) = roteiro("EXCECAO")
        val task = service.registrar(requisicao)
        service.processarPendentes()
        relogio.agora = service.buscar(task.id).proximaTentativa
        service.processarPendentes()
        // Segunda espera seria 24 h depois da segunda falha (30 h da primeira): passa da janela.
        assertEquals(StatusConsultaErp.CONCLUIDA, service.buscar(task.id).status)
        assertEquals(2, client.consultas)
        assertEquals("ERRO_CONSULTA_ERP", service.buscar(task.id).resultado?.codigo)
    }

    @Test
    fun `resultado de negocio zera a contagem de falhas tecnicas`() {
        val (service, client) = roteiro("ERRO_CONSULTA_ERP", "ERRO_CONSULTA_ERP", "EM_ABERTO", "ERRO_CONSULTA_ERP", "PAGO")
        val task = service.registrar(requisicao)
        rodaAte(service, task.id, Duration.ofHours(96))
        val concluida = service.buscar(task.id)
        assertEquals("PAGO", concluida.resultado?.codigo)
        assertEquals(5, client.consultas)
        assertEquals(0, concluida.falhasTecnicas)
        assertNull(concluida.primeiraFalhaTecnicaEm)
    }

    @Test
    fun `reinicio - tarefa reservada por um executor que caiu e retomada depois que a reserva vence`() {
        val (antes, client) = roteiro("EM_ABERTO", "PAGO")
        val task = antes.registrar(requisicao)
        antes.processarPendentes()
        // Outro executor reservou a reconsulta e caiu sem gravar nada.
        relogio.agora = antes.buscar(task.id).proximaTentativa
        assertNotNull(repository.reservarProxima(relogio.agora, null))
        val depois = service(listOf(client))
        depois.processarPendentes()
        assertEquals(1, client.consultas, "reserva ainda valida")
        relogio.avanca(Duration.ofMinutes(11))
        depois.processarPendentes()
        assertEquals(StatusConsultaErp.CONCLUIDA, depois.buscar(task.id).status)
        assertEquals("PAGO", depois.buscar(task.id).resultado?.codigo)
        assertEquals(2, client.consultas)
        verify(soap, times(1)).executeActivity(any())
    }

    @Test
    fun `configuracao invalida de espera ou falha tecnica e recusada na subida`() {
        val invalidas = listOf<ConsultaErpConfiguration.() -> Unit>(
            { esperaPagamento.codigos = setOf("EM_ABERTO", "ERRO_CONSULTA_ERP") },
            { esperaPagamento.prazo = Duration.ofDays(31) },
            { esperaPagamento.intervalo = Duration.ofSeconds(30) },
            { falhaTecnica.janelaMaxima = Duration.ofHours(25) },
            { falhaTecnica.tentativas = -1 },
            { falhaTecnica.multiplicador = 0 },
        )
        for (ajuste in invalidas) {
            val c = ConsultaErpConfiguration().apply(ajuste)
            assertThrows<IllegalArgumentException> { c.validarReconsulta() }
        }
        ConsultaErpConfiguration().validarReconsulta()
    }

    // ---- Falha tecnica durante a espera de 96 h ----

    private fun comentarios(): List<String> {
        val captor = ArgumentCaptor.forClass(NewCommentRequestType::class.java)
        verify(soap, atLeastOnce()).newComment(captor.capture())
        return captor.allValues.map { it.text }
    }

    @Test
    fun `ERP fora do ar durante a espera nao finaliza e PAGO depois da volta vai ao sucesso`() {
        val (service, client) = roteiro("EM_ABERTO", "EXCECAO", "EXCECAO", "EXCECAO", "EXCECAO", "EXCECAO", "PAGO")
        val aceite = relogio.agora
        val task = service.registrar(requisicao)
        service.processarPendentes()
        val agendas = mutableListOf<Duration>()
        while (client.consultas < 6) {
            val proxima = service.buscar(task.id).proximaTentativa
            agendas += Duration.between(relogio.agora, proxima)
            relogio.agora = proxima
            service.processarPendentes()
            val aguardando = service.buscar(task.id)
            assertEquals(StatusConsultaErp.PENDENTE, aguardando.status, "falha ${client.consultas - 1} nao finaliza")
            assertNull(aguardando.resultado)
            assertEquals("EM_ABERTO", aguardando.ultimaConsulta?.codigo, "resultado de negocio preservado")
            assertEquals("ERRO_CONSULTA_ERP", aguardando.ultimaFalhaTecnica?.codigo)
            assertEquals(relogio.agora, aguardando.ultimaFalhaTecnicaEm)
        }
        // 3 h (espera), depois backoff tecnico 15 min, 1 h, e 4 h/16 h limitados a 3 h.
        assertEquals(listOf(Duration.ofHours(3), Duration.ofMinutes(15), Duration.ofHours(1), Duration.ofHours(3),
            Duration.ofHours(3)), agendas)
        assertEquals(5, service.buscar(task.id).falhasTecnicas, "mais que as 3 novas tentativas das primeiras consultas")
        verifyNoInteractions(soap)

        relogio.agora = service.buscar(task.id).proximaTentativa
        service.processarPendentes()
        val concluida = service.buscar(task.id)
        assertEquals(StatusConsultaErp.CONCLUIDA, concluida.status)
        assertEquals("PAGO", concluida.resultado?.codigo)
        assertEquals(0, concluida.falhasTecnicas)
        assertTrue(relogio.agora.isBefore(aceite.plus(Duration.ofHours(96))))
        assertEquals(BigInteger.valueOf(3), acoes().single().actionSequence)
        assertFalse(comentarios().single().contains("falharam tecnicamente"))
    }

    @Test
    fun `ERP fora do ar ate o fim do prazo entrega o ultimo resultado de negocio ao fluxo manual`() {
        val (service, client) = roteiro("NOTA_NAO_ENCONTRADA", "PAGO_PARCIAL", "EXCECAO")
        val aceite = relogio.agora
        val prazo = aceite.plus(Duration.ofHours(96))
        val task = service.registrar(requisicao)
        rodaAte(service, task.id, Duration.ofHours(100))
        val concluida = service.buscar(task.id)
        assertEquals(StatusConsultaErp.CONCLUIDA, concluida.status)
        assertEquals("PAGO_PARCIAL", concluida.resultado?.codigo, "nao ERRO_CONSULTA_ERP")
        assertEquals(false, concluida.resultado?.sucesso)
        assertEquals(prazo, concluida.ultimaFalhaTecnicaEm, "ultima tentativa exatamente no fim do prazo")
        assertEquals(aceite.plus(Duration.ofHours(3)), concluida.ultimaConsultaEm)
        assertTrue(concluida.falhasTecnicas > 3)
        // 0 h, 3 h e falhas: 6 h (+15 min), 6h15 (+1 h), 7h15 e depois a cada 3 h ate o prazo.
        assertEquals(2 + 3 + 30, client.consultas)
        assertEquals(BigInteger.valueOf(5), acoes().single().actionSequence)
        val comentario = comentarios().single()
        assertTrue(comentario.contains("PAGO_PARCIAL: msg PAGO_PARCIAL"), comentario)
        assertTrue(comentario.contains("${concluida.falhasTecnicas} consulta(s) mais recente(s) ao ERP falharam tecnicamente"), comentario)
        assertTrue(comentario.contains("ultima em $prazo"), comentario)
    }

    // ---- Entrega ao SE recusada: backoff, esgotamento e reativacao manual ----

    @Test
    fun `avanco recusado usa backoff, esgota em RETORNO_FALHOU e READY manual retoma sem novo comentario`() {
        val (service, client) = roteiro("VALOR_DIVERGENTE")
        `when`(soap.executeActivity(any())).thenReturn(
            ExecuteActivityResponseType().apply { status = "FAILURE"; detail = "Instancia cancelada" })
        val inicio = relogio.agora
        val task = service.registrar(requisicao)
        service.processarPendentes()
        val agendas = mutableListOf<Duration>()
        while (service.buscar(task.id).status == StatusConsultaErp.RETORNO_PENDENTE) {
            val proxima = service.buscar(task.id).proximaTentativa
            agendas += Duration.between(relogio.agora, proxima)
            relogio.agora = proxima
            service.processarPendentes()
        }
        assertEquals(listOf(1L, 4, 16, 64, 256, 360, 360, 360).map(Duration::ofMinutes), agendas)
        val falhou = service.buscar(task.id)
        assertEquals(StatusConsultaErp.RETORNO_FALHOU, falhou.status)
        assertEquals("VALOR_DIVERGENTE", falhou.resultado?.codigo, "resultado persistido")
        assertTrue(falhou.respostaRegistrada)
        assertEquals(9, falhou.falhasRetorno)
        assertEquals(inicio, falhou.primeiraFalhaRetornoEm)
        assertTrue(falhou.ultimoErro!!.contains("Instancia cancelada"), falhou.ultimoErro)
        assertNull(falhou.reserva)
        verify(queue).sincronizar(falhou)
        verify(soap, times(9)).executeActivity(any())

        // Esgotada: nem job, nem Run, nem dias depois chamam o SE.
        relogio.avanca(Duration.ofDays(3))
        service.processarPendentes()
        service.executar(task.id)
        verify(soap, times(9)).executeActivity(any())
        verify(queue, atLeast(2)).sincronizar(falhou)

        // Operador corrige no SE e volta a task para READY.
        `when`(soap.executeActivity(any())).thenReturn(ExecuteActivityResponseType().apply { status = "SUCCESS" })
        val reativada = service.reativarRetorno(task.id)!!
        assertEquals(StatusConsultaErp.RETORNO_PENDENTE, reativada.status)
        assertEquals(0, reativada.falhasRetorno)
        assertNull(service.reativarRetorno(task.id), "reativar de novo nao muda nada")
        service.executar(task.id)
        val concluida = service.buscar(task.id)
        assertEquals(StatusConsultaErp.CONCLUIDA, concluida.status)
        assertNull(concluida.ultimoErro)
        verify(soap, times(1)).newComment(any())
        verify(soap, times(10)).executeActivity(any())
        assertEquals(1, client.consultas, "ERP nao e reconsultado na entrega")
    }

    @Test
    fun `falha no comentario segue o mesmo limite e a reativacao reenvia o comentario nao confirmado`() {
        config.retornoSe.tentativas = 2
        `when`(soap.newComment(any())).thenReturn(NewCommentResponseType().apply { status = "FAILURE"; detail = "Sem permissao" })
        val (service, _) = roteiro("EM_ABERTO")
        config.esperaPagamento.prazo = Duration.ZERO
        val task = service.registrar(requisicao)
        service.processarPendentes()
        while (service.buscar(task.id).status == StatusConsultaErp.RETORNO_PENDENTE) {
            relogio.agora = service.buscar(task.id).proximaTentativa
            service.processarPendentes()
        }
        val falhou = service.buscar(task.id)
        assertEquals(StatusConsultaErp.RETORNO_FALHOU, falhou.status)
        assertFalse(falhou.respostaRegistrada)
        verify(soap, times(3)).newComment(any())
        verify(soap, never()).executeActivity(any())

        `when`(soap.newComment(any())).thenReturn(NewCommentResponseType().apply { status = "SUCCESS" })
        service.reativarRetorno(task.id)
        service.executar(task.id)
        assertEquals(StatusConsultaErp.CONCLUIDA, service.buscar(task.id).status)
        verify(soap, times(4)).newComment(any())
        verify(soap, times(1)).executeActivity(any())
    }

    @Test
    fun `configuracao invalida do retorno ao SE e recusada na subida`() {
        val invalidas = listOf<ConsultaErpConfiguration.() -> Unit>(
            { retornoSe.intervaloInicial = Duration.ofSeconds(10) },
            { retornoSe.intervaloMaximo = Duration.ofSeconds(30) },
            { retornoSe.intervaloMaximo = Duration.ofDays(2) },
            { retornoSe.multiplicador = 0 },
            { retornoSe.tentativas = -1 },
            { retornoSe.janelaMaxima = Duration.ofDays(8) },
        )
        for (ajuste in invalidas) {
            assertThrows<IllegalArgumentException> { ConsultaErpConfiguration().apply(ajuste).validarReconsulta() }
        }
    }

    // ---- P3-1: resultado sem chaves de dados ----

    @Test
    fun `resultado sem dados preenche campos mapeados com vazio e conclui a entrega`() {
        config.camposRetorno = mapOf("codigo" to "erpcodigo", "parcelaValor" to "erpvalor", "pagamentoData" to "erpdata")
        val service = service()
        val task = service.registrar(requisicao)
        service.processarPendentes()
        val concluida = service.buscar(task.id)
        assertEquals("CNPJ_NAO_MAPEADO", concluida.resultado?.codigo)
        assertTrue(concluida.resultado!!.dados.isEmpty())
        assertEquals(StatusConsultaErp.CONCLUIDA, concluida.status)
        val formulario = ArgumentCaptor.forClass(EditEntityRecordRequestType::class.java)
        verify(soap).editEntityRecord(formulario.capture())
        assertEquals(mapOf("erpcodigo" to "CNPJ_NAO_MAPEADO", "erpvalor" to "", "erpdata" to ""),
            formulario.value.entityAttributeList.associate { it.entityAttributeID to it.entityAttributeValue })
    }

    @Test
    fun `falha tecnica gerada pelo servico tambem entrega com campos de dados mapeados`() {
        config.camposRetorno = mapOf("parcelaValor" to "erpvalor")
        config.falhaTecnica.tentativas = 0
        config.cnpjs = mapOf(requisicao.cpfCnpjTomador to Erp.SAP)
        val semDados = object : ConsultaErpClient {
            override val erp = Erp.SAP
            override fun consultar(requisicao: RegistrarConsultaErpRequest): ResultadoConsultaErp = error("timeout")
        }
        val service = service(listOf(semDados))
        val task = service.registrar(requisicao)
        service.processarPendentes()
        assertEquals("ERRO_CONSULTA_ERP", service.buscar(task.id).resultado?.codigo)
        assertEquals(StatusConsultaErp.CONCLUIDA, service.buscar(task.id).status)
    }
}
