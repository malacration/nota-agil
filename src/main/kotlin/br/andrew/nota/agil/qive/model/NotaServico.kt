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
data class NotaServico(
    var id: String,
    var xml: XmlWrapper? = null,
) {
    fun getDuplicata(): Duplicata = Duplicata(
        getDataEmissao() ?: throw Exception("Erro ao recuperar a data de emissao"),
        getcpfCnpjPrestador() ?: throw Exception("Nao e possivel pegar o cpf cnpj do prestador"),
        getNomePrestador() ?: throw Exception("Erro ao pegar nome do prestador"),
        getcpfCnpjTomador() ?: throw Exception("Erro ao cpf do tomador"),
        getNomeTomador() ?: throw Exception("Erro ao pegar nome do tomador"),
        getValorBruto() ?: throw Exception("erro ao obter valor Bruto"),
        getValorLiquido() ?: throw Exception("Erro ao pegar valor liquido"),
        getNumero() ?: throw Exception("Erro ao pegar numero da nota"),
        TipoDuplicata.Nfse,
        chaveAcesso = getChaveAcesso(),
    )

    fun getChaveAcesso(): String? =
        id.takeIf { it.isNotBlank() }
            ?: xml?.nfse?.infNfse?.attributes?.Id
            ?: xml?.nfse?.infNfse?.declaracaoPrestacaoServico
                ?.infDeclaracaoPrestacaoServico?.attributes?.Id
            ?: xml?.nfse?.infNfse?.codigoVerificacao

    fun getNomePrestador(): String? =
        xml?.nfse?.infNfse?.prestadorServico?.razaoSocial

    fun getcpfCnpjTomador(): String? =
        xml?.nfse?.infNfse?.declaracaoPrestacaoServico
            ?.infDeclaracaoPrestacaoServico?.tomador
            ?.identificacaoTomador?.cpfCnpj?.any()

    fun getNomeTomador(): String? =
        xml?.nfse?.infNfse?.declaracaoPrestacaoServico
            ?.infDeclaracaoPrestacaoServico?.tomador?.razaoSocial

    fun getDataEmissao(): Date? =
        xml?.nfse?.infNfse?.dataEmissao?.toFiscalDate()

    fun getValorLiquido(): BigDecimal? =
        xml?.nfse?.infNfse?.valoresNfse?.valorLiquidoNfse?.let(::BigDecimal)
            ?: getValorBruto()

    fun getValorBruto(): BigDecimal? =
        xml?.nfse?.infNfse?.declaracaoPrestacaoServico
            ?.infDeclaracaoPrestacaoServico?.servico
            ?.valores?.valorServicos?.let(::BigDecimal)

    fun getNumero(): String? = xml?.nfse?.infNfse?.numero

    fun getcpfCnpjPrestador(): String? =
        xml?.nfse?.infNfse?.prestadorServico
            ?.identificacaoPrestador?.cpfCnpj?.any()
}

@JsonIgnoreProperties(ignoreUnknown = true)
class XmlWrapper(
    @JsonProperty("Nfse")
    var nfse: Nfse? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
class Nfse(
    @JsonProperty("InfNfse")
    var infNfse: InfNfse? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
class InfNfse(
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
    @JsonProperty("DeclaracaoPrestacaoServico")
    var declaracaoPrestacaoServico: DeclaracaoPrestacaoServico? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
class InfNfseAttributes(
    var Id: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
class ValoresNfse(
    @JsonProperty("ValorLiquidoNfse")
    var valorLiquidoNfse: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
class PrestadorServico(
    @JsonProperty("IdentificacaoPrestador")
    var identificacaoPrestador: IdentificacaoPrestador? = null,
    @JsonProperty("RazaoSocial")
    var razaoSocial: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
class IdentificacaoPrestador(
    @JsonProperty("CpfCnpj")
    var cpfCnpj: CpfCnpj? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
class CpfCnpj(
    @JsonProperty("Cnpj")
    var cnpj: String? = null,
    @JsonProperty("Cpf")
    var cpf: String? = null,
) {
    fun any(): String = cnpj ?: cpf
        ?: throw Exception("Tanto CPF quanto CNPJ estao vazios")
}

@JsonIgnoreProperties(ignoreUnknown = true)
class DeclaracaoPrestacaoServico(
    @JsonProperty("InfDeclaracaoPrestacaoServico")
    var infDeclaracaoPrestacaoServico: InfDeclaracaoPrestacaoServico? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
class InfDeclaracaoPrestacaoServico(
    @JsonProperty("@attributes")
    var attributes: DpsAttributes? = null,
    @JsonProperty("Servico")
    var servico: Servico? = null,
    @JsonProperty("Tomador")
    var tomador: Tomador? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
class DpsAttributes(
    var Id: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
class Servico(
    @JsonProperty("Valores")
    var valores: ValoresServico? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
class ValoresServico(
    @JsonProperty("ValorServicos")
    var valorServicos: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
class Tomador(
    @JsonProperty("IdentificacaoTomador")
    var identificacaoTomador: IdentificacaoTomador? = null,
    @JsonProperty("RazaoSocial")
    var razaoSocial: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
class IdentificacaoTomador(
    @JsonProperty("CpfCnpj")
    var cpfCnpj: CpfCnpj? = null,
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
