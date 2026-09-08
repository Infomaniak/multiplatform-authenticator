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
