package br.andrew.nota.agil.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document


@Document("company")
class Company(
    @Id val cnpj : String,
    val nome : String) {

}