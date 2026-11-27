package br.andrew.nota.agil.softexpert

import br.andrew.nota.agil.softexpert.infrastructure.SoftExpertConsultaParser
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory

class SoftExpertConsultaParserTest {
    private val parser = SoftExpertConsultaParser()

    @Test
    fun `deve ler estrutura real do usuario e preservar matricula com zeros`() {
        val usuario = parser.usuario(USER_SOAP)
        assertEquals("001", usuario.matricula)
        assertEquals("pessoa.teste", usuario.login)
        assertEquals("Pessoa de Teste", usuario.nome)
        assertEquals("pessoa@example.invalid", usuario.email)
        assertNull(usuario.telefone)
        assertEquals("TI", usuario.areas.single().id)
        assertEquals("Tecnologia", usuario.areas.single().nome)
        assertEquals("Analista", usuario.funcoes.single().nome)
        assertEquals(listOf("EQ1", "EQ2"), usuario.equipes.map { it.id })
    }

    @Test
    fun `deve aceitar usuario sem vinculos opcionais`() {
        val usuario = parser.usuario(soap("urn:admin", "getUserDataResponse", """
            <data><item><Usuário>001</Usuário><Nome>Teste</Nome></item></data><Status>SUCCESS</Status>
        """))
        assertTrue(usuario.areas.isEmpty())
        assertTrue(usuario.funcoes.isEmpty())
        assertTrue(usuario.equipes.isEmpty())
    }

    @Test
    fun `deve ler detalhes da instancia com WorkflowTitle ou Title`() {
        listOf("WorkflowTitle", "Title").forEach { campo ->
            val detalhes = parser.instancia(soap("urn:workflow", "getWorkflowResponse", """
                <Status>SUCCESS</Status><$campo>Nota de teste</$campo><ProcessID>FXP-NA</ProcessID>
                <InstanceStatus>1</InstanceStatus><WorkflowStatus>Em analise</WorkflowStatus>
                <StarterUser>001</StarterUser><StarterExternalUser/>
                <StartDate>2026-11-11 10:00:00</StartDate>
            """), "FXP-NA001")
            assertEquals("FXP-NA001", detalhes.workflowId)
            assertEquals("Nota de teste", detalhes.title)
            assertEquals("FXP-NA", detalhes.processId)
            assertEquals("001", detalhes.starterUser)
            assertNull(detalhes.starterExternalUser)
        }
    }

    @Test
    fun `deve converter falhas logicas do SE sem devolver 200 falso`() {
        mapOf("12" to HttpStatus.NOT_FOUND, "56" to HttpStatus.BAD_REQUEST,
            "71" to HttpStatus.FORBIDDEN, "999" to HttpStatus.BAD_GATEWAY).forEach { (codigo, status) ->
            val erro = assertThrows<ResponseStatusException> {
                parser.usuario(soap("urn:admin", "getUserDataResponse",
                    "<Status>FAILURE</Status><Code>$codigo</Code><Detail>Consulta recusada</Detail>"))
            }
            assertEquals(status, erro.statusCode)
            assertEquals("Consulta recusada", erro.reason)
        }
    }

    @Test
    fun `deve rejeitar SOAP Fault XML invalido resposta inesperada e entidades externas`() {
        val respostas = listOf(
            "<invalido>",
            "<!DOCTYPE x [<!ENTITY e SYSTEM 'file:///nao-deve-ser-lido'>]>" + USER_SOAP,
            soap("urn:admin", "getUserDataResponse", "<Status>SUCCESS</Status><data/>"),
            soap("urn:workflow", "getWorkflowResponse", "<Status>SUCCESS</Status>"),
            """<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"><s:Body>
                <s:Fault><faultstring>Sem acesso</faultstring></s:Fault></s:Body></s:Envelope>""",
        )
        respostas.forEach { xml ->
            assertEquals(HttpStatus.BAD_GATEWAY,
                assertThrows<ResponseStatusException> { parser.usuario(xml) }.statusCode)
        }
    }

    @Test
    fun `envelope deve escapar identificador sem permitir injecao XML`() {
        val id = "001<&\"</urn:iduser>"
        val xml = parser.envelope("urn:admin", "getUserData", "iduser", id)
        val document = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(InputSource(StringReader(xml)))
        val campos = document.getElementsByTagNameNS("urn:admin", "iduser")
        assertEquals(1, campos.length)
        assertEquals(id, campos.item(0).textContent)
    }

    companion object {
        fun soap(namespace: String, operacao: String, campos: String) = """
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/">
              <s:Body><$operacao xmlns="$namespace">$campos</$operacao></s:Body>
            </s:Envelope>
        """.trimIndent()

        val USER_SOAP = soap("urn:admin", "getUserDataResponse", """
            <data><item><Usuário>001</Usuário><Login>pessoa.teste</Login><Nome>Pessoa de Teste</Nome>
              <E-mail>pessoa@example.invalid</E-mail><Telefone/>
              <department><item><iddepartment>TI</iddepartment><nmdepartment>Tecnologia</nmdepartment></item></department>
              <position><item><idposition>ANALISTA</idposition><nmposition>Analista</nmposition></item></position>
              <team><item><idteam>EQ1</idteam><nmteam>Equipe Um</nmteam></item>
                    <item><idteam>EQ2</idteam><nmteam>Equipe Dois</nmteam></item></team>
            </item></data><return>1</return><Status>SUCCESS</Status><Detail>Executado com sucesso</Detail><Code>1</Code>
        """)
    }
}
