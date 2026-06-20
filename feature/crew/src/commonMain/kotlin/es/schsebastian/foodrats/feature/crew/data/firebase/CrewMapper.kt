package es.schsebastian.foodrats.feature.crew.data.firebase

import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.feature.crew.domain.error.CrewError
import es.schsebastian.foodrats.feature.crew.domain.model.Crew
import es.schsebastian.foodrats.feature.crew.domain.model.CrewCode
import es.schsebastian.foodrats.feature.crew.domain.model.CrewTagline
import es.schsebastian.foodrats.feature.crew.domain.model.Member
import es.schsebastian.foodrats.feature.crew.domain.model.WelcomeMessage
import es.schsebastian.foodrats.feature.crew.domain.model.WeeklyChallenge
import kotlin.time.Instant

fun CrewDto.toDomain(): Result<Crew, CrewError> {
    val id = id ?: return Result.failure(CrewError.Backend.Unavailable)
    val name = name ?: return Result.failure(CrewError.Backend.Unavailable)
    val ownerIdRaw = ownerId ?: return Result.failure(CrewError.Backend.Unavailable)
    val createdAtMs = createdAtEpochMs ?: return Result.failure(CrewError.Backend.Unavailable)
    val codeStr = code ?: return Result.failure(CrewError.Backend.Unavailable)
    val parsedCode = when (val c = CrewCode.of(codeStr)) {
        is Result.Err -> return Result.failure(c.error)
        is Result.Ok  -> c.value
    }
    val crewId = when (val r = CrewId.of(id)) {
        is Result.Err -> return Result.failure(CrewError.Backend.Unavailable)
        is Result.Ok  -> r.value
    }
    val ownerId = when (val r = AccountId.of(ownerIdRaw)) {
        is Result.Err -> return Result.failure(CrewError.Backend.Unavailable)
        is Result.Ok  -> r.value
    }
    val members = memberIds.mapNotNull { mid ->
        val info = this.members[mid] ?: return@mapNotNull null
        val joined = info.joinedAtEpochMs ?: return@mapNotNull null
        val accountId = (AccountId.of(mid) as? Result.Ok)?.value ?: return@mapNotNull null
        Member(
            accountId = accountId,
            joinedAt = Instant.fromEpochMilliseconds(joined),
        )
    }
    // tagline is optional: absent/blank → null; too-long silently truncated on read (shouldn't
    // happen for well-formed data, but tolerant deserialization is the pre-launch policy).
    val tagline = tagline?.let { raw ->
        when (val t = CrewTagline.of(raw)) {
            is Result.Ok  -> t.value
            is Result.Err -> null   // silently ignore malformed tagline on read
        }
    }
    // welcomeMessage is optional: absent/blank → null; too-long silently ignored on read.
    val welcomeMessage = welcomeMessage?.let { raw ->
        when (val m = WelcomeMessage.of(raw)) {
            is Result.Ok  -> m.value
            is Result.Err -> null   // silently ignore malformed message on read
        }
    }
    // weeklyChallenge is optional: absent/blank → null; too-long silently ignored on read.
    // weeklyChallengeSetAt is optional: absent → null (tolerated if challenge is also null).
    val weeklyChallenge = weeklyChallenge?.let { raw ->
        when (val c = WeeklyChallenge.of(raw)) {
            is Result.Ok  -> c.value
            is Result.Err -> null   // silently ignore malformed challenge on read
        }
    }
    val weeklyChallengeSetAt = weeklyChallengeSetAtMillis?.let { ms ->
        Instant.fromEpochMilliseconds(ms)
    }
    return Result.success(
        Crew.of(
            id = crewId,
            name = name,
            code = parsedCode,
            ownerId = ownerId,
            createdAt = Instant.fromEpochMilliseconds(createdAtMs),
            members = members,
            blindVoting = blindVoting,
            tagline = tagline,
            welcomeMessage = welcomeMessage,
            weeklyChallenge = weeklyChallenge,
            weeklyChallengeSetAt = weeklyChallengeSetAt,
        ),
    )
}
