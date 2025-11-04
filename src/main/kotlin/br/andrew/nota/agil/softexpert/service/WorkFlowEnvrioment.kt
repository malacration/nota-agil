package br.andrew.nota.agil.softexpert.service

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@ConfigurationProperties(prefix = "softexpert.workflow")
class WorkFlowEnvrioment(val idProcess : String, val tableName : String) {
}