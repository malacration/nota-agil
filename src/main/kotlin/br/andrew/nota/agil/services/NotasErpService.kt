package br.andrew.nota.agil.services

import br.andrew.nota.agil.erp.notas.FiltroNotasErp
import br.andrew.nota.agil.erp.notas.NotaEntradaErp
import br.andrew.nota.agil.erp.notas.NotasEntradaErpClient
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
import org.slf4j.LoggerFactory
import org.springframework.dao.DuplicateKeyException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import java.util.Date

/**
 * Notas fiscais de entrada (contas a pagar) dos ERPs como fonte alternativa ao Qive. Uma unica
 * tela, com o ERP como filtro obrigatorio.
 *
 * Diferente do Qive, nada e agendado: listagem e criacao das tasks so por acao do usuario. A
 * importacao cria a task `CreateTask` (READY); o envio ao SE segue o fluxo atual (job SendWorkFlow
 * ou execucao manual na tela Tasks). Depois dela vem a `UploadPdfErp`.
 *
 * Sem processo duplicado no SE:
 * - NFe/CTe com chave de 44 (SAP): id da task = `{chave}-CreateTask`, o mesmo do job do Qive.
 * - Demais: id `{erp}-{id}-CreateTask`; antes de criar, procura `CreateTask` do mesmo prestador +
 *   numero ([NumeroNota]) + tomador (de qualquer origem). Os jobs do Qive fazem a mesma checagem
 *   ([criarTaskDoQive]) antes de criar a task deles.
 * - Garantia no banco: `insert` (nunca `save`) + indice unico em [Task.identidadeNota]: a mesma
 *   nota (mesma identidade canonica) nunca tem duas CreateTask e o job do Qive nunca sobrescreve a
 *   task de ERP de mesmo id, independente de tempo/trava.
 * - Equivalencias aproximadas (NFS-e longa x 9 digitos, tipo/serie desconhecidos) nao cabem num
 *   indice unico (a relacao nao e transitiva); para elas, checagem + insert ficam dentro de
 *   [TravaNotas] (mesma chave para ERP e Qive).
 */
