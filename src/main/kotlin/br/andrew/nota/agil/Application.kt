package br.andrew.nota.agil

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
@EnableMongoRepositories
class Application

fun main(args: Array<String>) {
	runApplication<Application>(*args)
}
