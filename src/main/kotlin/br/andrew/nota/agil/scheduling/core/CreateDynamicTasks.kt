package br.andrew.nota.agil.scheduling.core

import br.andrew.nota.agil.model.Company
import br.andrew.nota.agil.model.CompanyChanged
import br.andrew.nota.agil.repository.CompanyRepository
import br.andrew.nota.agil.scheduling.generic.CteReciveSchedule
import br.andrew.nota.agil.scheduling.generic.NfeReciveSchedule
import br.andrew.nota.agil.scheduling.generic.NfseReciveSchedule
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.annotation.Configuration
import org.springframework.context.event.EventListener
import org.springframework.context.support.GenericApplicationContext
import org.springframework.scheduling.TaskScheduler

@Configuration
class DynamicTasksConfig(
    private val ctx: GenericApplicationContext,
    private val repository: CompanyRepository,
    private val task: TaskScheduler) {

    val jobsByCompany= listOf(
        NfseReciveSchedule::class.java,
        NfeReciveSchedule::class.java,
        CteReciveSchedule::class.java,
    )

    @EventListener(ApplicationReadyEvent::class)
    fun register() {
        repository.save(Company("all","all", vinculadoQive = false))
        repository.findAll().filter { it.cnpj != "all" }.forEach { empresa ->
            registerCompany(empresa)
        }
    }

    @EventListener
    fun companyChanged(event: CompanyChanged) = registerCompany(event.company)

    @Synchronized
    fun registerCompany(empresa: Company) {
        if (empresa.cnpj == "all") return
        jobsByCompany.forEach { beanClass ->
            val beanName = "${beanClass.name}-${empresa.cnpj}"
            if (empresa.vinculadoQive && !ctx.containsBean(beanName)) {
                ctx.registerBean(beanName, beanClass, task, empresa)
            } else if (!empresa.vinculadoQive && ctx.containsBeanDefinition(beanName)) {
                // pause, nao stop: stop grava enabled=false e o job nao voltaria ao religar a empresa.
                ctx.getBean(beanName, ScheduledAbstract::class.java).pause()
                ctx.removeBeanDefinition(beanName)
            }
        }
    }
}