@Service
class NotasErpService(
    clients: List<NotasEntradaErpClient>,
    private val empresas: CompanyRepository,
    private val tasks: TaskRepository,
    private val trava: TravaNotas,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val porErp = clients.associateBy { it.erp }.also {
        require(it.size == clients.size) { "Mais de uma fonte de notas para o mesmo ERP" }
    }

    /** ERPs e se estao disponiveis (habilitados e com conexao configurada), para o front. */
    fun erps(): List<ErpNotasDisponivel> = porErp.values.sortedBy { it.nome }.map {
        val situacao = it.situacao()
        ErpNotasDisponivel(it.erp, it.nome, situacao.disponivel, situacao.habilitado, situacao.configurado, situacao.motivo)
    }

    /** Empresas cadastradas que o usuario pode ver ([visivel]) e se cada uma pode listar notas no [erp]. */
    fun empresas(erp: Erp, visivel: (Company) -> Boolean): List<EmpresaNotasErp> {
        val client = porErp[erp] ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "ERP sem fonte de notas")
        return empresas.findAll().filter { it.cnpj != "all" && visivel(it) }.sortedBy { it.nome }
            .map { EmpresaNotasErp(it.cnpj, it.nome, client.motivoEmpresaIndisponivel(it)) }
    }

    /** [autorizar] recebe a empresa cadastrada e lanca 403 se o usuario nao pode usa-la. */
    fun listar(erp: Erp, cnpj: String, dataInicial: String?, dataFinal: String?, fornecedor: String?, numero: String?,
               autorizar: (Company) -> Unit): ListagemNotasErp {
        val client = disponivel(erp)
        val empresa = empresa(cnpj, client, autorizar)
        val (inicio, fim) = periodo(dataInicial, dataFinal)
        val numeroLimpo = numero?.trim()?.takeIf { it.isNotEmpty() }?.also {
            if (!it.all(Char::isDigit)) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Numero da nota deve ser numerico")
        }
        val fornecedorLimpo = fornecedor?.filter(Char::isDigit)?.takeIf { it.isNotEmpty() }?.also {
            if (it.length != 11 && it.length != 14) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Fornecedor: CNPJ ou CPF completo")
        }
        val notas = client.listar(empresa, FiltroNotasErp(inicio.toString(), fim.toString(), fornecedorLimpo, numeroLimpo))
        val existentes = tasksExistentes(notas, empresa)
        return ListagemNotasErp(erp, notas.map { NotaErpListItem(it, existentes[it.id]?.let(TaskResumo::from), problema(it)) })
    }

    fun importar(erp: Erp, cnpj: String, ids: List<String>, autorizar: (Company) -> Unit): ImportacaoNotasErp {
        val pedidos = ids.map(String::trim).filter(String::isNotEmpty).distinct()
        if (pedidos.isEmpty() || pedidos.size > MAX_IMPORTACAO)
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe de 1 a $MAX_IMPORTACAO notas")
        if (pedidos.any { it.length > 60 }) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Id de nota invalido")
        val client = disponivel(erp)
        val empresa = empresa(cnpj, client, autorizar)
        // Sempre relidas no ERP: o front so manda o id.
        val notas = client.buscar(empresa, pedidos).associateBy { it.id }
        val existentes = tasksExistentes(notas.values.toList(), empresa)

        return ImportacaoNotasErp(pedidos.map { id ->
            val nota = notas[id]
                ?: return@map ResultadoImportacao(id, StatusImportacao.NAO_ENCONTRADA, null,
                    "Nota nao encontrada no ERP para esta empresa (cancelada ou de outra empresa)")
            existentes[id]?.let { return@map ResultadoImportacao(id, StatusImportacao.JA_EXISTE, it.id, "Nota ja possui task (${it.status})") }
            problema(nota)?.let { return@map ResultadoImportacao(id, StatusImportacao.ERRO, null, it) }
            try {
                val task = criarTask(nota, empresa)
                trava.comTrava(chaveTrava(task.duplicata!!)) {
                    // Releitura dentro da trava: outra nota deste lote, outra requisicao ou o job do Qive
                    // pode ter criado a task equivalente depois da leitura acima.
                    tasksExistentes(listOf(nota), empresa)[id]?.let {
                        return@comTrava ResultadoImportacao(id, StatusImportacao.JA_EXISTE, it.id, "Nota ja possui task (${it.status})")
                    }
                    // insert: se o mesmo id foi criado agora, falha em vez de sobrescrever.
                    tasks.insert(task)
                    ResultadoImportacao(id, StatusImportacao.CRIADA, task.id, null)
                }
            } catch (e: DuplicateKeyException) {
                // Mesmo id ou mesma identidadeNota (indice unico): outra criacao ganhou a corrida.
                ResultadoImportacao(id, StatusImportacao.JA_EXISTE, null, "Nota ja possui task")
            } catch (e: Exception) {
                log.warn("Falha ao criar task de nota do ERP {}: {}", erp, e.javaClass.simpleName)
                ResultadoImportacao(id, StatusImportacao.ERRO, null, e.message ?: "Erro ao criar a task")
            }
        })
    }

    /** Duplicata e task CreateTask (READY) da nota, com a origem e a referencia no ERP. */
    internal fun criarTask(nota: NotaEntradaErp, empresa: Company): Task {
        val chave44 = chave44(nota)
        val duplicata = Duplicata(
            dataEmissao = Date.from(LocalDate.parse(nota.dataEmissao ?: nota.dataLancamento)
                .atStartOfDay(ZoneId.systemDefault()).toInstant()),
            cpfCnpjPrestador = nota.cpfCnpjFornecedor!!,
            razaoPrestador = nota.nomeFornecedor ?: "",
            cpfCnpjTomador = empresa.cnpj,
            razaoTomador = empresa.nome,
            valorBruto = nota.valorBruto ?: nota.valorTotal!!,
            valorLiquido = nota.valorLiquido ?: nota.valorTotal!!,
            numero = nota.numero!!.toString(),
            tipo = nota.tipo,
            // NFe/CTe: chave de 44 (download no Qive). NFS-e: no Qive este campo e o id do documento,
            // que o ERP nao tem; a chave fiscal vai em chaveFiscal.
            chaveAcesso = chave44,
            serie = nota.serie,
            chaveFiscal = if (nota.tipo == TipoDuplicata.Nfse) Duplicata.chaveFiscalNfse(nota.chaveAcesso) else null,
        )
        return Task(idCreateTask(nota), duplicata.getTitulo(), TaskStatus.READY, TaskTypes.CreateTask, duplicata).also {
            it.origem = origem(nota.erp)
            it.notaErp = ReferenciaNotaErp(nota.erp, nota.id, empresa.cnpj, nota.anexosPdf)
            it.tomador = empresa.cnpj
            it.identidadeNota = identidadeNota(duplicata)
        }
    }

    /** Motivo para nao importar (do ERP ou dado obrigatorio ausente); null se pode. */
    internal fun problema(nota: NotaEntradaErp): String? = nota.problema ?: when {
        nota.cpfCnpjFornecedor.isNullOrBlank() -> "Nota sem CNPJ/CPF do fornecedor no ERP"
        nota.numero == null -> "Nota sem numero no ERP"
        nota.valorTotal == null -> "Nota sem valor no ERP"
        nota.dataEmissao == null && nota.dataLancamento == null -> "Nota sem data no ERP"
        else -> null
    }

    /** Task CreateTask ja existente para cada nota (por id e por prestador + numero + tomador). */
    private fun tasksExistentes(notas: List<NotaEntradaErp>, empresa: Company): Map<String, Task> {
        if (notas.isEmpty()) return emptyMap()
        val porId = tasks.findAllById(notas.flatMap { listOf(idCreateTask(it), idErp(it)) }.distinct()).associateBy { it.id }
        // Prestador gravado com qualquer mascara/espaco (tasks antigas): regex pelos digitos; o filtro
        // abaixo ([equivalente]) compara de novo so os digitos.
        val fornecedores = NumeroNota.regexDocumentos(notas.mapNotNull { it.cpfCnpjFornecedor })
        val regex = NumeroNota.regexCandidatos(notas.mapNotNull { it.numero?.toString() })
        val porNumero = if (fornecedores == null || regex == null) emptyList()
        else tasks.findAllByDuplicataCpfCnpjPrestadorRegexAndDuplicataNumeroRegex(fornecedores, regex)
            .filter { it.taskType == TaskTypes.CreateTask && it.duplicata?.cpfCnpjTomador?.filter(Char::isDigit) == empresa.cnpj }
        return notas.mapNotNull { nota ->
            val task = porId[idCreateTask(nota)] ?: porId[idErp(nota)] ?: porNumero.firstOrNull { equivalente(it, nota) }
            task?.let { nota.id to it }
        }.toMap()
    }

    private fun equivalente(task: Task, nota: NotaEntradaErp): Boolean {
        val d = task.duplicata ?: return false
        if (d.cpfCnpjPrestador.filter(Char::isDigit) != nota.cpfCnpjFornecedor?.filter(Char::isDigit)) return false
        if (!semConflito(distintivos(d), distintivos(nota))) return false
        return NumeroNota.mesmo(d.numero, nota.numero?.toString() ?: return false, nfse(d.tipo, nota.tipo))
    }

    private fun disponivel(erp: Erp): NotasEntradaErpClient {
        val client = porErp[erp] ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "ERP sem fonte de notas")
        val situacao = client.situacao()
        if (!situacao.disponivel) throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
            "Importacao de notas do ${client.nome} indisponivel: ${situacao.motivo}")
        return client
    }

    private fun empresa(cnpj: String, client: NotasEntradaErpClient, autorizar: (Company) -> Unit): Company {
        val digitos = cnpj.filter(Char::isDigit)
        if (digitos.length != 14) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "CNPJ da empresa deve ter 14 digitos")
        val empresa = empresas.findById(digitos).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Empresa nao cadastrada no Nota Agil")
        }
        autorizar(empresa)
        client.motivoEmpresaIndisponivel(empresa)?.let { throw ResponseStatusException(HttpStatus.BAD_REQUEST, it) }
        return empresa
    }

    private fun periodo(dataInicial: String?, dataFinal: String?): Pair<LocalDate, LocalDate> {
        fun data(valor: String?, campo: String): LocalDate = try {
            LocalDate.parse(valor?.trim() ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "$campo e obrigatoria"))
        } catch (e: DateTimeParseException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "$campo invalida (yyyy-MM-dd)")
        }
        val inicio = data(dataInicial, "dataInicial")
        val fim = data(dataFinal, "dataFinal")
        if (fim.isBefore(inicio)) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "dataFinal anterior a dataInicial")
        if (ChronoUnit.DAYS.between(inicio, fim) >= MAX_DIAS)
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Periodo maximo de $MAX_DIAS dias")
        return inicio to fim
    }

    companion object {
        const val MAX_IMPORTACAO = 100
        const val MAX_DIAS = 31L
        private val CHAVE_44 = Regex("[0-9]{44}")

        fun origem(erp: Erp): OrigemNota = when (erp) {
            Erp.SAP -> OrigemNota.SAP
            Erp.DEALER -> OrigemNota.DEALER
        }

        private fun chave44(nota: NotaEntradaErp): String? {
            if (nota.tipo != TipoDuplicata.Nfe && nota.tipo != TipoDuplicata.Cte) return null
            return nota.chaveAcesso?.filterNot(Char::isWhitespace)?.takeIf { CHAVE_44.matches(it) }
        }

        private fun idErp(nota: NotaEntradaErp) = "${nota.erp.name.lowercase()}-${nota.id}-${TaskTypes.CreateTask}"

        /** Mesmo id do job do Qive para NFe/CTe com chave; senao, id pelo ERP + id no ERP. */
        fun idCreateTask(nota: NotaEntradaErp): String = chave44(nota)?.let { "$it-${TaskTypes.CreateTask}" } ?: idErp(nota)

        /** Mesmo numero de outro tipo (CTe x NFS-e) nao e a mesma nota; tipo desconhecido (Dealer) e compativel. */
        fun tiposCompativeis(a: TipoDuplicata, b: TipoDuplicata) =
            a == b || a == TipoDuplicata.NaoDetectado || b == TipoDuplicata.NaoDetectado

        /** O que distingue notas de mesmo prestador + tomador + numero: tipo, serie e chave fiscal. */
        internal data class Distintivos(val tipo: TipoDuplicata, val serie: String?, val chave: String?)

        internal fun distintivos(d: Duplicata) =
            Distintivos(d.tipo, NumeroNota.serie(d.serieFiscal()), d.chaveAcessoFiscal()?.uppercase())

        internal fun distintivos(nota: NotaEntradaErp): Distintivos {
            val chave = chave44(nota)
                ?: if (nota.tipo == TipoDuplicata.Nfse) Duplicata.chaveFiscalNfse(nota.chaveAcesso) else null
            val serie = if (nota.tipo == TipoDuplicata.Nfse) nota.serie else chave44(nota)?.substring(22, 25) ?: nota.serie
            return Distintivos(nota.tipo, NumeroNota.serie(serie), chave?.uppercase())
        }

        /**
         * Podem ser a mesma nota? Tipos compativeis; com chave fiscal (44 da NFe/CTe ou 50 da NFS-e) nos
         * dois lados, a chave decide; senao, NFe/CTe com serie conhecida nos dois lados precisam da mesma
         * serie (serie do RPS da NFS-e nao conta). Dado desconhecido de um lado nao separa as notas.
         */
        internal fun semConflito(a: Distintivos, b: Distintivos): Boolean {
            if (!tiposCompativeis(a.tipo, b.tipo)) return false
            if (a.chave != null && b.chave != null) return a.chave == b.chave
            if (!nfse(a.tipo, b.tipo) && a.serie != null && b.serie != null && a.serie != b.serie) return false
            return true
        }

        /**
         * Identidade fiscal canonica gravada em [Task.identidadeNota] (indice unico): prestador + tomador
         * (so digitos) + tipo + numero (sem zeros a esquerda) + serie (NFe/CTe; "-" se desconhecida ou
         * NFS-e, cujo numero ja e unico por prestador). So iguala o que e certamente a mesma nota, para
         * o indice nunca barrar nota diferente; equivalencias aproximadas (NFS-e longa x 9 digitos do SAP,
         * tipo/serie desconhecidos do Dealer) ficam com a busca de candidatas dentro da [TravaNotas].
         * Null se faltar prestador, tomador ou numero.
         */
        fun identidadeNota(d: Duplicata): String? {
            val prestador = d.cpfCnpjPrestador.filter(Char::isDigit).takeIf { it.isNotEmpty() } ?: return null
            val tomador = d.cpfCnpjTomador.filter(Char::isDigit).takeIf { it.isNotEmpty() } ?: return null
            val numero = NumeroNota.limpo(d.numero).takeIf { it.isNotEmpty() } ?: return null
            val serie = if (d.tipo == TipoDuplicata.Nfe || d.tipo == TipoDuplicata.Cte) NumeroNota.serie(d.serieFiscal()) ?: "-" else "-"
            // Sem a chave fiscal de proposito (aceito na revisao): NFe/CTe com mesmos prestador/numero/serie e
            // chaves diferentes so existe com dado errado, e incluir a chave separaria nota com e sem chave.
            return listOf(prestador, tomador, d.tipo.name, numero, serie).joinToString("|")
        }

        /** Comparacao pelos 9 ultimos digitos so quando um dos lados e NFS-e (ver [NumeroNota]). */
        private fun nfse(a: TipoDuplicata, b: TipoDuplicata) = a == TipoDuplicata.Nfse || b == TipoDuplicata.Nfse

        /**
         * Chave da [TravaNotas]: prestador + tomador + numero canonico. Toda nota equivalente (mesmo
         * prestador, tomador e numero por [NumeroNota.mesmo]) cai na mesma chave.
         */
        fun chaveTrava(duplicata: Duplicata): String = listOf(
            duplicata.cpfCnpjPrestador.filter(Char::isDigit),
            duplicata.cpfCnpjTomador.filter(Char::isDigit),
            NumeroNota.canonico(duplicata.numero),
        ).joinToString("|", prefix = "nota|")

        /**
         * Para os jobs do Qive: ja ha CreateTask importada de ERP do mesmo prestador + numero +
         * tomador e tipo compativel? Evita abrir um segundo processo no SE para a mesma nota.
         */
        fun existeCreateTaskEquivalente(tasks: TaskRepository, duplicata: Duplicata): Boolean {
            val regex = NumeroNota.regexCandidatos(listOf(duplicata.numero)) ?: return false
            val prestador = duplicata.cpfCnpjPrestador.filter(Char::isDigit)
            val prestadorRegex = NumeroNota.regexDocumentos(listOf(prestador)) ?: return false
            return tasks.findAllByDuplicataCpfCnpjPrestadorRegexAndDuplicataNumeroRegex(prestadorRegex, regex).any {
                it.taskType == TaskTypes.CreateTask && it.origem != null && it.origem != OrigemNota.QIVE &&
                    it.duplicata?.cpfCnpjPrestador?.filter(Char::isDigit) == prestador &&
                    it.duplicata.cpfCnpjTomador.filter(Char::isDigit) == duplicata.cpfCnpjTomador.filter(Char::isDigit) &&
                    semConflito(distintivos(it.duplicata), distintivos(duplicata)) &&
                    NumeroNota.mesmo(it.duplicata.numero, duplicata.numero, nfse(it.duplicata.tipo, duplicata.tipo))
            }
        }

        /**
         * Para os jobs do Qive: cria a CreateTask [task] so se nao houver a mesma (mesmo id) nem uma
         * equivalente importada de ERP. Checagem e `insert` dentro da [trava]: nunca sobrescreve
         * (`save`) a task de ERP criada no meio. true = criada.
         */
        fun criarTaskDoQive(tasks: TaskRepository, trava: TravaNotas, task: Task): Boolean {
            val duplicata = task.duplicata ?: return false
            task.identidadeNota = identidadeNota(duplicata)
            return trava.comTrava(chaveTrava(duplicata)) {
                if (tasks.existsById(task.id) || existeCreateTaskEquivalente(tasks, duplicata)) return@comTrava false
                try {
                    tasks.insert(task)
                    true
                } catch (e: DuplicateKeyException) {
                    // Mesmo id ou mesma identidadeNota (indice unico): a task existente fica intacta.
                    false
                }
            }
        }
    }
}

