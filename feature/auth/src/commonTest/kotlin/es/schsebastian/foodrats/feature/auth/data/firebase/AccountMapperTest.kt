package es.schsebastian.foodrats.feature.auth.data.firebase

import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.result.Result
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class AccountMapperTest {

    private fun aid(raw: String): AccountId = (AccountId.of(raw) as Result.Ok).value

    private val validDto = AccountDto(
        id = "uid-1",
        handle = "sam",
        displayName = "Sam Cook",
        avatarPath = "avatars/uid-1.jpg",
        createdAtEpochMs = 1_700_000_000_000L,
        dataConsentVersion = 2,
        dataConsentGrantedAtEpochMs = 1_700_000_500_000L,
    )

    @Test fun toAccount_succeeds_on_well_formed_dto() {
        val account = validDto.toAccount()!!
        assertEquals(aid("uid-1"), account.id)
        assertEquals("sam", account.handle)
        assertEquals("Sam Cook", account.displayName)
        // toAccount carries the avatar PATH on avatarUrl (resolution happens downstream).
        assertEquals("avatars/uid-1.jpg", account.avatarUrl)
        assertEquals(2, account.dataConsentVersion)
        assertEquals(Instant.fromEpochMilliseconds(1_700_000_500_000L), account.dataConsentGrantedAt)
    }

    @Test fun toAccount_returns_null_when_id_is_null() {
        assertNull(validDto.copy(id = null).toAccount())
    }

    @Test fun toAccount_returns_null_when_id_is_blank() {
        // AccountId.of trims and rejects blank → mapper drops the row.
        assertNull(validDto.copy(id = "   ").toAccount())
    }

    @Test fun toAccount_defaults_blank_handle_and_display_name_when_null() {
        val account = validDto.copy(handle = null, displayName = null).toAccount()!!
        assertEquals("", account.handle)
        assertEquals("", account.displayName)
    }

    @Test fun toAccount_never_surfaces_email_from_the_public_doc() {
        // Email is PII owned by Firebase Auth; the public account doc must not carry it.
        assertNull(validDto.toAccount()!!.email)
    }

    @Test fun toAccount_passes_null_avatar_through() {
        assertNull(validDto.copy(avatarPath = null).toAccount()!!.avatarUrl)
    }

    @Test fun toAccount_defaults_consent_fields_when_absent() {
        // DTO defaults (version 0, null granted-at) mean "no consent recorded".
        val account = AccountDto(id = "uid-2").toAccount()!!
        assertEquals(0, account.dataConsentVersion)
        assertNull(account.dataConsentGrantedAt)
    }
}
