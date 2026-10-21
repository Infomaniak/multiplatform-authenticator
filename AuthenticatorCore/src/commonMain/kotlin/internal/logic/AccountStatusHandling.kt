@file:OptIn(ExperimentalCoroutinesApi::class)

package com.infomaniak.multiplatform_authenticator.core.internal.logic

import com.infomaniak.multiplatform_authenticator.core.Account
import com.infomaniak.multiplatform_authenticator.core.internal.db.AccountEntity
import com.infomaniak.multiplatform_authenticator.core.internal.db.AccountEntity.Status
import com.infomaniak.multiplatform_authenticator.core.internal.managers.AuthenticatorManager
import com.infomaniak.multiplatform_authenticator.core.internal.utils.waitForComplete
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.transformLatest

context(host: AccountStatusHost)
internal fun accountStatusForUser(
    userId: Long,
    waitForProfileRefreshTrigger: suspend () -> Unit,
): Flow<Account.Status> = host.dao.getAccountAsFlow(userId).transformLatest { entity ->
    val status = entity?.status
    host.blockLogger.withLog("accountStatusForUser($status)") {
        when (status) {
            Status.ToBeMigrated -> migrationAttempts(entity)
            Status.PasskeyRegistrationPending, Status.FirstPasskeyAuthenticationPending -> {
                registrationAttempts(entity)
            }
            Status.RestoringFromBackup, Status.DeletingOldKeyAfterRestoration -> {
                restoreFromBackupAttempts(account = entity)
            }
            Status.LoggedIn, Status.PasswordChanged -> {
                handledLoggedInState(account = entity, waitForProfileRefreshTrigger = waitForProfileRefreshTrigger)
            }
            Status.Disconnected -> {
                handleDisconnectedState(host.authenticatorManager, entity)
            }
            null -> Unit // Should not happen in practice.
        }
    }
}

/**
 * Tries to migrate the given account from kAuth to Infomaniak Authenticator, with retries,
 * and emits the relevant [Account.Status.NotConnected] as needed.
 *
 * 1. Tries to perform a login (3 different ways)
 * 2. Starts a migration session against the backend
 * 3. Registers a passkey
 * 4. Authenticate with it, getting a new access token
 */
context(host: AccountStatusHost)
private suspend fun FlowCollector<Account.Status.NotConnected>.migrationAttempts(accountToMigrate: AccountEntity) {
    require(accountToMigrate.status == Status.ToBeMigrated)
    emit(Account.Status.NotConnected.AttemptingToConnect.ToBeMigrated)

    host.waitForMigrationStartSignal()
    host.blockLogger.withLog("migrationAttempts.tryCrossAppLogin") {
        tryCrossAppLogin(accountToMigrate) { return }
    }
    host.blockLogger.withLog("migrationAttempts.tryToMigrateViaOngoingLogin") {
        tryToMigrateViaOngoingLogin(accountToMigrate) { return }
    }
    host.blockLogger.withLog("migrationAttempts.tryMigratingWithReLogin") {
        tryMigratingWithReLogin(accountToMigrate)
    }
}

context(host: AccountStatusHost)
private suspend fun FlowCollector<Account.Status.LoggedIn>.handledLoggedInState(
    account: AccountEntity,
    waitForProfileRefreshTrigger: suspend () -> Unit,
) {
    val needsToAcknowledgePasswordUpdate: Boolean = account.status == Status.PasswordChanged
    if (needsToAcknowledgePasswordUpdate) {
        val previousStatus = waitForComplete { passwordChangedAcknowledgedAsync ->
            Account.Status.LoggedIn(
                securityScore = account.securityScore,
                passwordChangedAck = passwordChangedAcknowledgedAsync::complete
            ).also { emit(it) }
        }
        emit(previousStatus.copy(passwordChangedAck = null))
        host.dao.upsert(account.copy(status = Status.LoggedIn))
    } else {
        emit(Account.Status.LoggedIn(securityScore = account.securityScore))
    }
    updateUserProfileLoop(account) { waitForProfileRefreshTrigger() }
}

private suspend fun FlowCollector<Account.Status.NotConnected.Disconnected>.handleDisconnectedState(
    authenticatorManager: AuthenticatorManager,
    account: AccountEntity
) {
    waitForComplete { disconnectionRequest ->
        val status = Account.Status.NotConnected.Disconnected(disconnectionRequest::complete)
        emit(status)
    }
    authenticatorManager.removeAccount(token = null, userId = account.id)
}
