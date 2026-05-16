package es.schsebastian.foodrats.core.domain.session

import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId

data class Session(val accountId: AccountId, val activeCrewId: CrewId?)
