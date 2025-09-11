package br.andrew.nota.agil.softexpert.infrastructure

import br.andrew.nota.agil.softexpert.infrastructure.SoftExpertConfiguration
import br.andrew.nota.agil.softexpert.infrastructure.interfaces.SoftexpertApiClient
import br.andrew.nota.agil.softexpert.infrastructure.interfaces.Workflow
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.http.client.ClientHttpRequestFactory
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.RestClient
import org.springframework.web.client.support.RestClientAdapter
import org.springframework.web.service.invoker.HttpServiceProxyFactory
import java.net.http.HttpClient
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLParameters
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlin.time.Duration

@Configuration
@EnableConfigurationProperties(SoftExpertConfiguration::class)
class SoftexpertWebClientConfig {

    @Bean("softexpertRest")
    fun softexpertRest(props: SoftExpertConfiguration): RestClient =
        RestClient.builder()
            .baseUrl(props.url)
            .defaultHeaders { h ->
                h.accept = listOf(MediaType.APPLICATION_JSON)
                h.contentType = MediaType.APPLICATION_JSON
                h.add("Authorization", props.authorization)
            }
            .defaultStatusHandler(HttpStatusCode::isError) { _, res ->
                val body = res.body.bufferedReader().readText()
                throw ApiException(res.statusCode.value(), body)
            }
            .requestFactory(insecureRequestFactory())
            .build()


    @Bean
    fun softexpertApi(@Qualifier("softexpertRest") rc: RestClient): SoftexpertApiClient {
        val workflow = HttpServiceProxyFactory
            .builder()
            .exchangeAdapter(RestClientAdapter.create(rc))
            .build()
            .createClient(Workflow::class.java)

        return SoftexpertApiClient(workflow)
    }

    private fun insecureRequestFactory(): ClientHttpRequestFactory {
        val trustAll = object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
        }
        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf<TrustManager>(trustAll), SecureRandom())
        }

        val sslParams = SSLParameters().apply {
            setEndpointIdentificationAlgorithm(null)
        }

        val jdkHttpClient = HttpClient.newBuilder()
            .sslContext(sslContext)
            .sslParameters(sslParams)
            .build()

        return JdkClientHttpRequestFactory(jdkHttpClient)
    }
}

class ApiException(val status: Int, body: String) : RuntimeException("HTTP $status: $body")