data class ErpNotasDisponivel(
    val erp: Erp,
    val nome: String,
    val disponivel: Boolean,
    val habilitado: Boolean,
    val configurado: Boolean,
    val motivo: String?,
)

data class EmpresaNotasErp(val cnpj: String, val nome: String, val motivoIndisponivel: String?)

data class ListagemNotasErp(val erp: Erp, val notas: List<NotaErpListItem>)

data class NotaErpListItem(
    val nota: NotaEntradaErp,
    /** Task CreateTask ja existente (Qive ou ERP); null = pode importar. */
    val task: TaskResumo?,
    /** Motivo para nao poder importar; null = ok. */
    val problema: String?,
)

data class TaskResumo(val id: String, val status: TaskStatus, val origem: OrigemNota, val recordID: String?) {
    companion object {
        fun from(task: Task) = TaskResumo(task.id, task.status, task.origem ?: OrigemNota.QIVE, task.recordID)
    }
}

data class ImportarNotasErpRequest(val erp: Erp? = null, val cnpj: String? = null, val ids: List<String>? = null)

data class ImportacaoNotasErp(val resultados: List<ResultadoImportacao>)

data class ResultadoImportacao(val id: String, val status: StatusImportacao, val taskId: String?, val mensagem: String?)

enum class StatusImportacao { CRIADA, JA_EXISTE, NAO_ENCONTRADA, ERRO }
