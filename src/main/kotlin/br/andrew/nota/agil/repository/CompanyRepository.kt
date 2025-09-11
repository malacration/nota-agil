package br.andrew.nota.agil.repository

import br.andrew.nota.agil.model.Company
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface CompanyRepository : MongoRepository<Company, String> {

}