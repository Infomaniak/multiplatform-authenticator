package com.infomaniak.multiplatform_authenticator.core.internal.logic

import com.infomaniak.multiplatform_authenticator.core.internal.db.AccountsDao
import com.infomaniak.multiplatform_authenticator.core.internal.managers.AuthenticatorManager
import com.infomaniak.multiplatform_authenticator.core.internal.managers.MigrationManager
import com.infomaniak.multiplatform_authenticator.core.internal.requests.AuthenticatorRequests
import com.infomaniak.multiplatform_authenticator.core.logging.BlockLogger
import com.infomaniak.multiplatform_authenticator.core.network.interfaces.AuthenticatorBridge
import com.infomaniak.multiplatform_authenticator.core.network.interfaces.CrashReportInterface

internal interface CrashReportHost {
    val crashReport: CrashReportInterface
}

internal interface MigrationHost : CrashReportHost {
    val authenticatorBridge: AuthenticatorBridge
    val dao: AccountsDao
    val migrationManager: MigrationManager
}

internal interface UserProfileUpdateHost : CrashReportHost {
    val authenticatorBridge: AuthenticatorBridge
    val dao: AccountsDao
    val authenticatorRequests: AuthenticatorRequests
}

internal interface RegistrationHost : CrashReportHost {
    val authenticatorBridge: AuthenticatorBridge
    val dao: AccountsDao
    val authenticatorManager: AuthenticatorManager
    val clientId: String
}

internal interface AccountStatusHost : MigrationHost, RegistrationHost, UserProfileUpdateHost, CrashReportHost {
    val blockLogger: BlockLogger<String>
    suspend fun waitForMigrationStartSignal()
}
