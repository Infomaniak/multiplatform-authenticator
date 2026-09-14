package com.infomaniak.multiplatform_authenticator.core

import com.infomaniak.multiplatform_authenticator.core.internal.AuthenticatorFacadeImpl
import com.infomaniak.multiplatform_authenticator.core.internal.utils.asFunction
import io.ktor.client.HttpClient
import kotlinx.coroutines.Deferred

fun AuthenticatorFacade.httpClients(): suspend (
    userId: Long,
    block: suspend (Deferred<HttpClient>) -> Nothing
) -> Nothing = when (this) {
    is AuthenticatorFacadeImpl -> authenticatorRequests.perUserHttpClient.asFunction()
    else -> error("Unexpected AuthenticatorFacade subclass: ${this::class}")
}
