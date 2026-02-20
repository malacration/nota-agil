package br.andrew.nota.agil.infrastructure

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component

@Component
@Configuration
class SoftExpertEnvrioment(
    @Value("\${soft.expert.host:\${softexpert.api.url:}}") val host : String,
    @Value("\${soft.expert.user:windson}") val user : String,
    @Value("\${soft.expert.password:windson}") val password : String,
) {

}
