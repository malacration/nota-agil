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

class Duplicata(
    val dataEmissao          : Date,
    val cpfCnpjPrestador     : String,
    val razaoPrestador : String,
    val cpfCnpjTomador : String,
    val razaoTomador : String,
    val valorBruto : BigDecimal,
    val valorLiquido : BigDecimal,
    val numero : String,
){

    companion object {
        private val DATE_FMT = DateTimeFormatter
            .ofPattern("dd/MM/yyyy")
            .withZone(ZoneId.systemDefault())
    }

    fun getTitulo(): String {
        val instant = dataEmissao.toInstant()
        return "${DATE_FMT.format(instant)} - $razaoPrestador - Nº $numero"
    }

    fun getSoftexpertMap(tableName: String): Map<String, Map<String, String>> {
        return mapOf<String,Map<String,String>>(
            "$tableName" to mapOf(
                "dataemissao" to SimpleDateFormat("yyyy-MM-dd").format(dataEmissao),
                "dtinclusao" to SimpleDateFormat("yyyy-MM-dd").format(Date()),
                "razaotomador" to razaoTomador,
                "cpfcnpjtomador" to cpfCnpjTomador,
                "cnpjprestador" to cpfCnpjPrestador,
                "razaoprestador" to razaoPrestador,
                "numdocfiscal" to numero.toString(),
                "valorbruto" to valorBruto.toString(),
                "valorliquido" to valorLiquido.toString()
            )
        )
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
