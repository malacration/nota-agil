package br.andrew.nota.agil.repository

import br.andrew.nota.agil.model.erp.*
import com.mongodb.client.MongoClients
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import br.andrew.nota.agil.services.ConsultaErpTaskQueue
import br.andrew.nota.agil.model.tasks.*
import br.andrew.nota.agil.controllers.TasksController
import br.andrew.nota.agil.services.TaskService
import br.andrew.nota.agil.qive.interfaces.QiveApiClient
import org.mockito.Mockito.mock

/** Usa apenas um banco temporario exclusivo, sem Spring e sem acesso ao SOAP/ERP. */
@EnabledIfEnvironmentVariable(named = "NOTA_AGIL_MONGO_IT_URI", matches = ".+")
class ConsultaErpRepositoryIntegrationTest {
    private val client = MongoClients.create(System.getenv("NOTA_AGIL_MONGO_IT_URI"))
    private val databaseName = "nota_agil_erp_it_${UUID.randomUUID().toString().replace("-", "")}"
    private val mongo = MongoTemplate(client, databaseName)
    private val repository = ConsultaErpRepository(mongo)
    private val task = ConsultaErpTask(
        "task-1", RegistrarConsultaErpRequest("WF001", "1", "12345678000190", "98765432000100", "001",
            BigDecimal("120.50"), numeroParcela = "01"), "atividade", 1, 2, "fxpna",
    )

    @AfterEach
    fun limpar() {
        try {
            client.getDatabase(databaseName).drop()
        } finally {
            client.close()
        }
    }

    @Test
    fun `protocolos existentes devem entrar na fila unica com paginacao global e filtros`() {
        val companies = mock(CompanyRepository::class.java)
        val queue = ConsultaErpTaskQueue(mongo, companies)
        repository.inserirOuRecuperar(task)
        val antiga = Task("pdf", "Documento", TaskStatus.READY, TaskTypes.UploadPdf)
            .also { it.createdAt = task.criadaEm.minusSeconds(20) }
        mongo.insert(antiga)
        queue.recuperarRegistrosExistentes()
        queue.recuperarRegistrosExistentes()
        val controller = TasksController(mock(TaskRepository::class.java), mock(TaskService::class.java),
            mock(QiveApiClient::class.java), mongo)
        val all = controller.get(PageRequest.of(0, 1), null, null, null)
        assertEquals(2L, all.totalElements)
        assertEquals(TaskTypes.ConsultaErp, all.content.single().taskType)
        assertEquals("001", all.content.single().consultaErp!!.requisicao.numDocFiscal)
        assertEquals("pdf", controller.get(PageRequest.of(1, 1), null, null, null).content.single().id)
        assertEquals(1L, controller.get(PageRequest.of(0, 10), TaskTypes.ConsultaErp, null, null).totalElements)
        assertEquals(1L, controller.get(PageRequest.of(0, 10), null, null, "12345678000190").totalElements)
        val queued = all.content.single()
        queued.status = TaskStatus.FAILED
        mongo.save(queued)
        queue.sincronizar(task.copy(status = StatusConsultaErp.RETORNO_PENDENTE, ultimoErro = "Retorno pendente"))
        assertEquals(TaskStatus.FAILED, mongo.findById(queued.id, Task::class.java)!!.status)
        queue.sincronizar(task.copy(status = StatusConsultaErp.CONCLUIDA))
        assertEquals(TaskStatus.FINISHED, mongo.findById(queued.id, Task::class.java)!!.status)
        queue.sincronizar(task)
        assertEquals(StatusConsultaErp.CONCLUIDA, mongo.findById(queued.id, Task::class.java)!!.consultaErp!!.status)
    }

    @Test
    fun `reserva por ID nao deve consumir outro protocolo da fila`() {
        repository.inserirOuRecuperar(task)
        repository.inserirOuRecuperar(task.copy(id = "outra"))
        assertEquals("outra", repository.reservarProxima(Instant.now(), "outra")!!.id)
        assertNull(repository.buscar(task.id)!!.reserva)
        assertNull(repository.reservarProxima(Instant.now(), "outra"))
    }

    @Test
    fun `listar deve filtrar processo e status e paginar registros recentes sem reserva`() {
        val now = Instant.now()
        repository.inserirOuRecuperar(task.copy(criadaEm = now.minusSeconds(30)))
        repository.inserirOuRecuperar(task.copy(id = "task-2", criadaEm = now.minusSeconds(10)))
        repository.inserirOuRecuperar(task.copy(id = "task-3", requisicao = task.requisicao.copy(workflowId = "OUTRO")))
        repository.inserirOuRecuperar(task.copy(id = "task-4", status = StatusConsultaErp.RETORNO_PENDENTE))
        val order = Sort.by(Sort.Direction.DESC, "criadaEm", "_id")
        val first = repository.listar(PageRequest.of(0, 1, order), StatusConsultaErp.PENDENTE, "WF001")
        val second = repository.listar(PageRequest.of(1, 1, order), StatusConsultaErp.PENDENTE, "WF001")
        assertEquals(2L, first.totalElements)
        assertEquals("task-2", first.content.single().id)
        assertEquals("task-1", second.content.single().id)
        assertNull(repository.buscar("task-2")!!.reserva)
        assertEquals(4L, repository.listar(PageRequest.of(0, 10, order), null, null).totalElements)
    }

