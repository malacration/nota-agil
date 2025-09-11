package br.andrew.nota.agil.softexpert.infrastructure.interfaces

import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.service.annotation.GetExchange
import org.springframework.web.service.annotation.HttpExchange
import org.springframework.web.service.annotation.PostExchange

@HttpExchange("/apigateway/softexpert/rest")
//@HttpExchange("/apigateway/se/exp/chatbot/api")
interface Workflow {

    @GetExchange("/instance.php")
    fun search(
        @RequestParam("cdUser") cdUser : Int,
        @RequestParam("cdProduct") cdProduct: Number = 39,
        @RequestParam("fgLanguage") fgLanguage : Int? = null,
        @RequestParam("fgStatus") fgStatus : Int? = null,
        @RequestParam("qtLimit") qtLimit : Int? = null,
    ): String

    @PostExchange("/instance.php")
    fun create(
        @RequestParam("idProcess") idProcess : String,
        @RequestParam("nmInstance") titulo : String,
        @RequestBody body: String? = null,
        @RequestParam("cdProduct") cdProduct: Number = 39,
    ): String

}
