package com.infomaniak.multiplatform_authenticator.core.internal.logic

import com.infomaniak.multiplatform_authenticator.core.Account
import com.infomaniak.multiplatform_authenticator.core.internal.db.AccountEntity
import kotlinx.coroutines.flow.FlowCollector

context(host: MigrationHost)
internal suspend fun FlowCollector<Account.Status.NotConnected>.restoreFromBackupAttempts(account: AccountEntity) {
    withRetries(userId = account.id) {
        emit(Account.Status.NotConnected.AttemptingToConnect)
        host.migrationManager.restore(account = account) { userId, token ->
            host.authenticatorBridge.attemptPersistingTokenForAccount(userId, token)
        }
    }
}
