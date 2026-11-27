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
    val tipo : TipoDuplicata = TipoDuplicata.NaoDetectado,
    /**
     * NFe/CTe: chave de acesso (44). NFS-e: id do documento no Qive (usado no download do PDF), que
     * NAO e a chave fiscal: essa fica em [chaveFiscal].
     */
    val chaveAcesso : String?  = null,
    /** Serie fiscal (NFe/CTe: ide/serie; NFS-e: serie do RPS/DPS). Null em tasks antigas. */
    val serie : String? = null,
    /** So NFS-e: chave nacional (50 digitos, sem "NFS") ou id DPS, como o SAP grava. Null em tasks antigas. */
    val chaveFiscal : String? = null,
){

    companion object {
        private val DATE_FMT = DateTimeFormatter
            .ofPattern("dd/MM/yyyy")
            .withZone(ZoneId.systemDefault())

        private val CHAVE_44 = Regex("[0-9]{6}[0-9A-Z]{12}[0-9]{26}")
        private val NFSE_NACIONAL = Regex("NFS([0-9]{50})")
        private val DPS = Regex("DPS[0-9]{8}[0-9A-Z]{12}[0-9]{22}")

        /** `InfNfse/@Id` -> chave fiscal da NFS-e ("NFS" + 50 -> 50 digitos; `DPS` + 42 fica); outro formato -> null. */
        fun chaveFiscalNfse(id: String?): String? {
            val limpo = id?.filterNot(Char::isWhitespace)?.uppercase()?.takeIf { it.isNotEmpty() } ?: return null
            NFSE_NACIONAL.matchEntire(limpo)?.let { return it.groupValues[1] }
            if (limpo.length == 50 && limpo.all(Char::isDigit)) return limpo
            return limpo.takeIf { DPS.matches(it) }
        }
    }

    /**
     * Chave de acesso como o SAP grava em OPCH.U_ChaveAcesso: NFe/CTe = [chaveAcesso] (44);
     * NFS-e = [chaveFiscal]. Null quando nao ha (task antiga de NFS-e, NFS-e municipal sem chave).
     */
    fun chaveAcessoFiscal(): String? = when (tipo) {
        TipoDuplicata.Nfse -> chaveFiscal
        else -> chaveAcesso?.filterNot(Char::isWhitespace)?.uppercase()?.takeIf { CHAVE_44.matches(it) }
    }

    /**
     * Serie fiscal. NFe/CTe: caracteres 23-25 da chave de 44 (3 digitos, ex. "001"; e a mesma
     * ide/serie com zeros) ou, sem chave, [serie]. NFS-e: [serie] (do RPS/DPS).
     */
    fun serieFiscal(): String? =
        chaveAcessoFiscal()?.takeIf { tipo != TipoDuplicata.Nfse }?.substring(22, 25)
            ?: serie?.trim()?.takeIf { it.isNotEmpty() }

    fun getTitulo(): String {
        val instant = dataEmissao.toInstant()
        return "${DATE_FMT.format(instant)} ${tipo} Nº $numero - $razaoPrestador"
    }

    /**
     * Campos do formulario do processo no SE. Serie e chave de acesso so vao quando o ID do campo
     * no formulario esta configurado ([campoSerie] / [campoChaveAcesso], `softexpert.workflow.*`)
     * e o valor existe: sem ID configurado nada muda no que e enviado.
     */
    fun getSoftexpertMap(
        tableName: String,
        campoSerie: String? = null,
        campoChaveAcesso: String? = null,
    ): Map<String, Map<String, String>> {
        val fiscais = buildMap {
            campoSerie?.trim()?.takeIf { it.isNotEmpty() }?.let { campo -> serieFiscal()?.let { put(campo, it) } }
            campoChaveAcesso?.trim()?.takeIf { it.isNotEmpty() }?.let { campo -> chaveAcessoFiscal()?.let { put(campo, it) } }
        }
        return mapOf<String,Map<String,String>>(
            "$tableName" to fiscais + mapOf(
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