package com.infomaniak.multiplatform_authenticator.core.internal.logic

import com.infomaniak.multiplatform_authenticator.core.Account
import com.infomaniak.multiplatform_authenticator.core.Account.Status.NotConnected.ReLogin
import com.infomaniak.multiplatform_authenticator.core.CredentialsForMigration
import com.infomaniak.multiplatform_authenticator.core.internal.MigrationAuthentication
import com.infomaniak.multiplatform_authenticator.core.internal.db.AccountEntity
import com.infomaniak.multiplatform_authenticator.core.internal.db.AccountEntity.Status
import com.infomaniak.multiplatform_authenticator.core.internal.extensions.cancellable
import com.infomaniak.multiplatform_authenticator.core.internal.extensions.toAccount
import com.infomaniak.multiplatform_authenticator.core.internal.utils.launchRacer
import com.infomaniak.multiplatform_authenticator.core.internal.utils.race
import com.infomaniak.multiplatform_authenticator.core.internal.utils.withTimeoutOrNull
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.FlowCollector
import kotlin.time.Duration.Companion.seconds

context(host: MigrationHost)
internal suspend inline fun FlowCollector<Account.Status.NotConnected>.tryCrossAppLogin(
    notConnectedAccount: AccountEntity,
    onLoginSuccess: () -> Nothing
) {
    val userId = notConnectedAccount.id
    withRetries(userId, onGiveUp = { return }) {
        emit(Account.Status.NotConnected.AttemptingToConnect)
        val temporaryToken = withTimeoutOrNull(
            waitForTimeout = {
                delay(8.seconds)
                "getTokenFromCrossAppLogin timed out"
            },
            onTimeout = { message -> host.crashReport.capture(userId, message) }
        ) {
            host.authenticatorBridge.getTokenFromCrossAppLogin(userId)
        } ?: return
        val authentication = MigrationAuthentication.CrossAppLogin(temporaryToken)
        if (attemptMigration(notConnectedAccount, authentication)) onLoginSuccess() else return
    }
}

context(host: MigrationHost)
internal suspend inline fun FlowCollector<Account.Status.NotConnected>.tryToMigrateViaOngoingLogin(
    notConnectedAccount: AccountEntity,
    onLoginSuccess: () -> Nothing
) {
    withRetries(notConnectedAccount.id, onGiveUp = { return }) {
        emit(Account.Status.NotConnected.AttemptingToConnect)
        val authentication = MigrationAuthentication.OngoingLogin
        if (attemptMigration(notConnectedAccount, authentication)) onLoginSuccess() else return
    }
}

context(host: MigrationHost)
internal suspend fun FlowCollector<ReLogin>.tryMigratingWithReLogin(accountToMigrate: AccountEntity) {
    var status = ReLogin(
        legacyAccount = accountToMigrate.toAccount(Account.Status.NotConnected.AttemptingToConnect),
        hadIncorrectPassword = false,
        lastIssue = null,
        sendCredentials = null
    )
    val issueDismissals = Channel<Unit>(capacity = Channel.CONFLATED)
    loop@ while (true) {
        status = runCatching {
            val credentialsAsync = CompletableDeferred<CredentialsForMigration>()
            status = status.copy(sendCredentials = credentialsAsync::complete)
            emit(status)
            val credentialsForMigration = awaitCredentialsWhileAllowingIssueDismissal(
                credentialsAsync = credentialsAsync,
                issueDismissals = issueDismissals,
                lastStatus = status,
                onStatusUpdate = { newStatus ->
                    status = newStatus
                    emit(status)
                }
            )
            status = status.copy(hadIncorrectPassword = false, lastIssue = null, sendCredentials = null)
            emit(status)
            val authentication = MigrationAuthentication.NoOngoingLogin(credentialsForMigration.password)
            val succeeded = attemptMigration(accountToMigrate, authentication)
            when {
                succeeded -> return
                else -> status.copy(hadIncorrectPassword = true)
            }
        }.cancellable().getOrElse {
            it.printStackTrace()
            // TODO Delete the capture method here after investigation
            host.crashReport.capture(accountToMigrate.id, "re-login migration attempt failed", it)
            // TODO Uncomment this when investigation is done
            // it.reportIfNeeded(accountToMigrate.id, message = "re-login migration attempt failed")
            val issue = ReLogin.DismissableIssue(
                dismiss = { issueDismissals.trySend(Unit) },
                cause = it.toIssueCause()
            )
            status.copy(lastIssue = issue)
        }
    }
}

private suspend inline fun awaitCredentialsWhileAllowingIssueDismissal(
    credentialsAsync: CompletableDeferred<CredentialsForMigration>,
    issueDismissals: ReceiveChannel<Unit>,
    lastStatus: ReLogin,
    onStatusUpdate: (newStatus: ReLogin) -> Unit,
): CredentialsForMigration = race {
    launchRacer { credentialsAsync.await() }
    if (lastStatus.lastIssue != null) launchRacer {
        issueDismissals.receive()
        null
    }
} ?: run {
    onStatusUpdate(lastStatus.copy(lastIssue = null))
    credentialsAsync.await()
}

context(host: MigrationHost)
private suspend fun attemptMigration(
    notConnectedAccount: AccountEntity,
    authentication: MigrationAuthentication,
): Boolean {
    val userId = notConnectedAccount.id
    val succeeded = host.migrationManager.tryMigrating(
        userId = userId,
        authentication = authentication,
    )

    if (!succeeded) return false

    host.dao.upsert(notConnectedAccount.copy(status = Status.FirstPasskeyAuthenticationPending))

    return true
}
