package br.andrew.nota.agil.qive.model

import br.andrew.nota.agil.model.Duplicata
import br.andrew.nota.agil.model.TipoDuplicata
import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Date

@JsonIgnoreProperties(ignoreUnknown = true)
data class NotaProdutos(
    @JsonProperty("access_key")
    var id: String,
    var xml: NfeXmlWrapper? = null
) {

    fun getDuplicata(): Duplicata {
        return Duplicata(
            getDataEmissao() ?: throw Exception("Erro ao recuperar a data de emissão"),
            getCpfCnpjEmitente() ?: throw Exception("Não é possível pegar o CNPJ/CPF do emitente"),
            getNomeEmitente() ?: throw Exception("Erro ao pegar nome do emitente"),
            getCpfCnpjDestinatario() ?: throw Exception("Erro ao pegar CNPJ/CPF do destinatário"),
            getNomeDestinatario() ?: throw Exception("Erro ao pegar nome do destinatário"),
            getValorBruto() ?: throw Exception("Erro ao obter valor bruto"),
            getValorLiquido() ?: throw Exception("Erro ao obter valor líquido"),
            getNumero() ?: throw Exception("Erro ao pegar número da nota"),
            TipoDuplicata.Nfe
        )
    }

    fun getDataEmissao(): Date? {
        val s = xml?.nfe?.infNFe?.ide?.dhEmi ?: return null
        val zone = ZoneId.of("America/Porto_Velho")

        fun tryParse(block: () -> Instant): Instant? =
            runCatching { block() }.getOrNull()

        val instant: Instant? =
            // ISO com offset, e.g. 2025-09-01T07:55:00-03:00
            tryParse { OffsetDateTime.parse(s, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant() }
            // ISO local sem offset (fallback raro)
                ?: tryParse { LocalDateTime.parse(s, DateTimeFormatter.ISO_LOCAL_DATE_TIME).atZone(zone).toInstant() }
                // Só data (fallback raro)
                ?: tryParse { LocalDate.parse(s, DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay(zone).toInstant() }
                // Instante ISO
                ?: tryParse { Instant.parse(s) }

        return instant?.let { Date.from(it) }
    }

    fun getNumero(): String? =
        xml?.nfe?.infNFe?.ide?.nNF

    fun getNomeEmitente(): String? =
        xml?.nfe?.infNFe?.emit?.xNome

    fun getCpfCnpjEmitente(): String? =
        xml?.nfe?.infNFe?.emit?.anyDoc()

    fun getNomeDestinatario(): String? =
        xml?.nfe?.infNFe?.dest?.xNome

    fun getCpfCnpjDestinatario(): String? =
        xml?.nfe?.infNFe?.dest?.anyDoc()

    fun getValorBruto(): BigDecimal? =
        xml?.nfe?.infNFe?.total?.ICMSTot?.vProd?.let { BigDecimal(it) }

    fun getValorLiquido(): BigDecimal? =
        (xml?.nfe?.infNFe?.total?.ICMSTot?.vNF ?: xml?.nfe?.infNFe?.total?.ICMSTot?.vProd)
            ?.let { BigDecimal(it) }
}

// ----------------------------- XML WRAPPERS -----------------------------

@JsonIgnoreProperties(ignoreUnknown = true)
data class NfeXmlWrapper(
    @JsonProperty("@attributes")
    var attributes: NfeXmlAttributes? = null,
    @JsonProperty("NFe")
    var nfe: NFe? = null,
    @JsonProperty("protNFe")
    var protNFe: ProtNFe? = null
    // Importante: não mapeamos "Signature" de propósito; será ignorado.
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class NfeXmlAttributes(
    var versao: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class NFe(
    @JsonProperty("infNFe")
    var infNFe: InfNFe? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ProtNFe(
    @JsonProperty("@attributes")
    var attributes: ProtNFeAttributes? = null,
    @JsonProperty("infProt")
    var infProt: InfProt? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ProtNFeAttributes(
    var versao: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class InfProt(
    var tpAmb: String? = null,
    var verAplic: String? = null,
    var chNFe: String? = null,
    var dhRecbto: String? = null,
    var nProt: String? = null,
    var digVal: String? = null,
    var cStat: String? = null,
    var xMotivo: String? = null
)

// ----------------------------- infNFe -----------------------------

@JsonIgnoreProperties(ignoreUnknown = true)
data class InfNFe(
    @JsonProperty("@attributes")
    var attributes: InfNFeAttributes? = null,
    var ide: Ide? = null,
    var emit: Emitente? = null,
    var dest: Destinatario? = null,
    @JsonProperty("det")
    @JsonFormat(with = [JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY])
    var det: List<Det>? = null,
    var total: Total? = null,
    var transp: Transp? = null,
    var cobr: Cobranca? = null,

    @JsonFormat(with = [JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY])
    var pag: List<Pagamento>? = null,
    var infAdic: InfAdic? = null,
    var infRespTec: InfRespTec? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class InfNFeAttributes(
    var Id: String? = null,
    var versao: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Ide(
    var cUF: String? = null,
    var cNF: String? = null,
    var natOp: String? = null,
    var mod: String? = null,
    var serie: String? = null,
    var nNF: String? = null,
    var dhEmi: String? = null,
    var dhSaiEnt: String? = null,
    var tpNF: String? = null,
    var idDest: String? = null,
    var cMunFG: String? = null,
    var tpImp: String? = null,
    var tpEmis: String? = null,
    var cDV: String? = null,
    var tpAmb: String? = null,
    var finNFe: String? = null,
    var indFinal: String? = null,
    var indPres: String? = null,
    var procEmi: String? = null,
    var verProc: String? = null
)

// ----------------------------- Emitente/Destinatário -----------------------------

@JsonIgnoreProperties(ignoreUnknown = true)
data class Emitente(
    @JsonProperty("CNPJ")
    var CNPJ: String? = null,
    @JsonProperty("CPF")
    var CPF: String? = null,
    var xNome: String? = null,
    var xFant: String? = null,
    var enderEmit: Ender? = null,
    var IE: String? = null,
    var CRT: String? = null
) {
    fun anyDoc(): String? = CNPJ ?: CPF
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class Destinatario(
    @JsonProperty("CNPJ")
    var CNPJ: String? = null,
    @JsonProperty("CPF")
    var CPF: String? = null,
    var xNome: String? = null,
    var enderDest: Ender? = null,
    var indIEDest: String? = null,
    var IE: String? = null
) {
    fun anyDoc(): String? = CNPJ ?: CPF
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class Ender(
    var xLgr: String? = null,
    var nro: String? = null,
    var xCpl: String? = null,
    var xBairro: String? = null,
    var cMun: String? = null,
    var xMun: String? = null,
    var UF: String? = null,
    var CEP: String? = null,
    var cPais: String? = null,
    var xPais: String? = null,
    var fone: String? = null
)

// ----------------------------- Itens -----------------------------

@JsonIgnoreProperties(ignoreUnknown = true)
data class Det(
    @JsonProperty("@attributes")
    var attributes: DetAttributes? = null,
    var prod: Prod? = null,
    var imposto: Imposto? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class DetAttributes(
    var nItem: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Prod(
    var cProd: String? = null,
    var cEAN: String? = null,
    var xProd: String? = null,
    var NCM: String? = null,
    var CFOP: String? = null,
    var uCom: String? = null,
    var qCom: String? = null,
    var vUnCom: String? = null,
    var vProd: String? = null,
    var cEANTrib: String? = null,
    var uTrib: String? = null,
    var qTrib: String? = null,
    var vUnTrib: String? = null,
    var indTot: String? = null,
    var nItemPed: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Imposto(
    var vTotTrib: String? = null,
    var ICMS: Icms? = null,
    var PIS: Pis? = null,
    var COFINS: Cofins? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Icms(
    var ICMS00: ICMS00? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ICMS00(
    var orig: String? = null,
    var CST: String? = null,
    var modBC: String? = null,
    var vBC: String? = null,
    var pICMS: String? = null,
    var vICMS: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Pis(
    var PISAliq: PISAliq? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class PISAliq(
    var CST: String? = null,
    var vBC: String? = null,
    var pPIS: String? = null,
    var vPIS: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Cofins(
    var COFINSAliq: COFINSAliq? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class COFINSAliq(
    var CST: String? = null,
    var vBC: String? = null,
    var pCOFINS: String? = null,
    var vCOFINS: String? = null
)

// ----------------------------- Totais / Frete -----------------------------

@JsonIgnoreProperties(ignoreUnknown = true)
data class Total(
    var ICMSTot: ICMSTot? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ICMSTot(
    var vBC: String? = null,
    var vICMS: String? = null,
    var vICMSDeson: String? = null,
    var vICMSUFRemet: String? = null,
    var vFCP: String? = null,
    var vBCST: String? = null,
    var vST: String? = null,
    var vFCPST: String? = null,
    var vFCPSTRet: String? = null,
    var vProd: String? = null,
    var vFrete: String? = null,
    var vSeg: String? = null,
    var vDesc: String? = null,
    var vII: String? = null,
    var vIPI: String? = null,
    var vIPIDevol: String? = null,
    var vPIS: String? = null,
    var vCOFINS: String? = null,
    var vOutro: String? = null,
    var vNF: String? = null,
    var vTotTrib: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Transp(
    var modFrete: String? = null,
    var transporta: Transporta? = null,
    var vol: Vol? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Transporta(
    var CNPJ: String? = null,
    var xNome: String? = null,
    var xEnder: String? = null,
    var xMun: String? = null,
    var UF: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Vol(
    var qVol: String? = null,
    var pesoL: String? = null,
    var pesoB: String? = null
)

// ----------------------------- Cobrança / Pagamento -----------------------------

@JsonIgnoreProperties(ignoreUnknown = true)
data class Cobranca(
    var fat: Fat? = null,
    @JsonFormat(with = [JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY])
    var dup: List<Dup>? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Fat(
    var nFat: String? = null,
    var vOrig: String? = null,
    var vDesc: String? = null,
    var vLiq: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Dup(
    var nDup: String? = null,
    var dVenc: String? = null, // yyyy-MM-dd
    var vDup: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Pagamento(
    @JsonFormat(with = [JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY])
    var detPag: List<DetPag>? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class DetPag(
    var indPag: String? = null,
    var tPag: String? = null,
    var vPag: String? = null
)

// ----------------------------- Info Adicional / Resp. Tec -----------------------------

@JsonIgnoreProperties(ignoreUnknown = true)
data class InfAdic(
    var infCpl: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class InfRespTec(
    var CNPJ: String? = null,
    var xContato: String? = null,
    var email: String? = null,
    var fone: String? = null
)
