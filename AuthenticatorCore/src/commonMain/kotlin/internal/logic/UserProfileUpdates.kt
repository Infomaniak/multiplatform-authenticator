package com.infomaniak.multiplatform_authenticator.core.internal.logic

import com.infomaniak.multiplatform_authenticator.core.internal.db.AccountEntity
import com.infomaniak.multiplatform_authenticator.core.internal.db.AccountEntity.Status
import com.infomaniak.multiplatform_authenticator.core.internal.extensions.cancellable
import com.infomaniak.multiplatform_authenticator.core.internal.extensions.toAccountEntity

context(host: UserProfileUpdateHost)
internal suspend fun updateUserProfileLoop(
    account: AccountEntity,
    waitForRefreshTrigger: suspend () -> Unit
) {
    require(account.isLoggedIn)
    while (true) {
        runCatching {
            val profile = host.authenticatorRequests.getUserProfile(account.id)
            val profileSecurity = requireNotNull(profile.preferences.security)
            val newStatus = when (account.lastPasswordUpdate) {
                profileSecurity.dateLastChangedPassword -> account.status
                else -> Status.PasswordChanged
            }
            val updatedAccount = profile.toAccountEntity(status = newStatus)
            if (updatedAccount != account) { // Avoid re-trigger loops when we're up to date.
                host.authenticatorBridge.persistUserProfile(profile)
                host.dao.upsert(updatedAccount) // Last because this will trigger cancellation from latest operator upwards.
            }
        }.cancellable().onFailure {
            it.printStackTrace()
            it.reportIfNeeded(account.id, message = "profile update refresh failed")
        }
        waitForRefreshTrigger()
    }
}
