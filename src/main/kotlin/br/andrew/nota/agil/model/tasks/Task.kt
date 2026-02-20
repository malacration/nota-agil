package br.andrew.nota.agil.model.tasks

import br.andrew.nota.agil.model.Duplicata
import com.fasterxml.jackson.annotation.JsonIgnore
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant
import java.util.Date


@Document("task")
class Task(
    @Id val id : String,
    val name : String,
    var status : TaskStatus,
    val taskType : TaskTypes,
    val duplicata : Duplicata? = null,
    val lastRunAt : Date? = null) {

    var recordID: String? = null
    var recordKey: String? = null

    @field:CreatedDate
    var createdAt: Instant = Instant.now()

    @JsonIgnore
    fun getWorkFlowId(): String? {
        return recordID
    }

    init{
        if(taskType == TaskTypes.CreateTask && duplicata == null)
            throw Exception("Nao é permitido tarefa de criar com duplicata null")
    }
}



//TODO rever esses status
enum class TaskStatus {
    READY,
    FAILED,
    FINISHED,
}

enum class TaskTypes {
    CreateTask,
    UploadPdf,
    CancelTask
}
