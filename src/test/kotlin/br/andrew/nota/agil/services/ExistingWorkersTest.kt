package br.andrew.nota.agil.services

import br.andrew.nota.agil.infrastructure.JobsConfiguration
import br.andrew.nota.agil.model.*
import br.andrew.nota.agil.model.tasks.*
import br.andrew.nota.agil.repository.*
import br.andrew.nota.agil.scheduling.SendToWorkFlowSchedule
import br.andrew.nota.agil.scheduling.core.DynamicTasksConfig
import br.andrew.nota.agil.support.anyValue as any
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyInt
import org.springframework.context.support.GenericApplicationContext
import org.springframework.scheduling.TaskScheduler

class ExistingWorkersTest {
    @Test
    fun `worker existente deve executar ERP mesmo quando outra task falhar`() {
        val jobs = mock(JobStateRepository::class.java)
        `when`(jobs.save(any())).thenAnswer { it.getArgument<JobState>(0) }
        val tasks = mock(TaskRepository::class.java)
        val service = mock(TaskService::class.java)
        val antiga = Task("pdf", "PDF", TaskStatus.READY, TaskTypes.UploadPdf)
        val erp = Task("erp", "Pagamento", TaskStatus.READY, TaskTypes.ConsultaErp)
        `when`(tasks.findAllByStatus(any(), any())).thenReturn(listOf(antiga, erp))
        doThrow(IllegalStateException("Falha isolada")).`when`(service).executa(antiga)
        val worker = SendToWorkFlowSchedule(mock(TaskScheduler::class.java), jobs, JobsConfiguration(), tasks, service)
        worker.runOnce()
        verify(service).executa(antiga)
        verify(service).executa(erp)
    }

    @Test
    fun `cadastro manual nao deve criar workers Qive e vinculo posterior deve registra los`() {
        GenericApplicationContext().use { context ->
            context.refresh()
            val config = DynamicTasksConfig(context, mock(CompanyRepository::class.java), mock(TaskScheduler::class.java))
            val manual = Company("12345678000190", "Manual", vinculadoQive = false)
            config.companyChanged(CompanyChanged(manual))
            assertEquals(0, context.beanDefinitionCount)
            config.companyChanged(CompanyChanged(manual.copy(vinculadoQive = true)))
            assertEquals(3, context.beanDefinitionCount)
            assertTrue(context.beanDefinitionNames.all { it.endsWith("-${manual.cnpj}") })
            config.companyChanged(CompanyChanged(manual.copy(vinculadoQive = true)))
            assertEquals(3, context.beanDefinitionCount)
        }
    }

    @Test
    fun `desvincular e vincular de novo a empresa religa os jobs do Qive que estavam ligados`() {
        val estados = java.util.concurrent.ConcurrentHashMap<String, JobState>()
        val jobs = mock(JobStateRepository::class.java)
        `when`(jobs.findByEmpresaAndTipo(anyString(), any())).thenAnswer { estados["${it.getArgument<String>(0)}-${it.getArgument<JobsTypes>(1)}"] }
        `when`(jobs.save(any())).thenAnswer { it.getArgument<JobState>(0).also { s -> estados[s.id] = s } }
        `when`(jobs.salvarEnabled(anyString(), anyBoolean())).thenAnswer {
            estados[it.getArgument<String>(0)]?.enabled = it.getArgument<Boolean>(1); 1L
        }
        val cnpj = "12345678000190"
        listOf(JobsTypes.NfeReceived, JobsTypes.NfseReceived, JobsTypes.CteReceived)
            .forEach { jobs.save(JobState(cnpj, it, enabled = true)) }
        val futuro = mock(java.util.concurrent.ScheduledFuture::class.java)
        val agendados = java.util.concurrent.atomic.AtomicInteger()
        // Scheduler real (nao mock): o registerBean casa os argumentos do construtor pelo tipo.
        val scheduler = object : org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler() {
            override fun scheduleAtFixedRate(task: Runnable, startTime: java.time.Instant, period: java.time.Duration):
                java.util.concurrent.ScheduledFuture<*> = futuro.also { agendados.incrementAndGet() }
        }

        GenericApplicationContext().use { context ->
            // Como na aplicacao: processadores de anotacao resolvem o construtor e autowiram o resto.
            org.springframework.context.annotation.AnnotationConfigUtils.registerAnnotationConfigProcessors(context)
            context.beanFactory.registerSingleton("jobStateRepository", jobs)
            context.beanFactory.registerSingleton("jobsConfiguration", JobsConfiguration())
            context.beanFactory.registerSingleton("taskRepository", mock(TaskRepository::class.java))
            context.beanFactory.registerSingleton("qiveApiClient", mock(br.andrew.nota.agil.qive.interfaces.QiveApiClient::class.java))
            context.beanFactory.registerSingleton("travaNotas", mock(TravaNotas::class.java))
            context.refresh()
            val config = DynamicTasksConfig(context, mock(CompanyRepository::class.java), scheduler)
            val empresa = Company(cnpj, "Empresa", vinculadoQive = true)

            config.companyChanged(CompanyChanged(empresa))
            context.beanDefinitionNames.filter { it.endsWith("-$cnpj") }
                .forEach { context.getBean(it, br.andrew.nota.agil.scheduling.core.ScheduledAbstract::class.java) }
            assertEquals(3, agendados.get())

            config.companyChanged(CompanyChanged(empresa.copy(vinculadoQive = false)))
            assertTrue(context.beanDefinitionNames.none { it.endsWith("-$cnpj") })
            verify(futuro, times(3)).cancel(false)
            assertTrue(estados.values.all { it.enabled }, "desvinculo nao grava enabled=false")

            config.companyChanged(CompanyChanged(empresa))
            val religados = context.beanDefinitionNames.filter { it.endsWith("-$cnpj") }
                .map { context.getBean(it, br.andrew.nota.agil.scheduling.core.ScheduledAbstract::class.java) }
            assertEquals(3, religados.size)
            assertTrue(religados.all { it.isRunning() })
            assertEquals(6, agendados.get())
        }
    }

