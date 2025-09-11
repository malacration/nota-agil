package br.andrew.nota.agil.qive.interfaces.controllers

import br.andrew.nota.agil.qive.interfaces.ReceivedResponse
import org.springframework.web.service.annotation.GetExchange
import org.springframework.web.service.annotation.HttpExchange


@HttpExchange("/company")
interface Company {

    @GetExchange("/")
    fun empresas(): ReceivedResponse<String>

}
