package br.andrew.nota.agil.qive.model

import br.andrew.nota.agil.model.Duplicata
import br.andrew.nota.agil.model.TipoDuplicata
import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date

@JsonIgnoreProperties(ignoreUnknown = true)
data class NotaServico(
    var id: String,
    var xml: XmlWrapper? = null
){

    fun getDuplicata() : Duplicata{
        return Duplicata(
            getDataEmissao() ?: throw Exception("Erro ao recuperar a data de emissao"),
            getcpfCnpjPrestador() ?: throw Exception("Nao e possivel pegar o cpf cnpj do prestador"),
            getNomePrestador() ?: throw Exception("Erro ao pegar nome do prestador"),
            getcpfCnpjTomador() ?: throw Exception("Erro ao cpf do tomador"),
            getNomeTomador() ?: throw Exception("Erro ao pegar nome do tomador"),
            getValorBruto() ?:  throw Exception("erro ao obter valor Bruto"),
            getValorLiquido() ?: throw Exception("Erro ao pegar valor liquido"),
            getNumero() ?: throw Exception("Erro ao pegar numero da nota"),
            TipoDuplicata.Nfse,
            chaveAcesso = getChaveAcesso()
        )
    }

    fun getChaveAcesso(): String? {
        return id
            .takeIf { it.isNotBlank() }
            ?: xml?.nfse?.infNfse?.attributes?.Id
            ?: xml?.nfse?.infNfse?.declaracaoPrestacaoServico?.infDeclaracaoPrestacaoServico?.attributes?.Id
            ?: xml?.nfse?.infNfse?.codigoVerificacao
    }

    fun getNomePrestador(): String? {
        return xml?.nfse?.infNfse?.prestadorServico?.razaoSocial
    }

    fun getcpfCnpjTomador(): String? {
        return xml?.nfse?.infNfse?.declaracaoPrestacaoServico?.
            infDeclaracaoPrestacaoServico?.tomador?.identificacaoTomador?.cpfCnpj?.any()
    }

    fun getNomeTomador() : String? {
        return xml?.nfse?.infNfse?.declaracaoPrestacaoServico?.
            infDeclaracaoPrestacaoServico?.tomador?.razaoSocial
    }

    //"DataEmissao": "2025-07-22T00:00:00",

    fun getDataEmissao(): Date? {
        val s = xml?.nfse?.infNfse?.dataEmissao ?: return null

        val zone = ZoneId.of("America/Porto_Velho")

        fun tryParse(block: () -> Instant): Instant? =
            runCatching { block() }.getOrNull()

        val instant: Instant? =
            // ISO com offset, ex: 2025-07-22T00:00:00.861-04:00 ou ...Z
            tryParse { OffsetDateTime.parse(s, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant() }
            // ISO local sem offset, ex: 2025-07-22T00:00:00
                ?: tryParse { LocalDateTime.parse(s, DateTimeFormatter.ISO_LOCAL_DATE_TIME).atZone(zone).toInstant() }
                // Só data, ex: 2025-07-22
                ?: tryParse { LocalDate.parse(s, DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay(zone).toInstant() }
                // ISO instant, ex: 2025-07-22T00:00:00Z (cobre alguns casos)
                ?: tryParse { Instant.parse(s) }

        return instant?.let { Date.from(it) }
    }

    fun getValorLiquido() : BigDecimal? {
        val valor = xml?.nfse?.infNfse?.valoresNfse?.valorLiquidoNfse ?: return getValorBruto()
        return BigDecimal(valor)
    }

    fun getValorBruto() : BigDecimal? {
        val valor = xml?.nfse?.infNfse?.declaracaoPrestacaoServico?.
        infDeclaracaoPrestacaoServico?.servico?.valores?.valorServicos ?: return null
        return BigDecimal(valor)
    }

    fun getNumero(): String? {
        return xml?.nfse?.infNfse?.numero
    }

    fun getcpfCnpjPrestador(): String? {
        return xml?.nfse?.infNfse?.prestadorServico?.identificacaoPrestador?.cpfCnpj?.any()
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class XmlWrapper(
    @JsonProperty("Nfse")
    var nfse: Nfse? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Nfse(
    @JsonProperty("@attributes")
    var attributes: NfseAttributes? = null,
    @JsonProperty("InfNfse")
    var infNfse: InfNfse? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class NfseAttributes(
    var versao: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class InfNfse(
    @JsonProperty("@attributes")
    var attributes: InfNfseAttributes? = null,
    @JsonProperty("Numero")
    var numero: String? = null,
    @JsonProperty("CodigoVerificacao")
    var codigoVerificacao: String? = null,
    @JsonProperty("DataEmissao")
    var dataEmissao: String? = null,
    @JsonProperty("ValoresNfse")
    var valoresNfse: ValoresNfse? = null,
    @JsonProperty("PrestadorServico")
    var prestadorServico: PrestadorServico? = null,
    @JsonProperty("OrgaoGerador")
    var orgaoGerador: OrgaoGerador? = null,
    @JsonProperty("DeclaracaoPrestacaoServico")
    var declaracaoPrestacaoServico: DeclaracaoPrestacaoServico? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class InfNfseAttributes(
    var Id: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ValoresNfse(
    @JsonProperty("ValorLiquidoNfse")
    var valorLiquidoNfse: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class PrestadorServico(
    @JsonProperty("IdentificacaoPrestador")
    var identificacaoPrestador: IdentificacaoPrestador? = null,
    @JsonProperty("RazaoSocial")
    var razaoSocial: String? = null,
    @JsonProperty("Endereco")
    var endereco: Endereco? = null,

    @JsonProperty("Contato")
    @JsonFormat(with = [JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY])
    var contato: List<Contato>? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class IdentificacaoPrestador(
    @JsonProperty("CpfCnpj")
    var cpfCnpj: CpfCnpj? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class CpfCnpj(
    // Deixe ambos para tolerar CNPJ ou CPF conforme o município
    @JsonProperty("Cnpj")
    val cnpj: String? = null,
    @JsonProperty("Cpf")
    val cpf: String? = null
){

    fun any() : String{
        return if(cnpj != null)
            cnpj
        else if(cpf != null)
            cpf
        else
            throw Exception("Tanto CPF quanto CNPJ estao vazios")
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class Endereco(
    @JsonProperty("Endereco")
    var logradouro: String? = null,
    @JsonProperty("Numero")
    var numero: String? = null,
    @JsonProperty("Complemento")
    var complemento: String? = null,
    @JsonProperty("Bairro")
    var bairro: String? = null,
    @JsonProperty("CodigoMunicipio")
    var codigoMunicipio: String? = null,
    @JsonProperty("Uf")
    var uf: String? = null,
    @JsonProperty("Cep")
    var cep: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Contato(
    @JsonProperty("Telefone")
    var telefone: String? = null,
    @JsonProperty("Email")
    var email: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class OrgaoGerador(
    @JsonProperty("CodigoMunicipio")
    var codigoMunicipio: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class DeclaracaoPrestacaoServico(
    @JsonProperty("InfDeclaracaoPrestacaoServico")
    var infDeclaracaoPrestacaoServico: InfDeclaracaoPrestacaoServico? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class InfDeclaracaoPrestacaoServico(
    @JsonProperty("@attributes")
    var attributes: DpsAttributes? = null,
    @JsonProperty("Rps")
    var rps: Rps? = null,
    @JsonProperty("Competencia")
    var competencia: String? = null,
    @JsonProperty("Servico")
    var servico: Servico? = null,
    @JsonProperty("Prestador")
    var prestador: Prestador? = null,
    @JsonProperty("Tomador")
    var tomador: Tomador? = null,
    @JsonProperty("RegimeEspecialTributacao")
    var regimeEspecialTributacao: String? = null,
    @JsonProperty("OptanteSimplesNacional")
    var optanteSimplesNacional: String? = null,
    @JsonProperty("IncentivoFiscal")
    var incentivoFiscal: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class DpsAttributes(
    var Id: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Rps(
    @JsonProperty("@attributes")
    var attributes: RpsAttributes? = null,
    @JsonProperty("IdentificacaoRps")
    var identificacaoRps: IdentificacaoRps? = null,
    @JsonProperty("DataEmissao")
    var dataEmissao: String? = null,
    @JsonProperty("Status")
    var status: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class RpsAttributes(
    var Id: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class IdentificacaoRps(
    @JsonProperty("Numero")
    var numero: String? = null,
    @JsonProperty("Serie")
    var serie: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Servico(
    @JsonProperty("Valores")
    var valores: ValoresServico? = null,
    @JsonProperty("IssRetido")
    var issRetido: String? = null,
    @JsonProperty("ItemListaServico")
    @JsonDeserialize(using = StringOrArrayToStringDeserializer::class)
    var itemListaServico: String? = null,
    @JsonProperty("Discriminacao")
    var discriminacao: String? = null,
    @JsonProperty("CodigoMunicipio")
    var codigoMunicipio: String? = null,
    @JsonProperty("CodigoPais")
    var codigoPais: String? = null,
    @JsonProperty("ExigibilidadeISS")
    var exigibilidadeISS: String? = null,
    @JsonProperty("MunicipioIncidencia")
    var municipioIncidencia: String? = null
)

class StringOrArrayToStringDeserializer : JsonDeserializer<String?>() {
    override fun deserialize(parser: JsonParser, context: DeserializationContext): String? {
        val node = parser.codec.readTree<com.fasterxml.jackson.databind.JsonNode>(parser)

        return when {
            node.isNull -> null
            node.isArray -> node.firstOrNull()?.asText()
            else -> node.asText()
        }
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class ValoresServico(
    @JsonProperty("ValorServicos")
    var valorServicos: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Prestador(
    @JsonProperty("CpfCnpj")
    var cpfCnpj: CpfCnpj? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Tomador(
    @JsonProperty("IdentificacaoTomador")
    var identificacaoTomador: IdentificacaoTomador? = null,
    @JsonProperty("RazaoSocial")
    var razaoSocial: String? = null,
    @JsonProperty("Endereco")
    var endereco: Endereco? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class IdentificacaoTomador(
    @JsonProperty("CpfCnpj")
    var cpfCnpj: CpfCnpj? = null
)
