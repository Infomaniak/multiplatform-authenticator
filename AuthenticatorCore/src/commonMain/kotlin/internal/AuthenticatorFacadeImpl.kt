/*
 * Infomaniak Authenticator - Android
 * Copyright (C) 2026 Infomaniak Network SA
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
@file:OptIn(ExperimentalCoroutinesApi::class)

package com.infomaniak.multiplatform_authenticator.core.internal

import com.infomaniak.multiplatform_authenticator.core.Account
import com.infomaniak.multiplatform_authenticator.core.AppStatus
import com.infomaniak.multiplatform_authenticator.core.AuthenticatorFacade
import com.infomaniak.multiplatform_authenticator.core.internal.db.AccountEntity.Status
import com.infomaniak.multiplatform_authenticator.core.internal.db.AccountsDatabase
import com.infomaniak.multiplatform_authenticator.core.internal.extensions.firstOrElse
import com.infomaniak.multiplatform_authenticator.core.internal.extensions.toAccount
import com.infomaniak.multiplatform_authenticator.core.internal.extensions.toAccountEntity
import com.infomaniak.multiplatform_authenticator.core.internal.logic.AccountStatusHost
import com.infomaniak.multiplatform_authenticator.core.internal.logic.CrashReportHost
import com.infomaniak.multiplatform_authenticator.core.internal.logic.MigrationHost
import com.infomaniak.multiplatform_authenticator.core.internal.logic.RegistrationHost
import com.infomaniak.multiplatform_authenticator.core.internal.logic.UserProfileUpdateHost
import com.infomaniak.multiplatform_authenticator.core.internal.logic.accountStatusForUser
import com.infomaniak.multiplatform_authenticator.core.internal.logic.appStatusFlow
import com.infomaniak.multiplatform_authenticator.core.internal.logic.logStatusChanges
import com.infomaniak.multiplatform_authenticator.core.internal.managers.AuthenticatorManager
import com.infomaniak.multiplatform_authenticator.core.internal.managers.MigrationManager
import com.infomaniak.multiplatform_authenticator.core.internal.requests.AuthenticatorRequests
import com.infomaniak.multiplatform_authenticator.core.internal.utils.buildFlowWithElements
import com.infomaniak.multiplatform_authenticator.core.internal.utils.dynamicLazyMapOfSharedFlow
import com.infomaniak.multiplatform_authenticator.core.internal.utils.raceOf
import com.infomaniak.multiplatform_authenticator.core.logging.BlockLogger
import com.infomaniak.multiplatform_authenticator.core.logging.breadcrumbsLogger
import com.infomaniak.multiplatform_authenticator.core.models.migration.user.SharedUserProfile
import com.infomaniak.multiplatform_authenticator.core.network.interfaces.AuthenticatorBridge
import com.infomaniak.multiplatform_authenticator.core.network.interfaces.CrashReportInterface
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

internal class AuthenticatorFacadeImpl(
    accountsDatabase: AccountsDatabase,
    override val clientId: String,
    override val authenticatorRequests: AuthenticatorRequests,
    override val authenticatorManager: AuthenticatorManager,
    override val migrationManager: MigrationManager,
    override val authenticatorBridge: AuthenticatorBridge,
    override val crashReport: CrashReportInterface,
    shouldLogStatusChanges: Boolean,
    coroutineScope: CoroutineScope,
) : AuthenticatorFacade(), CrashReportHost, MigrationHost, UserProfileUpdateHost, RegistrationHost, AccountStatusHost {

    override val blockLogger = BlockLogger.breadcrumbsLogger(crashReport, "lib.kmp")

    override val dao = accountsDatabase.getDao()

    private val accountEntities = flow {
        migrationManager.setBackedUpAccountsStatus()
        migrationManager.addLegacyAccountsToDB()
        emitAll(dao.getAccountsAsFlow())
    }.shareIn(coroutineScope, SharingStarted.Eagerly, replay = 1)

    private val atLeastOneConnectedAccount: Flow<Boolean> = accountEntities.map { entities ->
        entities.any { entity -> entity.isLoggedIn }
    }.distinctUntilChanged().shareIn(coroutineScope, SharingStarted.WhileSubscribed(), replay = 1)

    private val userIdsToStatusFlows = coroutineScope.dynamicLazyMapOfSharedFlow(
        cacheManager = { _, _ ->
            delay(5.seconds) // Should be more than enough to keep the state between re-uses.
        }
    ) { userId: Long ->
        accountStatusForUser(
            userId = userId,
            waitForProfileRefreshTrigger = { profileRefreshesTrigger.first() },
        )
    }

    private val proceedMigration: CompletableJob = Job()

    private val profileRefreshesTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** [Account.status] values come from the [accountStatusForUser] function. */
    override val accounts: Flow<List<Account>> = accountEntities.flatMapLatest { entities ->
        if (entities.isEmpty()) return@flatMapLatest flowOf(emptyList())

        val userIds = entities.mapTo(mutableSetOf()) { it.id }

        userIdsToStatusFlows.buildFlowWithElements(userIds) { userIdsToStatusFlow ->
            val flowsOfStatus = entities.map { userIdsToStatusFlow.getValue(it.id) }
            combine(flowsOfStatus) { statuses ->
                entities.mapIndexed { index, entity -> entity.toAccount(statuses[index]) }
            }
        }
    }.flowOn(Dispatchers.Default).conflate().distinctUntilChanged().shareIn(coroutineScope, SharingStarted.Eagerly, replay = 1)

    override val appStatus: SharedFlow<AppStatus> = appStatusFlow(
        accounts = accounts,
        proceedMigration = proceedMigration,
    ).shareIn(coroutineScope, SharingStarted.Eagerly, replay = 1)

    init {
        if (shouldLogStatusChanges) coroutineScope.launch { logStatusChanges() }
    }

    override suspend fun addAccounts(connectedAccounts: List<SharedUserProfile>) {
        connectedAccounts.forEach { authenticatorBridge.persistUserProfile(it) }
        val entities = connectedAccounts.map {
            it.toAccountEntity(Status.PasskeyRegistrationPending)
        }
        dao.upsert(entities)
    }

    override suspend fun removeAccount(token: String?, id: Long) {
        authenticatorManager.removeAccount(token, id)
    }

    override suspend fun refreshTokenFor(userId: Long) {
        val token = authenticatorManager.getToken(clientId, userId).firstOrElse {
            error("Could not get the key for user $userId from the storage: $it")
        }
        authenticatorBridge.attemptPersistingTokenForAccount(userId, token)
    }

    override fun refreshUserProfiles() {
        profileRefreshesTrigger.tryEmit(Unit)
    }

    override suspend fun waitForMigrationStartSignal(): Unit = raceOf(
        { proceedMigration.join() },
        { atLeastOneConnectedAccount.first { it } },
    )
}
