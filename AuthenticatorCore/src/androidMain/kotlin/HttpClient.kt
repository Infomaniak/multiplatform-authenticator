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
