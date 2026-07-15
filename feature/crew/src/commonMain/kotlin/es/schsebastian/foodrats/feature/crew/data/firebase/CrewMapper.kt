package es.schsebastian.foodrats.feature.crew.data.firebase

import es.schsebastian.foodrats.core.domain.crew.CrewScoreStyle
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.result.getOrNull
import es.schsebastian.foodrats.feature.crew.domain.error.CrewError
import es.schsebastian.foodrats.feature.crew.domain.model.Crew
import es.schsebastian.foodrats.feature.crew.domain.model.CrewCode
import es.schsebastian.foodrats.feature.crew.domain.model.CrewTagline
import es.schsebastian.foodrats.feature.crew.domain.model.JoinRequest
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
    // silently ignore malformed tagline on read
    val tagline = tagline?.let { CrewTagline.of(it).getOrNull() }
    // welcomeMessage is optional: absent/blank → null; too-long silently ignored on read.
    // silently ignore malformed message on read
    val welcomeMessage = welcomeMessage?.let { WelcomeMessage.of(it).getOrNull() }
    // weeklyChallenge is optional: absent/blank → null; too-long silently ignored on read.
    // weeklyChallengeSetAt is optional: absent → null (tolerated if challenge is also null).
    // silently ignore malformed challenge on read
    val weeklyChallenge = weeklyChallenge?.let { WeeklyChallenge.of(it).getOrNull() }
    val weeklyChallengeSetAt = weeklyChallengeSetAtMillis?.let { ms ->
        Instant.fromEpochMilliseconds(ms)
    }
    // scoreStyle: absent / unknown string ⇒ Stars (pre-C8 crews keep legacy behavior).
    val scoreStyle = when (scoreStyle) {
        "emoji"   -> CrewScoreStyle.Emoji
        "numeric" -> CrewScoreStyle.Numeric
        else      -> CrewScoreStyle.Stars  // "stars" or unknown
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
            scoreStyle = scoreStyle,
            // bannerPath: absent/null in old docs → null (no migration needed, tolerant deserialization).
            bannerPath = bannerPath?.ifBlank { null },
            // bannerToken: absent/null in old docs (incl. legacy fixed-path banners) → null, so the
            // banner is treated as MUTABLE. Non-null only for content-versioned banners set post-IMAGE-2.
            bannerToken = bannerToken?.ifBlank { null },
            // bannerFocalY: absent/null → 0.5 (center); clamped to the valid 0..1 range defensively.
            bannerFocalY = bannerFocalY?.toFloat()?.coerceIn(0f, 1f) ?: 0.5f,
        ),
    )
}

/**
 * Maps a [JoinRequestDto] to a [JoinRequest]. Returns `null` for an incomplete/malformed doc
 * (missing or unparseable accountId, or missing timestamp) so the owner's pending list silently
 * skips junk rather than failing the whole stream.
 */
fun JoinRequestDto.toDomain(): JoinRequest? {
    val rawId = accountId ?: return null
    val accountId = (AccountId.of(rawId) as? Result.Ok)?.value ?: return null
    val requestedAtMs = requestedAtEpochMs ?: return null
    return JoinRequest(accountId = accountId, requestedAt = Instant.fromEpochMilliseconds(requestedAtMs))
}

/** Maps a [CrewScoreStyle] to the Firestore string stored in [CrewDto.scoreStyle]. */
fun CrewScoreStyle.toDto(): String = when (this) {
    CrewScoreStyle.Stars   -> "stars"
    CrewScoreStyle.Emoji   -> "emoji"
    CrewScoreStyle.Numeric -> "numeric"
}
