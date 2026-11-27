package br.andrew.nota.agil.model

import br.andrew.nota.agil.model.erp.Erp
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document


@Document("company")
data class Company(
    @Id val cnpj : String,
    val nome : String,
    val erpPagamento: Erp? = null,
    val vinculadoQive: Boolean = true,
    /** Codigo da empresa no Dealer (Titulo_EmpresaCod); sem ele a empresa nao lista notas do Dealer. */
    val codigoDealer: Int? = null,
)

data class CompanyChanged(val company: Company)
