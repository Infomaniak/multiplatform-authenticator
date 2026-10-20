package com.infomaniak.multiplatform_authenticator.core.internal.logic

import com.infomaniak.multiplatform_authenticator.core.AuthenticatorFacade
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

internal suspend fun AuthenticatorFacade.logStatusChanges(): Nothing = coroutineScope {
    launch {
        accounts.collect { accounts ->
            accounts.forEach { account ->
                println("Account ${account.id} (${account.initials}) has status: ${account.status}")
            }
        }
    }
    appStatus.collect { println("appStatus: $it") }
}
