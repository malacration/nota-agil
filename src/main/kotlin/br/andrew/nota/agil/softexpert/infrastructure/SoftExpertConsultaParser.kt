package br.andrew.nota.agil.softexpert.infrastructure

import br.andrew.nota.agil.softexpert.infrastructure.interfaces.SoftExpertUser
import br.andrew.nota.agil.softexpert.infrastructure.interfaces.SoftExpertUserVinculo
import br.andrew.nota.agil.softexpert.infrastructure.interfaces.WorkflowDetails
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException
import org.w3c.dom.Element
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import java.io.StringReader
import java.io.StringWriter
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.stream.XMLOutputFactory

@Component
class SoftExpertConsultaParser {
    fun envelope(namespace: String, operacao: String, parametro: String, valor: String): String {
        val buffer = StringWriter()
        val xml = XMLOutputFactory.newFactory().createXMLStreamWriter(buffer)
        try {
            xml.writeStartDocument("UTF-8", "1.0")
            xml.writeStartElement("soap", "Envelope", SOAP_NAMESPACE)
            xml.writeNamespace("soap", SOAP_NAMESPACE)
            xml.writeNamespace("urn", namespace)
            xml.writeStartElement("soap", "Body", SOAP_NAMESPACE)
            xml.writeStartElement("urn", operacao, namespace)
            xml.writeStartElement("urn", parametro, namespace)
            xml.writeCharacters(valor)
            repeat(4) { xml.writeEndElement() }
            xml.writeEndDocument()
            xml.flush()
            return buffer.toString()
        } finally {
            xml.close()
        }
    }

    fun usuario(xml: String): SoftExpertUser {
        val resposta = resposta(xml, "urn:admin", "getUserDataResponse")
        val dados = resposta.child("data")?.child("item") ?: invalida("Dados do usuario ausentes")
        return SoftExpertUser(
            matricula = dados.text("Usuário") ?: invalida("Matricula ausente na resposta do usuario"),
            login = dados.text("Login"), nome = dados.text("Nome"),
            email = dados.text("E-mail"), telefone = dados.text("Telefone"),
            areas = dados.vinculos("department", "iddepartment", "nmdepartment"),
            funcoes = dados.vinculos("position", "idposition", "nmposition"),
            equipes = dados.vinculos("team", "idteam", "nmteam"),
        )
    }

    fun instancia(xml: String, workflowId: String): WorkflowDetails {
        val resposta = resposta(xml, "urn:workflow", "getWorkflowResponse")
        return WorkflowDetails(
            workflowId = workflowId,
            title = resposta.text("WorkflowTitle") ?: resposta.text("Title"),
            processId = resposta.text("ProcessID"), instanceStatus = resposta.text("InstanceStatus"),
            workflowStatus = resposta.text("WorkflowStatus"), starterUser = resposta.text("StarterUser"),
            starterExternalUser = resposta.text("StarterExternalUser"), startDate = resposta.text("StartDate"),
        )
    }

    private fun resposta(xml: String, namespace: String, operacao: String): Element {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isXIncludeAware = false
            isExpandEntityReferences = false
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
            setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
        }
        val root = try {
            factory.newDocumentBuilder().parse(InputSource(StringReader(xml))).documentElement
        } catch (_: SAXException) {
            invalida("XML invalido recebido do SoftExpert")
        }
        if (root.localName != "Envelope" || root.namespaceURI != SOAP_NAMESPACE) invalida("Envelope SOAP ausente")
        val body = root.child("Body") ?: invalida("Corpo SOAP ausente")
        body.child("Fault")?.let { invalida(it.text("faultstring") ?: "Falha SOAP no SoftExpert") }
        val resposta = body.child(operacao)?.takeIf { it.namespaceURI == namespace }
            ?: invalida("Operacao SOAP inesperada")
        if (resposta.text("Status") != "SUCCESS") {
            val status = when (resposta.text("Code")) {
                "12" -> HttpStatus.NOT_FOUND
                "56" -> HttpStatus.BAD_REQUEST
                "71" -> HttpStatus.FORBIDDEN
                else -> HttpStatus.BAD_GATEWAY
            }
            throw ResponseStatusException(status, resposta.text("Detail") ?: "Consulta recusada pelo SoftExpert")
        }
        return resposta
    }

    private fun Element.children(nome: String): List<Element> = (0 until childNodes.length)
        .mapNotNull { childNodes.item(it) as? Element }
        .filter { it.localName == nome || it.nodeName == nome }

    private fun Element.child(nome: String): Element? = children(nome).firstOrNull()
    private fun Element.text(nome: String): String? = child(nome)?.textContent?.trim()?.takeIf { it.isNotEmpty() }
    private fun Element.vinculos(grupo: String, id: String, nome: String): List<SoftExpertUserVinculo> =
        child(grupo)?.children("item")?.map { SoftExpertUserVinculo(it.text(id), it.text(nome)) } ?: emptyList()

    private fun invalida(mensagem: String): Nothing = throw ResponseStatusException(HttpStatus.BAD_GATEWAY, mensagem)

    companion object {
        private const val SOAP_NAMESPACE = "http://schemas.xmlsoap.org/soap/envelope/"
    }
}
