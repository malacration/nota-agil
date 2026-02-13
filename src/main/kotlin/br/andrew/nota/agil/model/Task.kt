package br.andrew.nota.agil.model

import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
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
    CancelTask
}
