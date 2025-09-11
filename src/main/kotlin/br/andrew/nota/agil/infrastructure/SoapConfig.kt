package br.andrew.nota.agil.infrastructure

import br.andrew.nota.agil.infrastructure.SoftExpertEnvrioment
import br.andrew.wsdl.document.DocumentoBindingStub
import br.andrew.wsdl.document.DocumentoLocator
import br.andrew.wsdl.document.DocumentoPortType
import br.andrew.wsdl.workflow.WorkflowBindingStub
import br.andrew.wsdl.workflow.WorkflowLocator
import br.andrew.wsdl.workflow.WorkflowPortType
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.util.UriComponentsBuilder
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
        }
    }

    fun getUrl(initialUri : String): URL {
        val builder = UriComponentsBuilder.fromUriString(initialUri)
        if(envrioment.host != "windson")
            builder.host(envrioment.host)
        return URL(builder.toUriString())
    }
}