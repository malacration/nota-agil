package br.andrew.nota.agil.services

import br.andrew.nota.agil.erp.ConsultaErpConfiguration
import br.andrew.nota.agil.erp.ConsultaErpResolver
import br.andrew.nota.agil.model.erp.*
import br.andrew.nota.agil.repository.ConsultaErpRepository
import br.andrew.nota.agil.softexpert.service.WorkFlowEnvrioment
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant

/**
 * Protocolo de consulta de pagamento no ERP pedido pelo SoftExpert.
 *
 * Linha do tempo de uma tarefa (detalhes em docs/consulta-erp-softexpert.md):
 * - POST: grava `PENDENTE` com `esperaPagamentoAte = aceite + espera-pagamento.prazo`.
 * - Cada consulta ao ERP passa pela [PoliticaReconsultaErp]: resultado nao final (parcela em
 *   aberto, nota ainda nao lancada, falha tecnica com tentativas sobrando) deixa a tarefa
 *   `PENDENTE` com `proximaTentativa` no futuro e nada vai ao SE. Resultado de negocio fica em
 *   `ultimaConsulta`; falha tecnica fica em `ultimaFalhaTecnica` e nao apaga o de negocio.
 * - Resultado final vai para `resultado`, `RETORNO_PENDENTE`, e segue o retorno ao SE (campos ->
 *   comentario -> executeActivity -> `CONCLUIDA`), uma unica vez.
 * - Entrega recusada/com erro: novas tentativas pela [PoliticaRetornoSe]; esgotou ->
 *   `RETORNO_FALHOU` (task nativa `FAILED`), sem novas chamadas ao SE ate [reativarRetorno].
 */
