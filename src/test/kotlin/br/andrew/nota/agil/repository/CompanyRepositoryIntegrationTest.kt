package br.andrew.nota.agil.repository

import br.andrew.nota.agil.model.Company
import br.andrew.nota.agil.model.erp.Erp
import com.mongodb.client.MongoClients
import org.bson.Document
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.data.mongodb.core.MongoTemplate
import java.util.UUID

@EnabledIfEnvironmentVariable(named = "NOTA_AGIL_MONGO_IT_URI", matches = ".+")
class CompanyRepositoryIntegrationTest {
    private val client = MongoClients.create(System.getenv("NOTA_AGIL_MONGO_IT_URI"))
    private val databaseName = "nota_agil_erp_it_${UUID.randomUUID().toString().replace("-", "")}"
    private val mongo = MongoTemplate(client, databaseName)

    @AfterEach
    fun limpar() {
        try { client.getDatabase(databaseName).drop() } finally { client.close() }
    }

    @Test
    fun `empresa antiga sem ERP deve carregar e permitir vincular e remover sem perder dados`() {
        mongo.getCollection("company").insertOne(Document("_id", "12345678000190").append("nome", "Empresa antiga"))
        val antiga = mongo.findById("12345678000190", Company::class.java)!!
        assertNull(antiga.erpPagamento)
        assertTrue(antiga.vinculadoQive)
        mongo.save(antiga.copy(erpPagamento = Erp.SAP))
        assertEquals(Erp.SAP, mongo.findById(antiga.cnpj, Company::class.java)!!.erpPagamento)
        mongo.save(antiga.copy(erpPagamento = null))
        val final = mongo.findById(antiga.cnpj, Company::class.java)!!
        assertNull(final.erpPagamento)
        assertEquals("Empresa antiga", final.nome)
    }
}
