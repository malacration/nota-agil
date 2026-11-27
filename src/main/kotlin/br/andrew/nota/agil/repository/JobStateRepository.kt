package br.andrew.nota.agil.repository

import br.andrew.nota.agil.model.JobState
import br.andrew.nota.agil.model.JobsTypes
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.data.mongodb.repository.Query
import org.springframework.data.mongodb.repository.Update
import java.time.Instant

interface JobStateRepository : MongoRepository<JobState, String> {
    fun findByEmpresaAndTipo(empresa: String, tipo: JobsTypes): JobState?

    /**
     * Progresso da execucao (cursor e lastFrom) SEM tocar em `enabled`: uma execucao em andamento nao
     * desfaz um stop/start manual feito enquanto ela rodava.
     */
    @Query("{ '_id': ?0 }")
    @Update("{ '\$set': { 'cursor': ?1, 'lastFrom': ?2 } }")
    fun salvarProgresso(id: String, cursor: Int, lastFrom: Instant): Long

    /** So o `enabled` (start/stop), sem sobrescrever o progresso de uma execucao em andamento. */
    @Query("{ '_id': ?0 }")
    @Update("{ '\$set': { 'enabled': ?1 } }")
    fun salvarEnabled(id: String, enabled: Boolean): Long
}
