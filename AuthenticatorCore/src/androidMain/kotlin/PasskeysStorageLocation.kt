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

import splitties.init.appCtx
import java.io.File

object PasskeysStorageLocation {

    val dir: File by lazy {
        appCtx.filesDir.resolve("passkeys").also { passkeysDir ->
            passkeysDir.mkdir()
        }
    }

    fun keyFile(userId: Long, keyId: String, isPublic: Boolean): File {
        val visibility = if (isPublic) "public" else "private"
        return dir.resolve("$userId-$keyId-$visibility.key")
    }
}
