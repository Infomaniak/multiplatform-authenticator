package com.infomaniak.multiplatform_authenticator.core.internal.logic

import com.infomaniak.multiplatform_authenticator.core.Account
import com.infomaniak.multiplatform_authenticator.core.internal.db.AccountEntity
import com.infomaniak.multiplatform_authenticator.core.internal.db.AccountEntity.Status
import com.infomaniak.multiplatform_authenticator.core.internal.extensions.firstOrElse
import com.infomaniak.multiplatform_authenticator.core.internal.otp.deleteLegacyAccount
import com.infomaniak.multiplatform_authenticator.core.internal.otp.deleteLegacyDB
import com.infomaniak.multiplatform_authenticator.core.internal.otp.getLegacyAccounts
import kotlinx.coroutines.flow.FlowCollector

context(host: RegistrationHost)
internal suspend fun FlowCollector<Account.Status.NotConnected>.registrationAttempts(notRegisteredAccount: AccountEntity) {
    val passKeyAlreadyRegistered = when (val accountStatus = notRegisteredAccount.status) {
        Status.PasskeyRegistrationPending -> false
        Status.FirstPasskeyAuthenticationPending -> true
        else -> throw IllegalArgumentException("registrationAttempts doesn't support $accountStatus")
    }
    val userId = notRegisteredAccount.id
    withRetries(userId = userId) {
        emit(Account.Status.NotConnected.AttemptingToConnect)
        if (!passKeyAlreadyRegistered) {
            val temporaryToken = host.authenticatorBridge.getTokenFromDatabase(userId)
                ?: error("Temporary token missing in DB for userId=$userId")
            // Just in case orphans passkeys are lying around, we want to make sure to start from a clean state.
            host.authenticatorManager.deleteKeysFor(notRegisteredAccount.id)
            val _ = host.authenticatorManager.registerPasskey(temporaryToken.accessToken, userId)
            host.dao.upsert(notRegisteredAccount.copy(status = Status.FirstPasskeyAuthenticationPending))
            // The DB update above is expected to cause the cancellation & restart of this.
        }

        val token = host.authenticatorManager.getToken(
            clientId = host.clientId,
            userId = userId,
        ).firstOrElse { error("Key not found: ${it.details}") }
        host.authenticatorBridge.attemptPersistingTokenForAccount(userId, token)
        val profile = host.authenticatorManager.getUserProfile(token.accessToken).also { it.apiToken = token }
        host.authenticatorBridge.persistUserProfile(profile)

        cleanupLegacyAccountIfNeeded(userId)
        host.dao.upsert(
            notRegisteredAccount.copy(
                securityScore = profile.preferences.security?.score,
                lastPasswordUpdate = profile.preferences.security?.dateLastChangedPassword,
                status = Status.LoggedIn
            )
        )
    }
}

private suspend fun cleanupLegacyAccountIfNeeded(userId: Long) {
    if (getLegacyAccounts().none { it.userId.toLong() == userId }) return
    deleteLegacyAccount(userId.toString())
    if (getLegacyAccounts().isEmpty()) deleteLegacyDB()
}
