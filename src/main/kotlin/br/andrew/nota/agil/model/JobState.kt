package br.andrew.nota.agil.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

@Document("job_state")
@CompoundIndexes(
    CompoundIndex(name = "uniq_company_type", def = "{'empresa': 1, 'tipo': 1}", unique = true)
)
data class JobState(
    val empresa: String,
    val tipo: JobsTypes,
    var enabled: Boolean = false,
    var cursor: Int = 0,
    var lastFrom: Instant = JobDefaults.BOOTSTRAP_FROM,
){
    @Id var id: String = "$empresa-${tipo.toString()}"
}

object JobDefaults {
    val BOOTSTRAP_FROM: Instant = Instant.parse("2025-08-27T00:00:00Z")
}