    @Test
    fun `deve deduplicar registro e persistir dados complexos`() {
        repository.inserirOuRecuperar(task)
        val repetida = repository.inserirOuRecuperar(task.copy(atividadeId = "outra"))
        assertEquals("atividade", repetida.atividadeId)
        assertEquals(0, task.requisicao.valorParcela.compareTo(repetida.requisicao.valorParcela))
        assertEquals("01", repetida.requisicao.numeroParcela)
        val reservada = repository.reservarProxima(Instant.now())!!
        repository.atualizar(reservada.copy(
            status = StatusConsultaErp.RETORNO_PENDENTE, erp = Erp.SAP,
            resultado = ResultadoConsultaErp(false, "TESTE", "Mensagem", mapOf("idTitulo" to "123")),
            respostaRegistrada = true,
        ), liberar = true)
        val salva = repository.buscar(task.id)!!
        assertEquals(Erp.SAP, salva.erp)
        assertEquals("123", salva.resultado?.dados?.get("idTitulo"))
        assertTrue(salva.respostaRegistrada)
        assertNull(salva.reserva)
    }

    @Test
    fun `apenas um executor pode reservar a mesma tarefa`() {
        repository.inserirOuRecuperar(task)
        val inicio = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val resultados = (1..2).map {
                executor.submit(Callable { inicio.await(); repository.reservarProxima(Instant.now()) })
            }
            inicio.countDown()
            assertEquals(1, resultados.map { it.get(10, TimeUnit.SECONDS) }.count { it != null })
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `reserva expirada deve ser recuperada e executor antigo impedido de salvar`() {
        repository.inserirOuRecuperar(task)
        val antiga = repository.reservarProxima(Instant.now())!!
        repository.atualizar(antiga.copy(status = StatusConsultaErp.RETORNO_PENDENTE,
            resultado = ResultadoConsultaErp(false, "TESTE", "Resultado persistido"), respostaRegistrada = true))
        mongo.updateFirst(Query(Criteria.where("_id").`is`(task.id)),
            Update().set("bloqueadoAte", Instant.EPOCH), ConsultaErpTask::class.java)
        val recuperada = repository.reservarProxima(Instant.now())!!
        assertNotEquals(antiga.reserva, recuperada.reserva)
        assertEquals("TESTE", recuperada.resultado?.codigo)
        assertTrue(recuperada.respostaRegistrada)
        assertThrows<IllegalStateException> { repository.atualizar(antiga, liberar = true) }
        repository.atualizar(recuperada.copy(status = StatusConsultaErp.CONCLUIDA), liberar = true)
        assertNull(repository.reservarProxima(Instant.now()))
    }

    @Test
    fun `entrega esgotada nao e reservada, pausa a task nativa e so volta pela reativacao`() {
        val queue = ConsultaErpTaskQueue(mongo, mock(CompanyRepository::class.java))
        repository.inserirOuRecuperar(task)
        val reservada = repository.reservarProxima(Instant.now())!!
        val falha = ResultadoConsultaErp(false, "ERRO_CONSULTA_ERP", "timeout")
        val esgotada = repository.atualizar(reservada.copy(
            status = StatusConsultaErp.RETORNO_FALHOU, resultado = ResultadoConsultaErp(false, "EM_ABERTO", "aberto"),
            respostaRegistrada = true, ultimoErro = "esgotada", falhasRetorno = 9, primeiraFalhaRetornoEm = Instant.EPOCH,
            ultimaFalhaTecnica = falha, ultimaFalhaTecnicaEm = Instant.EPOCH, atualizadaEm = Instant.now(),
        ), liberar = true)
        assertEquals(9, esgotada.falhasRetorno)
        assertEquals("ERRO_CONSULTA_ERP", esgotada.ultimaFalhaTecnica?.codigo)
        queue.sincronizar(esgotada)
        val nativa = "${task.id}-${TaskTypes.ConsultaErp}"
        assertEquals(TaskStatus.FAILED, mongo.findById(nativa, Task::class.java)!!.status)
        assertNull(repository.reservarProxima(Instant.now().plusSeconds(86400)))

        val agora = Instant.now()
        val reativada = repository.reativarRetorno(task.id, agora)!!
        assertEquals(StatusConsultaErp.RETORNO_PENDENTE, reativada.status)
        assertEquals(0, reativada.falhasRetorno)
        assertNull(reativada.primeiraFalhaRetornoEm)
        assertTrue(reativada.respostaRegistrada)
        assertEquals("esgotada", reativada.ultimoErro)
        assertNull(repository.reativarRetorno(task.id, agora), "idempotente")
        assertEquals(task.id, repository.reservarProxima(agora.plusSeconds(1))!!.id)
    }
}