@Service
class ConsultaErpService internal constructor(
    private val repository: ConsultaErpRepository,
    private val resolver: ConsultaErpResolver,
    private val configuration: ConsultaErpConfiguration,
    private val flow: WorkFlowEnvrioment,
    private val retorno: ConsultaErpRetornoService,
    private val queue: ConsultaErpTaskQueue,
    private val clock: Clock,
) {
    @Autowired
    constructor(
        repository: ConsultaErpRepository,
        resolver: ConsultaErpResolver,
        configuration: ConsultaErpConfiguration,
        flow: WorkFlowEnvrioment,
        retorno: ConsultaErpRetornoService,
        queue: ConsultaErpTaskQueue,
    ) : this(repository, resolver, configuration, flow, retorno, queue, Clock.systemUTC())

    private val log = LoggerFactory.getLogger(javaClass)
    private val politica = PoliticaReconsultaErp(configuration)
    private val politicaRetorno = PoliticaRetornoSe(configuration)

    init {
        configuration.validarReconsulta()
    }

    fun registrar(request: RegistrarConsultaErpRequest): ConsultaErpTask {
        val requisicao = request.normalizada()
        val id = MessageDigest.getInstance("SHA-256")
            .digest("${requisicao.workflowId}\u0000${requisicao.solicitacaoId}".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        repository.buscar(id)?.let { return confereRepeticao(it, requisicao) }
        if (configuration.atividadeId.isBlank() || configuration.acaoSucesso < 1 || configuration.acaoFalha < 1 ||
            configuration.acaoSucesso == configuration.acaoFalha) {
            throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Configure integracao.erp.atividade-id e as acoes distintas de sucesso/falha antes de receber solicitacoes")
        }
        val aceite = clock.instant()
        val task = repository.inserirOuRecuperar(ConsultaErpTask(
            id = id,
            requisicao = requisicao,
            atividadeId = configuration.atividadeId.trim(),
            acaoSucesso = configuration.acaoSucesso,
            acaoFalha = configuration.acaoFalha,
            tabelaFormulario = flow.tableName,
            camposRetorno = configuration.camposRetorno.toMap(),
            criadaEm = aceite,
            atualizadaEm = aceite,
            proximaTentativa = aceite,
            esperaPagamentoAte = aceite.plus(configuration.esperaPagamento.prazo),
        ))
        return confereRepeticao(task, requisicao)
    }

    private fun confereRepeticao(task: ConsultaErpTask, request: RegistrarConsultaErpRequest): ConsultaErpTask {
        if (task.requisicao != request) throw ResponseStatusException(HttpStatus.CONFLICT,
            "workflowId e solicitacaoId ja registrados com outros dados")
        queue.sincronizar(task)
        return task
    }

    fun buscar(id: String): ConsultaErpTask = repository.buscar(id)
        ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Solicitacao ERP nao encontrada")

    fun listar(page: Int, size: Int, status: StatusConsultaErp?, workflowId: String?): Page<ConsultaErpTask> {
        if (page < 0 || size !in 1..100) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Use page >= 0 e size entre 1 e 100")
        }
        val workflow = workflowId?.trim()?.takeIf { it.isNotEmpty() }
        if (workflow != null && workflow.length > 100) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "workflowId deve ter no maximo 100 caracteres")
        }
        val pagination = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "criadaEm", "_id"))
        return repository.listar(pagination, status, workflow)
    }

    fun processarPendentes(limite: Int = 50) {
        repeat(limite) {
            val task = repository.reservarProxima(clock.instant()) ?: return
            try {
                processar(task)
            } catch (e: Exception) {
                // Uma falha de persistencia/reserva nao deve interromper as demais tarefas.
                log.error("Falha no processamento da tarefa ERP {}", task.id, e)
            }
        }
    }

    fun executar(id: String) {
        val existente = buscar(id)
        // RETORNO_FALHOU so volta pela reativacao (TaskService.updateStatus -> READY); um Run ou
        // uma rodada do job com a task READY antiga em memoria apenas reafirma FAILED na fila.
        if (existente.status == StatusConsultaErp.CONCLUIDA || existente.status == StatusConsultaErp.RETORNO_FALHOU) {
            queue.sincronizar(existente)
            return
        }
        // Tarefa aguardando reconsulta (proximaTentativa no futuro) nao e reservada: Run e o job
        // respeitam a espera.
        val reservada = repository.reservarProxima(clock.instant(), id) ?: return
        processar(reservada)
    }

    private fun processar(reservada: ConsultaErpTask) {
        var task = reservada
        if (task.resultado == null) {
            task = consultarErp(task) ?: return
        }
        task = repository.atualizar(task.copy(tentativasRetorno = task.tentativasRetorno + 1, atualizadaEm = clock.instant()))
        try {
            if (!task.respostaRegistrada) {
                retorno.registrarResultado(task)
                task = repository.atualizar(task.copy(respostaRegistrada = true, atualizadaEm = clock.instant()))
            }
            retorno.avancar(task)
        } catch (e: Exception) {
            falhaNoRetorno(task, e)
            return
        }
        val agora = clock.instant()
        val concluida = repository.atualizar(task.copy(
            status = StatusConsultaErp.CONCLUIDA, ultimoErro = null,
            concluidaEm = agora, atualizadaEm = agora,
        ), liberar = true)
        queue.sincronizar(concluida)
    }

    /**
     * Retoma a entrega de uma tarefa `RETORNO_FALHOU` (chamado quando o operador volta a task
     * nativa para READY). Devolve a tarefa reativada, ou null se ela nao estava `RETORNO_FALHOU`.
     * Nao chama o SE: a proxima rodada do job entrega, sem repetir comentario ja confirmado.
     */
    fun reativarRetorno(id: String): ConsultaErpTask? =
        repository.reativarRetorno(id, clock.instant())
            ?.also { log.info("Entrega ao SoftExpert reativada manualmente para tarefa ERP {}", id) }

    /** Agenda nova tentativa da entrega com backoff ou, esgotada, encerra em `RETORNO_FALHOU`. */
    private fun falhaNoRetorno(task: ConsultaErpTask, e: Exception) {
        val agora = clock.instant()
        val erro = e.message ?: e.javaClass.simpleName
        val atualizada = when (val decisao = politicaRetorno.decidir(task, agora)) {
            is PoliticaRetornoSe.Decisao.NovaTentativa -> task.copy(
                ultimoErro = erro.take(1000), falhasRetorno = decisao.falhas,
                primeiraFalhaRetornoEm = decisao.primeiraFalhaEm, atualizadaEm = agora, proximaTentativa = decisao.em,
            )
            is PoliticaRetornoSe.Decisao.Esgotar -> task.copy(
                status = StatusConsultaErp.RETORNO_FALHOU,
                ultimoErro = "Entrega ao SoftExpert esgotada apos ${decisao.falhas} falhas seguidas desde " +
                    "${decisao.primeiraFalhaEm}; volte a task para READY para tentar de novo. Ultimo erro: $erro".take(1000),
                falhasRetorno = decisao.falhas, primeiraFalhaRetornoEm = decisao.primeiraFalhaEm,
                atualizadaEm = agora, proximaTentativa = agora,
            )
        }
        val salva = repository.atualizar(atualizada, liberar = true)
        queue.sincronizar(salva)
        if (salva.status == StatusConsultaErp.RETORNO_FALHOU) {
            log.error("Retorno ao SoftExpert esgotado para tarefa ERP {} ({} falhas): {}", task.id, salva.falhasRetorno, erro)
        } else {
            log.warn("Retorno ao SoftExpert pendente para tarefa ERP {} (falha {}), nova tentativa em {}: {}",
                task.id, salva.falhasRetorno, salva.proximaTentativa, erro)
        }
    }

    /**
     * Consulta o ERP e aplica a [PoliticaReconsultaErp]. Resultado final: grava em `resultado`
     * (RETORNO_PENDENTE) e devolve a tarefa para o retorno ao SE. Resultado nao final: grava em
     * `ultimaConsulta`, agenda a reconsulta, libera a reserva e devolve null (nada vai ao SE).
     * Cair entre a consulta e a gravacao so repete a consulta, que e somente leitura.
     */
    private fun consultarErp(task: ConsultaErpTask): ConsultaErpTask? {
        val client = resolver.resolver(task.requisicao.cpfCnpjTomador)
        val resultado = if (client == null) {
            ResultadoConsultaErp(false, "CNPJ_NAO_MAPEADO", "Nao existe ERP configurado para o CNPJ do tomador")
        } else try {
            client.consultar(task.requisicao)
        } catch (e: Exception) {
            log.error("Falha ao consultar {} na tarefa {}", client.erp, task.id, e)
            ResultadoConsultaErp(false, ConsultaErpConfiguration.CODIGO_FALHA_TECNICA,
                "Falha tecnica na consulta ao ERP ${client.erp}")
        }
        val agora = clock.instant()
        val decisao = politica.decidir(task, resultado, agora)
        val tecnica = !resultado.sucesso && resultado.codigo == ConsultaErpConfiguration.CODIGO_FALHA_TECNICA
        val consultada = task.copy(
            erp = client?.erp, consultas = task.consultas + 1,
            // Falha tecnica nao apaga o ultimo resultado de negocio: e ele que vai ao SE no fim da espera.
            ultimaConsulta = if (tecnica) politica.ultimoResultadoNegocio(task) else resultado,
            ultimaConsultaEm = if (tecnica) task.ultimaConsultaEm else agora,
            ultimaFalhaTecnica = if (tecnica) resultado else task.ultimaFalhaTecnica,
            ultimaFalhaTecnicaEm = if (tecnica) agora else task.ultimaFalhaTecnicaEm,
            falhasTecnicas = decisao.falhasTecnicas, primeiraFalhaTecnicaEm = decisao.primeiraFalhaTecnicaEm,
            atualizadaEm = agora,
        )
        return when (decisao) {
            is PoliticaReconsultaErp.Decisao.Entregar -> repository.atualizar(consultada.copy(
                resultado = decisao.resultado, status = StatusConsultaErp.RETORNO_PENDENTE,
            ))
            is PoliticaReconsultaErp.Decisao.Reconsultar -> {
                val aguardando = repository.atualizar(consultada.copy(proximaTentativa = decisao.em), liberar = true)
                queue.sincronizar(aguardando)
                log.info("Tarefa ERP {} aguardando reconsulta ({}, falhas tecnicas seguidas: {}) ate {}",
                    task.id, resultado.codigo, decisao.falhasTecnicas, decisao.em)
                null
            }
        }
    }
}
