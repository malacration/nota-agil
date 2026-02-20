package br.andrew.nota.agil.qive.model

import br.andrew.nota.agil.model.Duplicata
import br.andrew.nota.agil.model.TipoDuplicata
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date

@JsonIgnoreProperties(ignoreUnknown = true)
data class ConhecimentoTransporte(
    @JsonProperty("access_key")
    val id: String,
    val xml: JsonNode? = null,
) {
    private val payload: JsonNode? by lazy { extractPayload(xml) }

    private val infCteNode: JsonNode? by lazy {
        val root = payload ?: return@lazy null
        root.path("CTe").path("infCte").takeIf { !it.isMissingNode && !it.isNull }
            ?: root.path("CTeSimp").path("infCte").takeIf { !it.isMissingNode && !it.isNull }
            ?: root.path("cte").path("infCte").takeIf { !it.isMissingNode && !it.isNull }
            ?: root.path("cteSimp").path("infCte").takeIf { !it.isMissingNode && !it.isNull }
            ?: root.path("cteProc").path("CTe").path("infCte").takeIf { !it.isMissingNode && !it.isNull }
            ?: root.path("cteProc").path("CTeSimp").path("infCte").takeIf { !it.isMissingNode && !it.isNull }
            ?: root.path("cteProc").path("cte").path("infCte").takeIf { !it.isMissingNode && !it.isNull }
            ?: root.path("cteProc").path("cteSimp").path("infCte").takeIf { !it.isMissingNode && !it.isNull }
    }

    private val infCte: CteInf? by lazy {
        payload?.let { runCatching { MAPPER.treeToValue(it, CteXmlWrapper::class.java) }.getOrNull() }?.infCte()
    }

    @JsonIgnoreProperties
    fun getDuplicata(): Duplicata {
        return Duplicata(
            getDataEmissao() ?: throw Exception("Erro ao recuperar a data de emissao do CTe"),
            getCpfCnpjPrestador() ?: throw Exception("Nao foi possivel obter CPF/CNPJ do emitente do CTe"),
            getNomePrestador() ?: throw Exception("Nao foi possivel obter nome do emitente do CTe"),
            getCpfCnpjTomador() ?: throw Exception("Nao foi possivel obter CPF/CNPJ do tomador do CTe"),
            getNomeTomador() ?: throw Exception("Nao foi possivel obter nome do tomador do CTe"),
            getValorBruto() ?: throw Exception("Nao foi possivel obter valor bruto do CTe"),
            getValorLiquido() ?: throw Exception("Nao foi possivel obter valor liquido do CTe"),
            getNumero() ?: throw Exception("Nao foi possivel obter numero do CTe"),
            TipoDuplicata.Cte,
            chaveAcesso = id,
        )
    }

    fun getDataEmissao(): Date? {
        val s = infCte?.ide?.dhEmi ?: return null
        val zone = ZoneId.of("America/Porto_Velho")

        fun tryParse(block: () -> Instant): Instant? =
            runCatching { block() }.getOrNull()

        val instant: Instant? =
            tryParse { OffsetDateTime.parse(s, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant() }
                ?: tryParse { LocalDateTime.parse(s, DateTimeFormatter.ISO_LOCAL_DATE_TIME).atZone(zone).toInstant() }
                ?: tryParse { LocalDate.parse(s, DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay(zone).toInstant() }
                ?: tryParse { Instant.parse(s) }

        return instant?.let { Date.from(it) }
    }

    fun getNumero(): String? =
        infCte?.ide?.nCT

    fun getNomePrestador(): String? =
        infCte?.emit?.xNome ?: infCte?.emit?.xFant

    fun getCpfCnpjPrestador(): String? =
        infCte?.emit?.anyDoc()

    fun getNomeTomador(): String? =
        infCte?.toma4?.toma?.xNome
            ?: infCte?.toma?.xNome
            ?: partyFromToma3()?.xNome
            ?: fallbackParties().firstNotNullOfOrNull { it?.xNome }

    fun getCpfCnpjTomador(): String? =
        infCte?.toma4?.toma?.anyDoc()
            ?: infCte?.toma?.anyDoc()
            ?: partyFromToma3()?.anyDoc()
            ?: fallbackParties().firstNotNullOfOrNull { it?.anyDoc() }

    fun getValorBruto(): BigDecimal? =
        toBigDecimal(infCte?.vPrest?.vTPrest)
            ?: toBigDecimal(textAt(infCteNode, "vPrest", "vTPrest"))
            ?: toBigDecimal(textAt(infCteNode, "total", "vTPrest"))
            ?: toBigDecimal(textAt(infCteNode, "det", "vPrest"))
            ?: toBigDecimal(textAt(infCteNode, "det", 0, "vPrest"))

    fun getValorLiquido(): BigDecimal? =
        toBigDecimal(infCte?.vPrest?.vRec)
            ?: toBigDecimal(textAt(infCteNode, "vPrest", "vRec"))
            ?: toBigDecimal(textAt(infCteNode, "total", "vTRec"))
            ?: toBigDecimal(textAt(infCteNode, "det", "vRec"))
            ?: toBigDecimal(textAt(infCteNode, "det", 0, "vRec"))
            ?: getValorBruto()

    private fun extractPayload(node: JsonNode?): JsonNode? {
        if (node == null || node.isNull) return null

        return when {
            node.isObject && node.has("xml") -> extractPayload(node.get("xml"))
            node.isObject -> node
            node.isTextual -> {
                val value = node.asText().trim()
                if (value.startsWith("{") || value.startsWith("[")) {
                    val parsed = runCatching { MAPPER.readTree(value) }.getOrNull()
                    extractPayload(parsed)
                } else {
                    null
                }
            }
            else -> null
        }
    }

    private fun partyFromToma3(): CteParty? {
        val toma3 = infCte?.ide?.toma3?.toma ?: return null
        return when (toma3) {
            "0" -> infCte?.rem
            "1" -> infCte?.exped
            "2" -> infCte?.receb
            "3" -> infCte?.dest
            else -> null
        }
    }

    private fun fallbackParties(): List<CteParty?> = listOf(
        infCte?.dest,
        infCte?.rem,
        infCte?.receb,
        infCte?.exped,
    )

    private fun toBigDecimal(value: String?): BigDecimal? {
        if (value.isNullOrBlank()) return null
        return runCatching { BigDecimal(value) }.getOrNull()
    }

    private fun textAt(node: JsonNode?, vararg path: String): String? {
        var current = node ?: return null
        for (segment in path) {
            current = current.path(segment)
            if (current.isMissingNode || current.isNull) return null
        }
        return current.asText().takeIf { it.isNotBlank() }
    }

    private fun textAt(node: JsonNode?, first: String, index: Int, last: String): String? {
        val target = node?.path(first)
            ?.takeIf { !it.isMissingNode && !it.isNull && it.isArray && it.size() > index }
            ?.path(index)
            ?.path(last)
            ?: return null
        return target.asText().takeIf { it.isNotBlank() }
    }

    companion object {
        private val MAPPER = jacksonObjectMapper()
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class CteXmlWrapper(
    @JsonProperty("CTe")
    val cteUpper: CteDoc? = null,
    @JsonProperty("CTeSimp")
    val cteSimpUpper: CteDoc? = null,
    @JsonProperty("cte")
    val cteLower: CteDoc? = null,
    @JsonProperty("cteSimp")
    val cteSimpLower: CteDoc? = null,
    @JsonProperty("cteProc")
    val cteProc: CteProc? = null,
) {
    fun infCte(): CteInf? =
        cteProc?.cteUpper?.infCte
            ?: cteProc?.cteSimpUpper?.infCte
            ?: cteProc?.cteLower?.infCte
            ?: cteProc?.cteSimpLower?.infCte
            ?: cteUpper?.infCte
            ?: cteSimpUpper?.infCte
            ?: cteLower?.infCte
            ?: cteSimpLower?.infCte
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class CteProc(
    @JsonProperty("CTe")
    val cteUpper: CteDoc? = null,
    @JsonProperty("CTeSimp")
    val cteSimpUpper: CteDoc? = null,
    @JsonProperty("cte")
    val cteLower: CteDoc? = null,
    @JsonProperty("cteSimp")
    val cteSimpLower: CteDoc? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class CteDoc(
    @JsonProperty("infCte")
    val infCte: CteInf? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class CteInf(
    val ide: CteIde? = null,
    val emit: CteParty? = null,
    val rem: CteParty? = null,
    val exped: CteParty? = null,
    val receb: CteParty? = null,
    val dest: CteParty? = null,
    val toma: CteParty? = null,
    val toma4: CteToma4? = null,
    val vPrest: CteVPrest? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class CteIde(
    val dhEmi: String? = null,
    val nCT: String? = null,
    val toma3: CteToma3? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class CteToma3(
    val toma: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class CteToma4(
    val toma: CteParty? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class CteParty(
    @JsonProperty("CNPJ")
    val cnpj: String? = null,
    @JsonProperty("CPF")
    val cpf: String? = null,
    val xNome: String? = null,
    val xFant: String? = null,
) {
    fun anyDoc(): String? = cnpj ?: cpf
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class CteVPrest(
    val vTPrest: String? = null,
    val vRec: String? = null,
)
