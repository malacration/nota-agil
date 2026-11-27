package br.andrew.nota.agil.services

import br.andrew.nota.agil.erp.notas.FiltroNotasErp
import br.andrew.nota.agil.erp.notas.NotaEntradaErp
import br.andrew.nota.agil.erp.notas.NotasEntradaErpClient
import br.andrew.nota.agil.erp.notas.SituacaoNotasErp
import br.andrew.nota.agil.model.Company
import br.andrew.nota.agil.model.Duplicata
import br.andrew.nota.agil.model.TipoDuplicata
import br.andrew.nota.agil.model.erp.Erp
import br.andrew.nota.agil.model.tasks.OrigemNota
import br.andrew.nota.agil.model.tasks.ReferenciaNotaErp
import br.andrew.nota.agil.model.tasks.Task
import br.andrew.nota.agil.model.tasks.TaskStatus
import br.andrew.nota.agil.model.tasks.TaskTypes
import br.andrew.nota.agil.repository.CompanyRepository
import br.andrew.nota.agil.repository.TaskRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.anyIterable
import org.mockito.ArgumentMatchers.anyString
import br.andrew.nota.agil.support.anyValue
import org.springframework.dao.DuplicateKeyException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.math.BigDecimal
import java.util.Date
import java.util.Optional

/** Dados ficticios: CNPJs, chaves e valores nao correspondem a registros reais. */
class NotasErpServiceTest {

    /** Fonte de notas em memoria (sem mock: a interface tem metodos com valor padrao). */
    private class FonteFake(
        override val erp: Erp,
        var situacao: SituacaoNotasErp = SituacaoNotasErp(true, true),
        var notas: List<NotaEntradaErp> = emptyList(),
    ) : NotasEntradaErpClient {
        override val nome = erp.name
        var filtros = mutableListOf<FiltroNotasErp>()
        var buscados = mutableListOf<List<String>>()
        override fun situacao() = situacao
        override fun motivoEmpresaIndisponivel(empresa: Company) =
            if (erp == Erp.DEALER && empresa.codigoDealer == null) "Empresa sem codigo no Dealer" else null
        override fun listar(empresa: Company, filtro: FiltroNotasErp) = notas.also { filtros += filtro }
        override fun buscar(empresa: Company, ids: List<String>) = notas.filter { it.id in ids }.also { buscados += ids }
        override fun filiais() = emptyList<br.andrew.nota.agil.erp.notas.FilialErp>()
    }

    private val sap = FonteFake(Erp.SAP)
    private val dealer = FonteFake(Erp.DEALER)
    private val empresas = mock(CompanyRepository::class.java)
    private val tasks = mock(TaskRepository::class.java)
    private val trava = TravaNotasMemoria()
    private val service = NotasErpService(listOf(sap, dealer), empresas, tasks, trava)
    private val livre: (Company) -> Unit = {}

    private val cnpj = "12345678000190"
    private val fornecedor = "98765432000100"
    private val chave = "11261198765432000100550010000001231000000001"

    private fun nota(id: String, erp: Erp = Erp.SAP, tipo: TipoDuplicata = TipoDuplicata.Nfe, numero: Long? = 123,
                     chaveAcesso: String? = chave, cpfCnpj: String? = fornecedor, problema: String? = null) = NotaEntradaErp(
        id = id, erp = erp, numero = numero, serie = "1", tipo = tipo, chaveAcesso = chaveAcesso,
        dataEmissao = "2026-11-10", dataLancamento = "2026-11-11", nomeFornecedor = "Fornecedor X",
        cpfCnpjFornecedor = cpfCnpj, valorTotal = BigDecimal("100.00"), valorBruto = BigDecimal("110.00"),
        valorLiquido = BigDecimal("95.00"), anexosPdf = 1, problema = problema,
    )

    private fun empresa(codigoDealer: Int? = 7) {
        `when`(empresas.findById(cnpj)).thenReturn(Optional.of(Company(cnpj, "Empresa X", codigoDealer = codigoDealer)))
    }

