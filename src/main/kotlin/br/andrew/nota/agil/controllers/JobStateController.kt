package br.andrew.nota.agil.controllers

import br.andrew.nota.agil.repository.JobStateRepository
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.util.Date



@RestController
@RequestMapping("/job-state")
class JobStateController(val repository: JobStateRepository) {


    @PutMapping("{id}")
    fun update(@RequestParam cursor : Int, @RequestParam lastFrom : BigDecimal, @PathVariable id : String){
        repository.save(repository.findById(id).get().also {
            it.cursor = cursor
            it.lastFrom = parseInstantFromClient(lastFrom)
        })
    }
}

fun parseInstantFromClient(bd: BigDecimal): Instant {
    val looksLikeMillis = bd.scale() <= 3 && bd >= BigDecimal("1000000000000")

    return if (looksLikeMillis) {
        Instant.ofEpochMilli(bd.setScale(0, RoundingMode.DOWN).longValueExact())
    } else {
        val secs = bd.setScale(0, RoundingMode.DOWN).longValueExact()
        val nanos = bd.subtract(BigDecimal.valueOf(secs))
            .movePointRight(9)                       // fração de segundo -> nanos
            .setScale(0, RoundingMode.DOWN)
            .intValueExact()
        Instant.ofEpochSecond(secs, nanos.toLong())
    }
}