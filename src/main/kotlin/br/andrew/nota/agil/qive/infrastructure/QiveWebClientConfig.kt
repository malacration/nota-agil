package br.andrew.nota.agil.qive.infrastructure

import br.andrew.nota.agil.qive.interfaces.QiveApiClient
import br.andrew.nota.agil.qive.interfaces.controllers.Company
import br.andrew.nota.agil.qive.interfaces.controllers.Cte
import br.andrew.nota.agil.qive.interfaces.controllers.Events
import br.andrew.nota.agil.qive.interfaces.controllers.Nfe
import br.andrew.nota.agil.qive.interfaces.controllers.Nfse
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.web.client.RestClient
import org.springframework.web.client.support.RestClientAdapter
import org.springframework.web.service.invoker.HttpServiceProxyFactory

@Configuration
@EnableConfigurationProperties(QiveConfiguration::class)
class QiveWebClientConfig {

    @Bean("qiveRest")
    fun arquiveiRest(props: QiveConfiguration): RestClient =
        RestClient.builder()
            .baseUrl(props.url)
            .defaultHeaders { h ->
                h.accept = listOf(MediaType.APPLICATION_JSON)
                h.contentType = MediaType.APPLICATION_JSON
                h.add("X-API-ID", props.id)
                h.add("X-API-KEY", props.key)
                if(props.permission != null){
                    h.add("X-API-PERMISSION-ID", props.permission)
                    h.add("X-PERMISSION-ID", props.permission)
                }
            }
            .defaultStatusHandler(HttpStatusCode::isError) { _, res ->
                val body = res.body.bufferedReader().readText()
                throw ApiException(res.statusCode.value(), body)
            }
            .build()


    @Bean
    fun arquiveiApi(@Qualifier("qiveRest") rc: RestClient): QiveApiClient{
        val nfe = HttpServiceProxyFactory
            .builder()
            .exchangeAdapter(RestClientAdapter.create(rc))
            .build()
            .createClient(Nfe::class.java)

        val nfse = HttpServiceProxyFactory
            .builder()
            .exchangeAdapter(RestClientAdapter.create(rc))
            .build()
            .createClient(Nfse::class.java)

        val cte = HttpServiceProxyFactory
            .builder()
            .exchangeAdapter(RestClientAdapter.create(rc))
            .build()
            .createClient(Cte::class.java)

        val events = HttpServiceProxyFactory
            .builder()
            .exchangeAdapter(RestClientAdapter.create(rc))
            .build()
            .createClient(Events::class.java)

        val company = HttpServiceProxyFactory
            .builder()
            .exchangeAdapter(RestClientAdapter.create(rc))
            .build()
            .createClient(Company::class.java)

        return QiveApiClient(nfe,events,company,nfse,cte)
    }
}

class ApiException(val status: Int, body: String) : RuntimeException("HTTP $status: $body")
