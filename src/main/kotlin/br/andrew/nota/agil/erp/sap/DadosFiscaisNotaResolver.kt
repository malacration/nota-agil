package br.andrew.nota.agil.erp.sap

import br.andrew.nota.agil.model.Duplicata
import br.andrew.nota.agil.model.TipoDuplicata
import br.andrew.nota.agil.model.erp.RegistrarConsultaErpRequest
import br.andrew.nota.agil.model.tasks.TaskTypes
import br.andrew.nota.agil.qive.interfaces.QiveApiClient
import br.andrew.nota.agil.repository.TaskRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.Collections

/**
 * Completa chave de acesso, serie e tipo da nota quando o SoftExpert nao manda (processos abertos
 * antes de os campos existirem no formulario FXP-NA). Somente leitura.
 *
 * Ordem, campo a campo (o que o SE mandou nunca e trocado):
 * 1. `CREATE_TASK`: task `CreateTask` cujo `recordID` (id da instancia devolvido pelo SE na criacao)
 *    e o `workflowId` do pedido; e a task do proprio processo e completa tipo, chave e serie. Mais
 *    de uma task com o mesmo recordID: nao usa nenhuma. So vale se a task confere com o pedido
 *    (mesmo CNPJ do prestador, mesmo numero sem zeros a esquerda - NFS-e tambem pelos 9 ultimos
 *    digitos - e, se o pedido traz tipo/serie, mesmo tipo e mesma serie conhecida); senao e
 *    ignorada (WARN com identificadores mascarados) e segue como se nao houvesse task do processo.
 * 2. `TASK_NUMERO`: sem a task do processo, tasks `CreateTask` do mesmo prestador + numero (com ou
 *    sem zeros a esquerda) + tomador. Como a nota achada assim pode ser OUTRO documento, so completa
 *    a CHAVE, e so quando o pedido ja traz tipo NFe ou CTe e serie, a nota tem o mesmo tipo e a
 *    mesma serie, e ela e a unica candidata (candidata de serie desconhecida conta como possivel).
 *    So NFe/CTe porque emitente + modelo + serie + numero e unico na SEFAZ; numero/serie de RPS de
 *    NFS-e nao garantem isso, entao NFS-e nunca usa este passo. Nunca completa tipo nem serie: sem
 *    eles o pedido segue para os desfechos seguros (DADOS_NOTA_INSUFICIENTES aqui,
 *    AMBIGUO/NOTA_NAO_ENCONTRADA no sap-rovema).
 * 3. `QIVE`: NFS-e de task antiga do proprio processo (sem chave fiscal/serie gravadas): um GET no
 *    Qive pelo id (`/nfse/received?id[]=`), com cache em memoria por id. Falha do Qive: segue sem.
 *
 * [DadosFiscais.origem] diz quem forneceu os valores (vai para `dados.origemDadosFiscais`, para
 * auditoria). O sap-rovema ainda confere a chave contra numero, serie, tipo e CNPJ do pedido
 * (DADOS_NOTA_DIVERGENTES se nao bater). Nao loga chave, CNPJ nem numero.
 */