    private fun taskExistente(id: String, tipo: TipoDuplicata, numero: String = "123", tomador: String = cnpj, origem: OrigemNota? = null,
                              serie: String? = null, prestador: String = fornecedor, chaveAcesso: String? = null) =
        Task(id, "t", TaskStatus.FINISHED, TaskTypes.CreateTask, Duplicata(Date(), prestador, "F", tomador, "E",
            BigDecimal.ONE, BigDecimal.ONE, numero, tipo, chaveAcesso = chaveAcesso, serie = serie)).also { it.origem = origem }

    @Test
    fun `erps informam disponibilidade e motivo`() {
        dealer.situacao = SituacaoNotasErp(habilitado = false, configurado = true)
        sap.situacao = SituacaoNotasErp(habilitado = true, configurado = false)
        val erps = service.erps().associateBy { it.erp }
        assertFalse(erps.getValue(Erp.DEALER).disponivel)
        assertEquals("Desativado por variavel de ambiente", erps.getValue(Erp.DEALER).motivo)
        assertFalse(erps.getValue(Erp.SAP).disponivel)
        assertEquals("Variaveis de conexao ausentes", erps.getValue(Erp.SAP).motivo)
    }

    @Test
    fun `ERP indisponivel recusa listagem e importacao com 503`() {
        empresa()
        dealer.situacao = SituacaoNotasErp(habilitado = false, configurado = true)
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, assertThrows<ResponseStatusException> {
            service.listar(Erp.DEALER, cnpj, "2026-11-01", "2026-11-02", null, null, livre)
        }.statusCode)
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, assertThrows<ResponseStatusException> {
            service.importar(Erp.DEALER, cnpj, listOf("nf1"), livre)
        }.statusCode)
    }

    @Test
    fun `Dealer exige codigo da empresa`() {
        empresa(codigoDealer = null)
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows<ResponseStatusException> {
            service.listar(Erp.DEALER, cnpj, "2026-11-01", "2026-11-02", null, null, livre)
        }.statusCode)
        `when`(empresas.findAll()).thenReturn(listOf(Company(cnpj, "Empresa X"), Company("all", "all")))
        assertEquals("Empresa sem codigo no Dealer", service.empresas(Erp.DEALER) { true }.single().motivoIndisponivel)
        assertNull(service.empresas(Erp.SAP) { true }.single().motivoIndisponivel)
    }

    @Test
    fun `valida periodo, fornecedor e numero antes de ir ao ERP`() {
        empresa()
        for ((ini, fim, forn, num) in listOf(
            listOf("2026-11-10", "2026-11-01", null, null),
            listOf("2026-10-01", "2026-11-01", null, null),
            listOf("01/11/2026", "2026-11-02", null, null),
            listOf("2026-11-01", "2026-11-02", "123", null),
            listOf("2026-11-01", "2026-11-02", null, "12A"),
        )) {
            assertEquals(HttpStatus.BAD_REQUEST, assertThrows<ResponseStatusException> {
                service.listar(Erp.SAP, cnpj, ini, fim, forn, num, livre)
            }.statusCode)
        }
        assertTrue(sap.filtros.isEmpty())
    }

    @Test
    fun `ids de task por ERP`() {
        assertEquals("$chave-CreateTask", NotasErpService.idCreateTask(nota("1")))
        assertEquals("sap-2-CreateTask", NotasErpService.idCreateTask(nota("2", tipo = TipoDuplicata.Nfse)))
        assertEquals("dealer-nf9-CreateTask", NotasErpService.idCreateTask(nota("nf9", Erp.DEALER, TipoDuplicata.NaoDetectado, chaveAcesso = null)))
    }

    @Test
    fun `task criada a partir da nota do Dealer`() {
        val task = service.criarTask(nota("nf9", Erp.DEALER, TipoDuplicata.NaoDetectado, chaveAcesso = null), Company(cnpj, "Empresa X"))
        assertEquals("dealer-nf9-CreateTask", task.id)
        assertEquals(OrigemNota.DEALER, task.origem)
        assertEquals(ReferenciaNotaErp(Erp.DEALER, "nf9", cnpj, 1), task.notaErp)
        val d = task.duplicata!!
        assertEquals(TipoDuplicata.NaoDetectado, d.tipo)
        assertNull(d.chaveAcesso)
        assertEquals(BigDecimal("110.00"), d.valorBruto)
        assertEquals(cnpj, d.cpfCnpjTomador)
    }

    @Test
    fun `listagem marca nota que ja tem task, inclusive do Qive para nota do Dealer sem tipo`() {
        empresa()
        dealer.notas = listOf(
            nota("nf1", Erp.DEALER, TipoDuplicata.NaoDetectado, numero = 123, chaveAcesso = null),
            nota("nf2", Erp.DEALER, TipoDuplicata.NaoDetectado, numero = 456, chaveAcesso = null),
            nota("t3", Erp.DEALER, TipoDuplicata.NaoDetectado, numero = null, chaveAcesso = null, problema = "Titulo sem nota fiscal vinculada no Dealer"),
        )
        `when`(tasks.findAllById(anyIterable())).thenReturn(emptyList())
        `when`(tasks.findAllByDuplicataCpfCnpjPrestadorRegexAndDuplicataNumeroRegex(NumeroNota.regexDocumentos(listOf(fornecedor))!!, NumeroNota.regexCandidatos(listOf("123", "456"))!!))
            .thenReturn(listOf(taskExistente("qive-CreateTask", TipoDuplicata.Nfe, numero = "000123"),
                taskExistente("outra-empresa", TipoDuplicata.Nfe, numero = "456", tomador = "11111111000111")))

        val itens = service.listar(Erp.DEALER, cnpj, "2026-11-01", "2026-11-30", null, null, livre).notas

        assertEquals("qive-CreateTask", itens[0].task?.id)
        assertEquals(OrigemNota.QIVE, itens[0].task?.origem)
        assertNull(itens[1].task)
        assertNull(itens[1].problema)
        assertEquals("Titulo sem nota fiscal vinculada no Dealer", itens[2].problema)
        assertEquals(FiltroNotasErp("2026-11-01", "2026-11-30", null, null), dealer.filtros.single())
    }

    @Test
    fun `importacao relida no ERP cria so o que pode e informa o resto`() {
        empresa()
        sap.notas = listOf(
            nota("1", numero = 1, chaveAcesso = "11261198765432000100550010000000011000000001"),
            nota("2"),
            nota("3", numero = 3, chaveAcesso = null, cpfCnpj = null),
        )
        `when`(tasks.findAllById(anyIterable())).thenReturn(listOf(taskExistente("$chave-CreateTask", TipoDuplicata.Nfe)))

        val r = service.importar(Erp.SAP, "12.345.678/0001-90", listOf("1", "2", "3", "4", "1"), livre).resultados.associateBy { it.id }

        assertEquals(listOf("1", "2", "3", "4"), sap.buscados.single())
        assertEquals(StatusImportacao.CRIADA, r["1"]?.status)
        assertEquals(StatusImportacao.JA_EXISTE, r["2"]?.status)
        assertEquals(StatusImportacao.ERRO, r["3"]?.status)
        assertEquals(StatusImportacao.NAO_ENCONTRADA, r["4"]?.status)
        val inserida = ArgumentCaptor.forClass(Task::class.java)
        verify(tasks).insert(inserida.capture())
        assertEquals(OrigemNota.SAP, inserida.value.origem)
    }

    @Test
    fun `importacao exige empresa cadastrada e de 1 a 100 notas`() {
        `when`(empresas.findById(cnpj)).thenReturn(Optional.empty())
        assertEquals(HttpStatus.NOT_FOUND, assertThrows<ResponseStatusException> { service.importar(Erp.SAP, cnpj, listOf("1"), livre) }.statusCode)
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows<ResponseStatusException> { service.importar(Erp.SAP, cnpj, emptyList(), livre) }.statusCode)
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows<ResponseStatusException> {
            service.importar(Erp.SAP, cnpj, (1..101).map(Int::toString), livre)
        }.statusCode)
        verify(tasks, never()).insert(org.mockito.ArgumentMatchers.any(Task::class.java))
    }

    @Test
    fun `jobs do Qive reconhecem nota ja importada de ERP`() {
        val duplicata = Duplicata(Date(), fornecedor, "F", cnpj, "E", BigDecimal.ONE, BigDecimal.ONE, "123", TipoDuplicata.Nfe)
        `when`(tasks.findAllByDuplicataCpfCnpjPrestadorRegexAndDuplicataNumeroRegex(NumeroNota.regexDocumentos(listOf(fornecedor))!!, NumeroNota.regexCandidatos(listOf("123"))!!))
            .thenReturn(listOf(taskExistente("dealer-nf1-CreateTask", TipoDuplicata.NaoDetectado, origem = OrigemNota.DEALER)))
        assertTrue(NotasErpService.existeCreateTaskEquivalente(tasks, duplicata))

        `when`(tasks.findAllByDuplicataCpfCnpjPrestadorRegexAndDuplicataNumeroRegex(NumeroNota.regexDocumentos(listOf(fornecedor))!!, NumeroNota.regexCandidatos(listOf("123"))!!))
            .thenReturn(listOf(
                taskExistente("qive-CreateTask", TipoDuplicata.Nfe, origem = null),
                taskExistente("sap-9-CreateTask", TipoDuplicata.Nfe, tomador = "11111111000111", origem = OrigemNota.SAP),
                taskExistente("sap-8-CreateTask", TipoDuplicata.Cte, origem = OrigemNota.SAP),
            ))
        assertFalse(NotasErpService.existeCreateTaskEquivalente(tasks, duplicata))
    }

    // ------------------------------------------------------------ autorizacao, numeros e concorrencia

    @Test
    fun `sem a trava, o indice de identidade barra a segunda task da mesma nota (ERP x Qive e ERP x ERP)`() {
        empresa()
        val banco = BancoTasks(tasks)
        val semTrava = NotasErpService(listOf(sap, dealer), empresas, tasks, SemTrava)
        sap.notas = listOf(nota("7", tipo = TipoDuplicata.Nfe, numero = 123, chaveAcesso = null))
        // As checagens nao veem nada (corrida): so o banco pode impedir a duplicidade.
        banco.esconderNaChecagem = true
        val qive = Task("qive-x-CreateTask", "t", TaskStatus.READY, TaskTypes.CreateTask,
            Duplicata(Date(), fornecedor, "F", cnpj, "E", BigDecimal.ONE, BigDecimal.ONE, "000123", TipoDuplicata.Nfe, serie = "001"))
        assertTrue(NotasErpService.criarTaskDoQive(tasks, SemTrava, qive))
        assertEquals(StatusImportacao.JA_EXISTE, semTrava.importar(Erp.SAP, cnpj, listOf("7"), livre).resultados.single().status)
        assertEquals(listOf("qive-x-CreateTask"), banco.ids())

        banco.apagar("qive-x-CreateTask")
        assertEquals(StatusImportacao.CRIADA, semTrava.importar(Erp.SAP, cnpj, listOf("7"), livre).resultados.single().status)
        dealer.notas = emptyList()
        val outroId = semTrava.criarTask(nota("7b", tipo = TipoDuplicata.Nfe, numero = 123, chaveAcesso = null), Company(cnpj, "Empresa X"))
        assertThrows<DuplicateKeyException> { tasks.insert(outroId) }
        assertEquals(listOf("sap-7-CreateTask"), banco.ids())
    }

    @Test
    fun `identidade da nota ignora mascara e zeros e separa serie e tipo`() {
        val base = Duplicata(Date(), "98.765.432/0001-00", "F", "12.345.678/0001-90", "E", BigDecimal.ONE, BigDecimal.ONE, "000123",
            TipoDuplicata.Nfe, serie = "001")
        assertEquals("$fornecedor|$cnpj|Nfe|123|1", NotasErpService.identidadeNota(base))
        val serie2 = Duplicata(Date(), fornecedor, "F", cnpj, "E", BigDecimal.ONE, BigDecimal.ONE, "123", TipoDuplicata.Nfe, serie = "2")
        assertEquals("$fornecedor|$cnpj|Nfe|123|2", NotasErpService.identidadeNota(serie2))
        val nfse = Duplicata(Date(), fornecedor, "F", cnpj, "E", BigDecimal.ONE, BigDecimal.ONE, "2600000001234", TipoDuplicata.Nfse, serie = "A1")
        assertEquals("$fornecedor|$cnpj|Nfse|2600000001234|-", NotasErpService.identidadeNota(nfse))
        val semNumero = Duplicata(Date(), fornecedor, "F", cnpj, "E", BigDecimal.ONE, BigDecimal.ONE, "000", TipoDuplicata.Nfe)
        assertNull(NotasErpService.identidadeNota(semNumero))
    }

    @Test
    fun `task do Qive com CNPJ mascarado e numero com separador e achada nos dois sentidos`() {
        empresa()
        val banco = BancoTasks(tasks)
        banco.salvar(taskExistente("qive-CreateTask", TipoDuplicata.Nfe, numero = "1.234", prestador = "98.765.432/0001-00"))
        sap.notas = listOf(nota("1", tipo = TipoDuplicata.Nfe, numero = 1234, chaveAcesso = null, cpfCnpj = fornecedor))
        assertEquals("qive-CreateTask", service.listar(Erp.SAP, cnpj, "2026-11-01", "2026-11-30", null, null, livre).notas.single().task?.id)

        banco.apagar("qive-CreateTask")
        banco.salvar(taskExistente("sap-1-CreateTask", TipoDuplicata.Nfe, numero = "1234", prestador = "98.765.432/0001-00", origem = OrigemNota.SAP))
        val qive = Duplicata(Date(), fornecedor, "F", cnpj, "E", BigDecimal.ONE, BigDecimal.ONE, "01234", TipoDuplicata.Nfe)
        assertTrue(NotasErpService.existeCreateTaskEquivalente(tasks, qive))
    }

    @Test
    fun `task antiga com prestador em mascara parcial ou com espacos e achada e nao gera segunda task`() {
        empresa()
        val banco = BancoTasks(tasks)
        banco.salvar(taskExistente("qive-antiga-CreateTask", TipoDuplicata.Nfe, numero = "123", prestador = " 98.765.432/0001-00 "))
        sap.notas = listOf(nota("1", tipo = TipoDuplicata.Nfe, numero = 123, chaveAcesso = null))
        val r = service.importar(Erp.SAP, cnpj, listOf("1"), livre).resultados.single()
        assertEquals(StatusImportacao.JA_EXISTE, r.status)
        assertEquals("qive-antiga-CreateTask", r.taskId)

        banco.apagar("qive-antiga-CreateTask")
        banco.salvar(taskExistente("sap-1-CreateTask", TipoDuplicata.Nfe, numero = "123", prestador = "98765432/000100", origem = OrigemNota.SAP))
        val qive = Duplicata(Date(), fornecedor, "F", cnpj, "E", BigDecimal.ONE, BigDecimal.ONE, "123", TipoDuplicata.Nfe)
        assertTrue(NotasErpService.existeCreateTaskEquivalente(tasks, qive))
        // Outro prestador com os mesmos digitos em outra ordem nao casa.
        banco.apagar("sap-1-CreateTask")
        banco.salvar(taskExistente("sap-2-CreateTask", TipoDuplicata.Nfe, numero = "123", prestador = "98.765.432/0001-01", origem = OrigemNota.SAP))
        assertFalse(NotasErpService.existeCreateTaskEquivalente(tasks, qive))
    }

    @Test
    fun `NFe de mesmo numero com serie ou chave diferente nao e a mesma nota`() {
        empresa()
        val banco = BancoTasks(tasks)
        banco.salvar(taskExistente("qive-s2-CreateTask", TipoDuplicata.Nfe, numero = "123", serie = "2"))
        sap.notas = listOf(
            nota("1", tipo = TipoDuplicata.Nfe, numero = 123, chaveAcesso = null),                      // serie 1
            nota("2", Erp.SAP, TipoDuplicata.NaoDetectado, numero = 123, chaveAcesso = null).copy(serie = null),
        )
        val itens = service.listar(Erp.SAP, cnpj, "2026-11-01", "2026-11-30", null, null, livre).notas.associateBy { it.nota.id }
        assertNull(itens["1"]?.task, "serie 1 x serie 2")
        assertEquals("qive-s2-CreateTask", itens["2"]?.task?.id, "serie desconhecida nao separa")

        // Job do Qive: task SAP serie 1 nao bloqueia NFe serie 2; chaves de 44 diferentes tambem nao.
        banco.salvar(taskExistente("sap-1-CreateTask", TipoDuplicata.Nfe, numero = "123", serie = "1", origem = OrigemNota.SAP, chaveAcesso = chave))
        val serie2 = Duplicata(Date(), fornecedor, "F", cnpj, "E", BigDecimal.ONE, BigDecimal.ONE, "123", TipoDuplicata.Nfe, serie = "2")
        assertFalse(NotasErpService.existeCreateTaskEquivalente(tasks, serie2))
        val outraChave = "11261198765432000100550010000001231000000009"
        val comOutraChave = Duplicata(Date(), fornecedor, "F", cnpj, "E", BigDecimal.ONE, BigDecimal.ONE, "123", TipoDuplicata.Nfe, chaveAcesso = outraChave)
        assertFalse(NotasErpService.existeCreateTaskEquivalente(tasks, comOutraChave))
        val mesmaChave = Duplicata(Date(), fornecedor, "F", cnpj, "E", BigDecimal.ONE, BigDecimal.ONE, "123", TipoDuplicata.Nfe, chaveAcesso = chave)
        assertTrue(NotasErpService.existeCreateTaskEquivalente(tasks, mesmaChave))
    }

    @Test
    fun `listagem e importacao conferem a permissao na empresa cadastrada antes de ir ao ERP`() {
        empresa(codigoDealer = 2)
        val negar: (Company) -> Unit = { e ->
            if (e.codigoDealer != 1) throw ResponseStatusException(HttpStatus.FORBIDDEN, "Filial nao liberada")
        }
        assertEquals(HttpStatus.FORBIDDEN, assertThrows<ResponseStatusException> {
            service.listar(Erp.DEALER, cnpj, "2026-11-01", "2026-11-02", null, null, negar)
        }.statusCode)
        assertEquals(HttpStatus.FORBIDDEN, assertThrows<ResponseStatusException> {
            service.importar(Erp.DEALER, cnpj, listOf("nf1"), negar)
        }.statusCode)
        assertTrue(dealer.filtros.isEmpty())
        assertTrue(dealer.buscados.isEmpty())
        `when`(empresas.findAll()).thenReturn(listOf(Company(cnpj, "Empresa X", codigoDealer = 2), Company("22222222000122", "Y", codigoDealer = 1)))
        assertEquals(listOf("22222222000122"), service.empresas(Erp.DEALER) { it.codigoDealer == 1 }.map { it.cnpj })
    }

    @Test
    fun `nota do SAP com numero curto acha task do Qive com zeros a esquerda e NFS-e longa`() {
        empresa()
        val banco = BancoTasks(tasks)
        banco.salvar(taskExistente("qive-nfe-CreateTask", TipoDuplicata.Nfe, numero = "000123"))
        banco.salvar(taskExistente("qive-nfse-CreateTask", TipoDuplicata.Nfse, numero = "2600000001234"))
        sap.notas = listOf(
            nota("1", tipo = TipoDuplicata.Nfe, numero = 123, chaveAcesso = null),
            nota("2", tipo = TipoDuplicata.Nfse, numero = 1234, chaveAcesso = null),
            nota("3", tipo = TipoDuplicata.Nfe, numero = 1234, chaveAcesso = null),
        )
        val itens = service.listar(Erp.SAP, cnpj, "2026-11-01", "2026-11-30", null, null, livre).notas.associateBy { it.nota.id }
        assertEquals("qive-nfe-CreateTask", itens["1"]?.task?.id)
        assertEquals("qive-nfse-CreateTask", itens["2"]?.task?.id)
        assertNull(itens["3"]?.task, "9 ultimos so valem para NFS-e")
    }

    @Test
    fun `job do Qive com numero longo acha NFS-e importada do SAP com 9 digitos e vice-versa`() {
        val banco = BancoTasks(tasks)
        banco.salvar(taskExistente("sap-5-CreateTask", TipoDuplicata.Nfse, numero = "1234", origem = OrigemNota.SAP))
        val longa = Duplicata(Date(), fornecedor, "F", cnpj, "E", BigDecimal.ONE, BigDecimal.ONE, "2600000001234", TipoDuplicata.Nfse)
        assertTrue(NotasErpService.existeCreateTaskEquivalente(tasks, longa))
        val outraLonga = Duplicata(Date(), fornecedor, "F", cnpj, "E", BigDecimal.ONE, BigDecimal.ONE, "2500000001234", TipoDuplicata.Nfse)
        banco.salvar(taskExistente("sap-6-CreateTask", TipoDuplicata.Nfse, numero = "2600000001234", origem = OrigemNota.SAP))
        banco.apagar("sap-5-CreateTask")
        assertFalse(NotasErpService.existeCreateTaskEquivalente(tasks, outraLonga), "dois longos com o mesmo sufixo")
        val curta = Duplicata(Date(), fornecedor, "F", cnpj, "E", BigDecimal.ONE, BigDecimal.ONE, "000001234", TipoDuplicata.Nfse)
        assertTrue(NotasErpService.existeCreateTaskEquivalente(tasks, curta))
    }

    @Test
    fun `duas notas equivalentes no mesmo lote criam uma task so`() {
        empresa()
        val banco = BancoTasks(tasks)
        sap.notas = listOf(
            nota("5", tipo = TipoDuplicata.Nfse, numero = 1234, chaveAcesso = null),
            nota("6", tipo = TipoDuplicata.Nfse, numero = 2600000001234, chaveAcesso = null),
        )
        val r = service.importar(Erp.SAP, cnpj, listOf("5", "6"), livre).resultados.associateBy { it.id }
        assertEquals(StatusImportacao.CRIADA, r["5"]?.status)
        assertEquals(StatusImportacao.JA_EXISTE, r["6"]?.status)
        assertEquals(listOf("sap-5-CreateTask"), banco.ids())
    }

    @Test
    fun `importacao de ERP e job do Qive concorrentes com ids diferentes criam uma task so`() {
        empresa()
        val banco = BancoTasks(tasks)
        sap.notas = listOf(nota("5", tipo = TipoDuplicata.Nfse, numero = 1234, chaveAcesso = null))
        val qive = Task("qive-nfse-CreateTask", "t", TaskStatus.READY, TaskTypes.CreateTask,
            Duplicata(Date(), fornecedor, "F", cnpj, "E", BigDecimal.ONE, BigDecimal.ONE, "2600000001234", TipoDuplicata.Nfse))
        // A primeira consulta de candidatas (dentro da trava) segura a thread ate o job do Qive ter tentado.
        banco.segurarPrimeiraConsultaDaTrava(trava)

        val executor = Executors.newFixedThreadPool(2)
        try {
            val erp = executor.submit<ImportacaoNotasErp> { service.importar(Erp.SAP, cnpj, listOf("5"), livre) }
            assertTrue(banco.consultaSegura.await(5, TimeUnit.SECONDS))
            val job = executor.submit<Boolean> { NotasErpService.criarTaskDoQive(tasks, trava, qive) }
            // Sem a trava o job consultaria agora, nao veria a task do ERP e inseriria a sua.
            assertTrue(trava.esperando("nota|$fornecedor|$cnpj|1234", 5, TimeUnit.SECONDS))
            banco.liberar.countDown()
            assertEquals(StatusImportacao.CRIADA, erp.get(5, TimeUnit.SECONDS).resultados.single().status)
            assertFalse(job.get(5, TimeUnit.SECONDS))
        } finally {
            executor.shutdownNow()
        }
        assertEquals(listOf("sap-5-CreateTask"), banco.ids())
    }

    @Test
    fun `job do Qive com o mesmo id nunca sobrescreve a task importada do ERP`() {
        empresa()
        val banco = BancoTasks(tasks)
        sap.notas = listOf(nota("1"))
        assertEquals(StatusImportacao.CRIADA, service.importar(Erp.SAP, cnpj, listOf("1"), livre).resultados.single().status)
        val qive = Task("$chave-CreateTask", "t", TaskStatus.READY, TaskTypes.CreateTask,
            Duplicata(Date(), fornecedor, "F", cnpj, "E", BigDecimal.ONE, BigDecimal.ONE, "123", TipoDuplicata.Nfe, chaveAcesso = chave))
        assertFalse(NotasErpService.criarTaskDoQive(tasks, trava, qive))

        // Corrida: a checagem nao viu a task (criada entre a checagem e o insert) -> insert falha, nada muda.
        banco.esconderNaChecagem = true
        assertFalse(NotasErpService.criarTaskDoQive(tasks, trava, qive))
        val gravada = banco.get("$chave-CreateTask")!!
        assertEquals(OrigemNota.SAP, gravada.origem)
        assertEquals(ReferenciaNotaErp(Erp.SAP, "1", cnpj, 1), gravada.notaErp)
        verify(tasks, never()).save(org.mockito.ArgumentMatchers.any(Task::class.java))
    }

    /** Trava que nao trava: simula a trava perdida (lease vencido, outro dono). */
    private object SemTrava : TravaNotas {
        override fun <T> comTrava(chave: String, bloco: () -> T): T = bloco()
    }

    /** Trava em memoria (mesma semantica da TravaNotasMongo dentro de um processo). */
    private class TravaNotasMemoria : TravaNotas {
        private val travas = ConcurrentHashMap<String, ReentrantLock>()
        override fun <T> comTrava(chave: String, bloco: () -> T): T {
            val lock = travas.computeIfAbsent(chave) { ReentrantLock() }
            lock.lock()
            try { return bloco() } finally { lock.unlock() }
        }
        fun travadaPelaThreadAtual() = travas.values.any { it.isHeldByCurrentThread }
        fun esperando(chave: String, tempo: Long, unidade: TimeUnit): Boolean {
            val limite = System.nanoTime() + unidade.toNanos(tempo)
            while (System.nanoTime() < limite) {
                if (travas[chave]?.hasQueuedThreads() == true) return true
                Thread.sleep(5)
            }
            return false
        }
    }

    /** TaskRepository em memoria sobre o mock: insert com id unico e busca por prestador + regex do numero. */
    private class BancoTasks(tasks: TaskRepository) {
        private val dados = ConcurrentHashMap<String, Task>()
        val consultaSegura = CountDownLatch(1)
        val liberar = CountDownLatch(1)
        @Volatile private var segurar: TravaNotasMemoria? = null
        @Volatile var esconderNaChecagem = false

        init {
            `when`(tasks.insert(org.mockito.ArgumentMatchers.any(Task::class.java))).thenAnswer {
                val t = it.getArgument<Task>(0)
                synchronized(dados) {
                    // Simula o indice unico esparso em identidadeNota.
                    if (t.identidadeNota != null && dados.values.any { o -> o.identidadeNota == t.identidadeNota })
                        throw DuplicateKeyException("identidadeNota duplicada")
                    if (dados.putIfAbsent(t.id, t) != null) throw DuplicateKeyException("id duplicado")
                }
                t
            }
            `when`(tasks.existsById(anyString())).thenAnswer { !esconderNaChecagem && dados.containsKey(it.getArgument<String>(0)) }
            `when`(tasks.findAllById(anyIterable())).thenAnswer { inv ->
                if (esconderNaChecagem) emptyList() else inv.getArgument<Iterable<String>>(0).mapNotNull { dados[it] }
            }
            `when`(tasks.findAllByDuplicataCpfCnpjPrestadorRegexAndDuplicataNumeroRegex(anyString(), anyString())).thenAnswer { inv ->
                val prestador = Regex(inv.getArgument<String>(0))
                val regex = Regex(inv.getArgument<String>(1))
                val achadas = dados.values.filter { t ->
                    !esconderNaChecagem && prestador.matches(t.duplicata!!.cpfCnpjPrestador) && regex.matches(t.duplicata.numero)
                }
                if (segurar?.travadaPelaThreadAtual() == true && consultaSegura.count > 0L) {
                    consultaSegura.countDown()
                    liberar.await(5, TimeUnit.SECONDS)
                }
                achadas
            }
        }

        /** A primeira consulta feita DENTRO da trava fica parada ate [liberar]. */
        fun segurarPrimeiraConsultaDaTrava(trava: TravaNotasMemoria) { segurar = trava }

        fun salvar(t: Task) { dados[t.id] = t }
        fun apagar(id: String) { dados.remove(id) }
        fun get(id: String) = dados[id]
        fun ids() = dados.keys.sorted()
    }
}
