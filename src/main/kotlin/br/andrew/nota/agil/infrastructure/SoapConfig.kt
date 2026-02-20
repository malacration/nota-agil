package br.andrew.nota.agil.infrastructure

import br.andrew.wsdl.document.DocumentoBindingStub
import br.andrew.wsdl.document.DocumentoLocator
import br.andrew.wsdl.document.DocumentoPortType
import br.andrew.wsdl.workflow.WorkflowBindingStub
import br.andrew.wsdl.workflow.WorkflowLocator
import br.andrew.wsdl.workflow.WorkflowPortType
import org.apache.axis.AxisProperties
import org.apache.axis.client.Stub
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.util.UriComponentsBuilder
import java.net.URI
import java.net.URL

@Configuration
class SoapConfig(val envrioment : SoftExpertEnvrioment) {

    @Bean
    fun workFlowBinding(): WorkflowPortType {
        return WorkflowBindingStub(
            getUrl(WorkflowLocator().workflowPortAddress),
            WorkflowLocator()
        ).also {
            it.username = envrioment.user
            it.password = envrioment.password
            configureAxisSsl(it)
        }
    }

    @Bean
    fun documentBinding(): DocumentoPortType {
        return DocumentoBindingStub(
            getUrl(DocumentoLocator().documentoPortAddress),
            DocumentoLocator()
        ).also {
            it.username = envrioment.user
            it.password = envrioment.password
            configureAxisSsl(it)
        }
    }

    fun getUrl(initialUri : String): URL {
        val configuredHost = envrioment.host.trim()
        if (configuredHost.isBlank()) return URL(initialUri)

        val initial = URI.create(initialUri)
        val result = if (configuredHost.startsWith("http://") || configuredHost.startsWith("https://")) {
            val override = URI.create(configuredHost)
            UriComponentsBuilder.newInstance()
                .scheme(override.scheme ?: initial.scheme)
                .host(override.host ?: override.authority)
                .apply {
                    if (override.port != -1) {
                        port(override.port)
                    } else if (initial.port != -1) {
                        port(initial.port)
                    }
                }
                .path(
                    if (override.path.isNullOrBlank() || override.path == "/") {
                        initial.path
                    } else {
                        override.path
                    },
                )
                .query(initial.query)
                .build(true)
                .toUriString()
        } else {
            UriComponentsBuilder.fromUriString(initialUri)
                .host(configuredHost)
                .build(true)
                .toUriString()
        }

        return URL(result)
    }

    private fun configureAxisSsl(stub: Stub) {
        val socketFactory = InsecureAxisSocketFactory::class.java.name
        AxisProperties.setProperty("axis.socketSecureFactory", socketFactory)
        stub._setProperty(
            "axis.socketSecureFactory",
            socketFactory,
        )
    }
}
