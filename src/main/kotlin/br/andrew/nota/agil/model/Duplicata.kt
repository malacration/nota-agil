package br.andrew.nota.agil.model

import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date

class Duplicata(
    val dataEmissao          : Date,
    val cpfCnpjPrestador     : String,
    val razaoPrestador : String,
    val cpfCnpjTomador : String,
    val razaoTomador : String,
    val valorBruto : BigDecimal,
    val valorLiquido : BigDecimal,
    val numero : String,
    val tipo : TipoDuplicata,
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
                "valorliquido" to valorLiquido.toString(),
                "tipodoc" to tipo.toString()
            )
        )
    }

}