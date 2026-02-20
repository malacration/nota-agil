package br.andrew.nota.agil.qive.interfaces

import br.andrew.nota.agil.qive.interfaces.controllers.Company
import br.andrew.nota.agil.qive.interfaces.controllers.Cte
import br.andrew.nota.agil.qive.interfaces.controllers.Events
import br.andrew.nota.agil.qive.interfaces.controllers.Nfe
import br.andrew.nota.agil.qive.interfaces.controllers.Nfse


class QiveApiClient(
    val nfe: Nfe,
    val events: Events,
    val company: Company,
    val nfse: Nfse,
    val cte: Cte,
)


