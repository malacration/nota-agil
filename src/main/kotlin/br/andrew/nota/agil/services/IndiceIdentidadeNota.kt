package br.andrew.nota.agil.services

import br.andrew.nota.agil.model.tasks.Task
import org.slf4j.LoggerFactory
import org.springframework.context.event.ContextRefreshedEvent
import org.springframework.context.event.EventListener
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.Index
import org.springframework.stereotype.Component

/**
 * Indice unico esparso em `task.identidadeNota`: garantia no banco de que nao existem duas
 * CreateTask da mesma nota (o insert perdedor falha com DuplicateKey). Esparso: as tasks antigas,
 * sem o campo, ficam fora, entao o indice nao conflita com dados existentes. Sem o indice a aplicacao
 * NAO sobe: importar sem a garantia de unicidade abriria processo duplicado no SE.
 */
@Component
class IndiceIdentidadeNota(private val mongo: MongoTemplate) {
    private val log = LoggerFactory.getLogger(javaClass)

    @EventListener(ContextRefreshedEvent::class)
    fun criar() {
        try {
            mongo.indexOps(Task::class.java).createIndex(definicao())
        } catch (e: Exception) {
            log.error("Indice unico {} em task.identidadeNota nao criado ({}); a aplicacao nao sobe sem ele", NOME, e.javaClass.simpleName)
            throw IllegalStateException("Indice unico $NOME (task.identidadeNota) nao criado: sem ele a importacao " +
                "de notas pode duplicar processos no SE. Verifique o acesso ao Mongo e indices conflitantes.", e)
        }
    }

    companion object {
        const val NOME = "uniq_identidade_nota"
        fun definicao(): Index = Index().on("identidadeNota", Sort.Direction.ASC).unique().sparse().named(NOME)
    }
}
