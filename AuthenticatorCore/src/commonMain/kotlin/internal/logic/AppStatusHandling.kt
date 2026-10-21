@file:OptIn(ExperimentalCoroutinesApi::class)

package com.infomaniak.multiplatform_authenticator.core.internal.logic

import com.infomaniak.multiplatform_authenticator.core.Account
import com.infomaniak.multiplatform_authenticator.core.Account.Status.NotConnected.ReLogin
import com.infomaniak.multiplatform_authenticator.core.AppStatus
import com.infomaniak.multiplatform_authenticator.core.AuthenticatorFacade
import com.infomaniak.multiplatform_authenticator.core.internal.utils.waitForComplete
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.transformLatest

internal fun appStatusFlow(
    accountsFlow: Flow<List<Account>>,
    proceedMigrationSignal: CompletableJob,
): Flow<AppStatus> = flow {
    var needsToShowEverythingReady = false

    val appStatusFlow: Flow<AppStatus> = accountsFlow.transformLatest { accounts ->
        val atLeastOneConnectedAccount = accounts.any { it.status is Account.Status.LoggedIn }
        val noConnectedAccount = !atLeastOneConnectedAccount

        if (noConnectedAccount) {
            needsToShowEverythingReady = true
            if (accounts.isEmpty()) {
                emit(AppStatus.LoginRequired.NotMigrating)
                /**
                 * Waiting for [AuthenticatorFacade.addAccounts] to be called,
                 * which will cancel this, as `accountEntities` emits.
                 */
                awaitCancellation()
            } else {
                val needsMigration = accounts.any { it.status == Account.Status.NotConnected.AttemptingToConnect.ToBeMigrated }
                if (needsMigration) {
                    emit(AppStatus.LoginRequired.MigratingFromLegacyKAuth(proceed = proceedMigrationSignal::complete))
                    proceedMigrationSignal.join()
                }
                emit(AppStatus.LoggingIn)
                handleLoggingInStatus(accounts)
            }
        } else if (needsToShowEverythingReady) {
            waitForComplete { proceedAsync ->
                emit(AppStatus.EverythingReady(proceed = proceedAsync::complete))
            }
        }
        while (true) {
            needsToShowEverythingReady = false
            waitForComplete { addAnAccountAsync ->
                emit(AppStatus.SetupComplete(addAnAccount = addAnAccountAsync::complete))
            }
            needsToShowEverythingReady = true
            waitForComplete { backAsync ->
                emit(AppStatus.AddingAnAccount(cancel = backAsync::complete))
            }
        }
    }

    emitAll(appStatusFlow)
}.distinctUntilChanged()

private suspend fun FlowCollector<AppStatus.LoginRequired.MustReLogin>.handleLoggingInStatus(accounts: List<Account>) {
    val stillTryingToConnect = accounts.any { account ->
        account.status is Account.Status.NotConnected.AttemptingToConnect
    }
    if (stillTryingToConnect) {
        awaitCancellation() // If at least one account is still loading, wait for the next update (i.e. cancellation)
    }

    val accountToRelogin: Account = when (accounts.size) {
        1 if accounts.single().status is ReLogin -> accounts.single()
        else -> {
            /** Continue towards [AppStatus.SetupComplete] to let the user handle failures, or re-login accounts separately */
            return
        }
    }
    /** Continue towards [AppStatus.SetupComplete] if the user asks to skip. */
    waitForComplete { skipAsync ->
        emit(AppStatus.LoginRequired.MustReLogin(accountToRelogin.id, skipAsync::complete))
    }
}
