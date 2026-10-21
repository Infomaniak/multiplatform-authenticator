package com.infomaniak.multiplatform_authenticator.core.internal.logic

import com.infomaniak.multiplatform_authenticator.core.Account
import com.infomaniak.multiplatform_authenticator.core.Issue
import com.infomaniak.multiplatform_authenticator.core.internal.extensions.cancellable
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.FlowCollector

context(host: CrashReportHost)
internal suspend inline fun <R> FlowCollector<Account.Status.NotConnected>.withRetries(
    userId: Long,
    onGiveUp: () -> Unit = {},
    block: () -> R
): R {
    while (true) {
        runCatching {
            return block()
        }.cancellable().onFailure {
            it.printStackTrace()
            it.reportIfNeeded(userId, "account connection attempt failed")
            if (it is IllegalStateException || it is IllegalArgumentException) { // Local errors, no recourse.
                val issue = Issue.NonRetriable(it.message ?: it::class.simpleName ?: "$it")
                emit(Account.Status.NotConnected.LoginFailed(issue))
                awaitCancellation()
            }
            val issueCause = it.toIssueCause()
            val shouldRetryAsync = CompletableDeferred<Boolean>()
            val issue = Issue.Retriable(cause = issueCause, proceed = shouldRetryAsync::complete)
            emit(Account.Status.NotConnected.LoginFailed(issue))
            val shouldRetry = shouldRetryAsync.await()
            if (shouldRetry) continue else onGiveUp()
        }
    }
}
