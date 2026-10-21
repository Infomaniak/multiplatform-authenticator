package com.infomaniak.multiplatform_authenticator.core.internal.logic

import com.infomaniak.multiplatform_authenticator.core.Issue.Retriable.Cause
import com.infomaniak.multiplatform_authenticator.core.network.exceptions.ApiException
import com.infomaniak.multiplatform_authenticator.core.network.exceptions.NetworkException
import kotlinx.io.IOException

internal fun Throwable.toIssueCause(): Cause = when (this) {
    is NetworkException, is IOException -> Cause.NetworkIssue
    is ApiException if (statusCode == 503) -> Cause.ServerUnavailable
    is ApiException.ApiErrorException -> {
        Cause.Other(12_000 + statusCode, "http $statusCode $errorCode $errorMessage")
    }
    is ApiException.UnexpectedApiErrorFormatException -> {
        Cause.Other(22_000 + statusCode, "http $statusCode $bodyResponse")
    }
    else -> {
        Cause.Other(11_000, message ?: this::class.simpleName ?: "$this")
    }
}

context(host: CrashReportHost)
internal fun Throwable.reportIfNeeded(userId: Long, message: String) {
    when (this) {
        is NetworkException, is IOException -> Unit
        is ApiException if (statusCode == 503) -> Unit
        else -> host.crashReport.capture(userId, message, this)
    }
}
