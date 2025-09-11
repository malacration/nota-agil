package br.andrew.nota.agil.repository

import br.andrew.nota.agil.model.JobState
import br.andrew.nota.agil.model.JobsTypes
import org.springframework.data.mongodb.repository.MongoRepository

interface JobStateRepository : MongoRepository<JobState, String> {
    fun findByEmpresaAndTipo(empresa: String, tipo: JobsTypes): JobState?
}