@Component
open class DadosFiscaisNotaResolver(
    private val tasks: TaskRepository,
    private val qive: QiveApiClient?,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** Cache de NFS-e lidas no Qive (id -> dados). So guarda resposta valida do Qive. */
    private val cacheQive: MutableMap<String, DadosNfse> = Collections.synchronizedMap(
        object : LinkedHashMap<String, DadosNfse>(64, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, DadosNfse>?) = size > MAX_CACHE
        })

    data class DadosFiscais(
        val chaveAcesso: String?,
        val serie: String?,
        val tipo: TipoDuplicata?,
        /**
         * Quem forneceu os valores, na ordem, unidos por "+": [REQUISICAO], [CREATE_TASK],
         * [TASK_NUMERO], [QIVE]; [NENHUMA] quando nao ha valor algum.
         */
        val origem: String,
    ) {
        /** Algum valor veio de fora do pedido do SE. */
        val completado: Boolean get() = origem != REQUISICAO && origem != NENHUMA
    }

    private data class DadosNfse(val chave: String?, val serie: String?)

    open fun completar(requisicao: RegistrarConsultaErpRequest): DadosFiscais {
        val pedido = soDaRequisicao(requisicao)
        var chave = pedido.chaveAcesso
        var serie = pedido.serie
        var tipo = pedido.tipo
        if (chave != null && serie != null && tipo != null) return pedido

        val origens = linkedSetOf<String>()
        if (chave != null || serie != null || tipo != null) origens += REQUISICAO
        try {
            val propria = duplicataPorWorkflow(requisicao.workflowId)
            // NFS-e antiga sem chave/serie na task: o Qive e lido antes, para a serie entrar na conferencia.
            val chaveTask = propria?.chaveAcessoFiscal()
            val serieTask = propria?.serieFiscal()
            val doQive = if (propria?.tipo == TipoDuplicata.Nfse && ((chave ?: chaveTask) == null || (serie ?: serieTask) == null))
                nfseNoQive(propria) else null
            if (propria != null && confereComPedido(propria, requisicao, pedido, serieTask ?: doQive?.serie)) {
                val tipoTask = propria.tipo.takeUnless { it == TipoDuplicata.NaoDetectado }
                if (tipo == null && tipoTask != null) { tipo = tipoTask; origens += CREATE_TASK }
                if (chave == null && chaveTask != null) { chave = chaveTask; origens += CREATE_TASK }
                if (serie == null && serieTask != null) { serie = serieTask; origens += CREATE_TASK }
                if (chave == null && doQive?.chave != null) { chave = doQive.chave; origens += QIVE }
                if (serie == null && doQive?.serie != null) { serie = doQive.serie; origens += QIVE }
            } else if (chave == null && (tipo == TipoDuplicata.Nfe || tipo == TipoDuplicata.Cte) && serie != null) {
                // So NFe/CTe: emitente + modelo + serie + numero e unico na SEFAZ. NFS-e nunca.
                chavePorNumero(requisicao, tipo, serie)?.let { chave = it; origens += TASK_NUMERO }
            }
        } catch (e: Exception) {
            log.warn("Falha ao buscar a task de criacao para completar dados da nota: {}", e.javaClass.simpleName)
        }
        return DadosFiscais(chave, serie, tipo, origens.joinToString("+").ifEmpty { NENHUMA })
    }

    private fun duplicataPorWorkflow(workflowId: String): Duplicata? {
        val id = workflowId.trim().takeIf { it.isNotEmpty() } ?: return null
        val achadas = tasks.findAllByTaskTypeAndRecordID(TaskTypes.CreateTask, id).mapNotNull { it.duplicata }
        return achadas.singleOrNull()
    }

    /**
     * A task achada pelo recordID e mesmo a nota do pedido? Confere CNPJ do prestador (so digitos),
     * numero (sem zeros a esquerda; NFS-e tambem aceita um lado com mais de 9 digitos e o outro
     * igual aos 9 ultimos, como o SAP grava) e, quando o pedido traz, tipo e serie (serie so quando
     * a da task e conhecida, pela task ou pelo Qive). Nao confere: WARN mascarado e false.
     */
    private fun confereComPedido(task: Duplicata, requisicao: RegistrarConsultaErpRequest, pedido: DadosFiscais,
                                 serieTask: String?): Boolean {
        val divergencias = buildList {
            if (task.cpfCnpjPrestador.filter(Char::isDigit) != requisicao.cnpjPrestador.filter(Char::isDigit)) add("prestador")
            if (!mesmoNumero(task.numero, requisicao.numDocFiscal, task.tipo == TipoDuplicata.Nfse)) add("numero")
            if (pedido.tipo != null && task.tipo != pedido.tipo) add("tipo")
            if (pedido.serie != null && serieTask != null && normalizaSerie(serieTask) != normalizaSerie(pedido.serie)) add("serie")
        }
        if (divergencias.isEmpty()) return true
        log.warn("Task CreateTask do workflow {} nao confere com o pedido ({}); ignorada para completar dados da nota " +
            "[prestador task={} pedido={}; numero task={} pedido={}; tipo task={} pedido={}]",
            mascara(requisicao.workflowId), divergencias.joinToString(","),
            mascara(task.cpfCnpjPrestador), mascara(requisicao.cnpjPrestador),
            mascara(task.numero), mascara(requisicao.numDocFiscal), task.tipo, pedido.tipo)
        return false
    }

    /**
     * Chave da unica NFe/CTe do mesmo prestador + numero + tomador + tipo + serie do pedido; null se
     * nenhuma, se mais de uma pode ser a nota (serie desconhecida conta como possivel) ou se a
     * unica nao tem chave. Nunca chamado para NFS-e.
     */
    private fun chavePorNumero(requisicao: RegistrarConsultaErpRequest, tipo: TipoDuplicata, serie: String): String? {
        val numero = requisicao.numDocFiscal.trim()
        val numeros = setOf(numero, numero.trimStart('0')).filter { it.isNotEmpty() }
        if (numeros.isEmpty()) return null
        val candidatas = tasks.findAllByDuplicataCpfCnpjPrestadorInAndDuplicataNumeroIn(listOf(requisicao.cnpjPrestador), numeros)
            .filter { it.taskType == TaskTypes.CreateTask }
            .mapNotNull { it.duplicata }
            .filter { it.cpfCnpjTomador.filter(Char::isDigit) == requisicao.cpfCnpjTomador }
            .filter { it.tipo == tipo }
            // Varias tasks da mesma nota (reprocessamento) sao uma so; notas diferentes, nao.
            .distinctBy { listOf(it.tipo, it.chaveAcesso, it.chaveFiscal) }
        val seriePedido = normalizaSerie(serie) ?: return null
        val possiveis = candidatas.filter { it.serieFiscal() == null || normalizaSerie(it.serieFiscal()) == seriePedido }
        val unica = possiveis.singleOrNull() ?: return null
        return unica.takeIf { it.serieFiscal() != null }?.chaveAcessoFiscal()
    }

    private fun nfseNoQive(duplicata: Duplicata): DadosNfse? {
        val api = qive ?: return null
        val id = duplicata.chaveAcesso?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        cacheQive[id]?.let { return it }
        return try {
            val nota = api.nfse.receivedPorId(listOf(id)).data.singleOrNull() ?: return null
            DadosNfse(nota.getChaveFiscal(), nota.getSerie()).also { cacheQive[id] = it }
        } catch (e: Exception) {
            log.warn("Falha ao ler a NFS-e no Qive para completar dados da nota: {}", e.javaClass.simpleName)
            null
        }
    }

    companion object {
        const val MAX_CACHE = 1000

        const val REQUISICAO = "REQUISICAO"
        const val CREATE_TASK = "CREATE_TASK"
        const val TASK_NUMERO = "TASK_NUMERO"
        const val QIVE = "QIVE"
        const val NENHUMA = "NENHUMA"

        /** So o que o SE mandou, sem completar nada. */
        fun soDaRequisicao(requisicao: RegistrarConsultaErpRequest): DadosFiscais {
            val chave = requisicao.chaveAcesso?.takeIf { it.isNotBlank() }
            val serie = requisicao.serieDocFiscal?.takeIf { it.isNotBlank() }
            val tipo = requisicao.tipoDoc?.takeUnless { it == TipoDuplicata.NaoDetectado }
            val origem = if (chave != null || serie != null || tipo != null) REQUISICAO else NENHUMA
            return DadosFiscais(chave, serie, tipo, origem)
        }

        /** Mesma regra do sap-rovema para NFe/CTe: sem zeros a esquerda, "001" == "1". */
        private fun normalizaSerie(serie: String?): String? =
            serie?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }?.trimStart('0')?.ifEmpty { "0" }

        /**
         * Mesmo numero de nota, sem zeros a esquerda. NFS-e: o SAP grava so os 9 ultimos digitos dos
         * numeros longos, entao um lado com mais de 9 digitos casa com o outro igual aos 9 ultimos.
         */
        internal fun mesmoNumero(numeroTask: String, numeroPedido: String, nfse: Boolean): Boolean {
            val a = numeroTask.trim().trimStart('0')
            val b = numeroPedido.trim().trimStart('0')
            if (a.isEmpty() || b.isEmpty()) return false
            if (a == b) return true
            if (!nfse || !a.all(Char::isDigit) || !b.all(Char::isDigit)) return false
            val (longo, curto) = if (a.length > b.length) a to b else b to a
            return longo.length > 9 && longo.takeLast(9).trimStart('0') == curto
        }

        /** Para log: so os 3 ultimos caracteres ("***123"). */
        private fun mascara(valor: String?): String {
            val v = valor?.trim().orEmpty()
            return if (v.length <= 3) "***" else "***" + v.takeLast(3)
        }
    }
}
