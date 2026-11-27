package br.andrew.nota.agil.services

import org.bson.Document
import org.slf4j.LoggerFactory
import org.springframework.dao.DuplicateKeyException
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.util.Date
import java.util.UUID

/**
 * Exclusao mutua na criacao de CreateTask da mesma nota (importacao de ERP x importacao de ERP x
 * job do Qive), entre threads e entre instancias. A [chave] agrupa as notas que podem ser a mesma
 * (prestador + tomador + numero canonico, ver [NotasErpService.chaveTrava]); dentro da trava quem
 * cria confere de novo se ja existe task equivalente e so entao insere.
 *
 * A trava NAO e a garantia de unicidade da mesma nota: essa vem do indice unico em
 * `task.identidadeNota` (e do `_id`), que nao depende de tempo. A trava cobre so as equivalencias
 * aproximadas que nao cabem num indice (NFS-e longa x 9 digitos do SAP, tipo/serie desconhecidos).
 */
interface TravaNotas {
    fun <T> comTrava(chave: String, bloco: () -> T): T
}

/**
 * [TravaNotas] no Mongo: documento com `_id` = chave na colecao `nota_trava` (o `_id` unico garante
 * um dono so). Trava abandonada (processo caiu) vence em [validade]. Sem conseguir em [espera],
 * lanca [IllegalStateException] (quem chama trata como erro e tenta de novo depois).
 */
@Component
class TravaNotasMongo(private val mongo: MongoTemplate) : TravaNotas {
    private val log = LoggerFactory.getLogger(javaClass)
    internal var espera: Duration = Duration.ofSeconds(5)
    /** Bem acima do tempo do bloco (consulta + insert, milissegundos): so recupera trava de processo que caiu. */
    internal var validade: Duration = Duration.ofMinutes(5)
    internal var agora: () -> Instant = Instant::now

    override fun <T> comTrava(chave: String, bloco: () -> T): T {
        val dono = UUID.randomUUID().toString()
        adquirir(chave, dono)
        try {
            return bloco()
        } finally {
            runCatching { mongo.remove(Query(Criteria.where("_id").`is`(chave).and("dono").`is`(dono)), COLECAO) }
                .onFailure { log.warn("Falha ao liberar trava de nota: {}", it.javaClass.simpleName) }
        }
    }

    private fun adquirir(chave: String, dono: String) {
        val limite = System.nanoTime() + espera.toNanos()
        while (true) {
            try {
                mongo.insert(Document(mapOf("_id" to chave, "dono" to dono, "criadaEm" to Date.from(agora()))), COLECAO)
                return
            } catch (e: DuplicateKeyException) {
                // So remove se ainda for uma trava vencida (outra pode ter acabado de ser criada).
                mongo.remove(Query(Criteria.where("_id").`is`(chave).and("criadaEm").lt(Date.from(agora().minus(validade)))), COLECAO)
                if (System.nanoTime() > limite)
                    throw IllegalStateException("Nota em processamento por outra requisicao; tente novamente")
                Thread.sleep(PAUSA_MS)
            }
        }
    }

    companion object {
        const val COLECAO = "nota_trava"
        private const val PAUSA_MS = 25L
    }
}
