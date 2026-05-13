package br.andrew.nota.agil.qive.infrastructure

import br.andrew.nota.agil.qive.interfaces.ReceivedResponse
import br.andrew.nota.agil.qive.model.NotaProdutos
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.time.ZoneOffset
import java.util.Date

@Component
class QiveNfeReceivedClient(
    @Qualifier("qiveRest") private val restClient: RestClient,
    private val parser: QiveNfeReceivedParser,
) {

    fun listReceived(
        cnpj: String,
        cursor: Int? = null,
        from: Date? = null,
        to: Date? = null,
    ): ReceivedResponse<NotaProdutos> {
        val responseBody = restClient.get()
            .uri { builder ->
                builder.path("/nfe/received")
                    .queryParam("cnpj[]", cnpj)
                    .queryParam("format_type", "JSON")
                cursor?.let { builder.queryParam("cursor", it) }
                from?.let { builder.queryParam("created_at[from]", it) }
                to?.let { builder.queryParam("created_at[to]", it) }
                builder.build()
            }
            .retrieve()
            .body(String::class.java)
            ?: throw QiveResponseBodyCaptureException(
                endpoint = "/nfe/received",
                parametros = buildParametros(cnpj, cursor, from, to),
                responseBody = "",
                cause = IllegalStateException("Resposta da Qive veio sem body"),
            )

        return parser.parseListReceived(
            body = responseBody,
            endpoint = "/nfe/received",
            parametros = buildParametros(cnpj, cursor, from, to),
        )
    }

    private fun buildParametros(
        cnpj: String,
        cursor: Int?,
        from: Date?,
        to: Date?,
    ): Map<String, Any?> = mapOf(
        "cnpj" to cnpj,
        "cursor" to cursor,
        "from" to from?.toInstant()?.atOffset(ZoneOffset.UTC)?.toString(),
        "to" to to?.toInstant()?.atOffset(ZoneOffset.UTC)?.toString(),
    )
}

@Component
class QiveNfeReceivedParser(
    private val objectMapper: ObjectMapper,
) {

    fun parseListReceived(
        body: String,
        endpoint: String,
        parametros: Map<String, Any?>,
    ): ReceivedResponse<NotaProdutos> {
        return try {
            objectMapper.readValue(body, object : TypeReference<ReceivedResponse<NotaProdutos>>() {})
        } catch (e: Exception) {
            throw QiveResponseBodyCaptureException(
                endpoint = endpoint,
                parametros = parametros,
                responseBody = body,
                cause = e,
            )
        }
    }
}

class QiveResponseBodyCaptureException(
    val endpoint: String,
    val parametros: Map<String, Any?>,
    val responseBody: String,
    cause: Throwable,
) : RuntimeException("Falha ao desserializar resposta da Qive em $endpoint", cause)
