package br.andrew.nota.agil.qive.model

import br.andrew.nota.agil.model.Duplicata
import br.andrew.nota.agil.model.TipoDuplicata
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date

@JsonIgnoreProperties(ignoreUnknown = true)
data class NotaProdutos(
    @JsonProperty("access_key")
    var id: String,
    var xml: NfeXmlWrapper? = null,
) {
    fun getDuplicata(): Duplicata = Duplicata(
        getDataEmissao() ?: throw Exception("Erro ao recuperar a data de emissão"),
        getCpfCnpjEmitente() ?: throw Exception("Não é possível pegar o CNPJ/CPF do emitente"),
        getNomeEmitente() ?: throw Exception("Erro ao pegar nome do emitente"),
        getCpfCnpjDestinatario() ?: throw Exception("Erro ao pegar CNPJ/CPF do destinatário"),
        getNomeDestinatario() ?: throw Exception("Erro ao pegar nome do destinatário"),
        getValorBruto() ?: throw Exception("Erro ao obter valor bruto"),
        getValorLiquido() ?: throw Exception("Erro ao obter valor líquido"),
        getNumero() ?: throw Exception("Erro ao pegar número da nota"),
        TipoDuplicata.Nfe,
        chaveAcesso = id,
    )

    fun getDataEmissao(): Date? =
        xml?.nfe?.infNFe?.ide?.dhEmi?.toFiscalDate()

    fun getNumero(): String? = xml?.nfe?.infNFe?.ide?.nNF

    fun getNomeEmitente(): String? = xml?.nfe?.infNFe?.emit?.xNome

    fun getCpfCnpjEmitente(): String? = xml?.nfe?.infNFe?.emit?.anyDoc()

    fun getNomeDestinatario(): String? = xml?.nfe?.infNFe?.dest?.xNome

    fun getCpfCnpjDestinatario(): String? = xml?.nfe?.infNFe?.dest?.anyDoc()

    fun getValorBruto(): BigDecimal? =
        xml?.nfe?.infNFe?.total?.ICMSTot?.vProd?.let(::BigDecimal)

    fun getValorLiquido(): BigDecimal? =
        (xml?.nfe?.infNFe?.total?.ICMSTot?.vNF
            ?: xml?.nfe?.infNFe?.total?.ICMSTot?.vProd)
            ?.let(::BigDecimal)
}

@JsonIgnoreProperties(ignoreUnknown = true)
class NfeXmlWrapper(
    @JsonProperty("NFe")
    var nfe: NFe? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
class NFe(
    var infNFe: InfNFe? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
class InfNFe(
    var ide: Ide? = null,
    var emit: Emitente? = null,
    var dest: Destinatario? = null,
    var total: Total? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
class Ide(
    var nNF: String? = null,
    var dhEmi: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
class Emitente(
    @JsonProperty("CNPJ")
    var CNPJ: String? = null,
    @JsonProperty("CPF")
    var CPF: String? = null,
    var xNome: String? = null,
) {
    fun anyDoc(): String? = CNPJ ?: CPF
}

@JsonIgnoreProperties(ignoreUnknown = true)
class Destinatario(
    @JsonProperty("CNPJ")
    var CNPJ: String? = null,
    @JsonProperty("CPF")
    var CPF: String? = null,
    var xNome: String? = null,
) {
    fun anyDoc(): String? = CNPJ ?: CPF
}

@JsonIgnoreProperties(ignoreUnknown = true)
class Total(
    @JsonProperty("ICMSTot")
    var ICMSTot: ICMSTot? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
class ICMSTot(
    var vProd: String? = null,
    var vNF: String? = null,
)

private fun String.toFiscalDate(): Date? {
    val zone = ZoneId.of("America/Porto_Velho")
    val instant = runCatching {
        OffsetDateTime.parse(this, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant()
    }.getOrNull()
        ?: runCatching {
            LocalDateTime.parse(this, DateTimeFormatter.ISO_LOCAL_DATE_TIME).atZone(zone).toInstant()
        }.getOrNull()
        ?: runCatching {
            LocalDate.parse(this, DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay(zone).toInstant()
        }.getOrNull()
        ?: runCatching { Instant.parse(this) }.getOrNull()

    return instant?.let(Date::from)
}