    @Test
    fun `execucao em andamento nao desfaz o stop manual feito enquanto ela rodava`() {
        val cnpj = "12345678000190"
        val id = "$cnpj-${JobsTypes.NfeReceived}"
        // "Banco": cada leitura devolve uma copia, como o Mongo.
        val banco = java.util.concurrent.ConcurrentHashMap<String, JobState>()
        // Criado desligado (o scheduler mock nao agenda); ligado antes da execucao.
        banco[id] = JobState(cnpj, JobsTypes.NfeReceived, enabled = false)
        fun copia(s: JobState) = JobState(s.empresa, s.tipo, s.enabled, s.cursor, s.lastFrom)
        val jobs = mock(JobStateRepository::class.java)
        `when`(jobs.findByEmpresaAndTipo(anyString(), any())).thenAnswer { banco[id]?.let(::copia) }
        `when`(jobs.save(any())).thenAnswer { it.getArgument<JobState>(0).also { s -> banco[s.id] = copia(s) } }
        `when`(jobs.salvarEnabled(anyString(), anyBoolean())).thenAnswer {
            banco[it.getArgument<String>(0)]?.enabled = it.getArgument<Boolean>(1); 1L
        }
        `when`(jobs.salvarProgresso(anyString(), anyInt(), any())).thenAnswer {
            banco[it.getArgument<String>(0)]?.apply { cursor = it.getArgument(1); lastFrom = it.getArgument(2) }; 1L
        }
        val nfe = mock(br.andrew.nota.agil.qive.interfaces.controllers.Nfe::class.java)
        val qive = br.andrew.nota.agil.qive.interfaces.QiveApiClient(nfe, mock(br.andrew.nota.agil.qive.interfaces.controllers.Events::class.java),
            mock(br.andrew.nota.agil.qive.interfaces.controllers.Company::class.java),
            mock(br.andrew.nota.agil.qive.interfaces.controllers.Nfse::class.java), mock(br.andrew.nota.agil.qive.interfaces.controllers.Cte::class.java))
        val job = br.andrew.nota.agil.scheduling.generic.NfeReciveSchedule(mock(TaskScheduler::class.java), Company(cnpj, "Empresa"),
            jobs, JobsConfiguration(), mock(TaskRepository::class.java), qive, mock(TravaNotas::class.java))
        `when`(nfe.listReceived(any(), any(), any(), any(), any(), any(), anyString())).thenAnswer {
            // Admin para o job manualmente enquanto a execucao (que leu enabled=true) ainda roda.
            job.stop()
            br.andrew.nota.agil.qive.interfaces.ReceivedResponse<br.andrew.nota.agil.qive.model.NotaProdutos>(
                emptyList(), null, 0, null, br.andrew.nota.agil.qive.interfaces.Status(200, "ok"))
        }
        banco[id]!!.enabled = true
        val antes = banco[id]!!.lastFrom

        job.runOnce()

        assertFalse(banco[id]!!.enabled, "o stop manual vale")
        assertTrue(banco[id]!!.lastFrom.isAfter(antes), "o progresso da execucao foi gravado")
        assertEquals(0, banco[id]!!.cursor)
        verify(jobs, never()).save(org.mockito.ArgumentMatchers.argThat<JobState> { it.enabled })
    }
}
