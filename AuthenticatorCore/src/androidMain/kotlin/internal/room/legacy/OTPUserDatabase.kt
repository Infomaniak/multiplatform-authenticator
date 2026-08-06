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
package com.infomaniak.multiplatform_authenticator.core.internal.room.legacy

import android.content.Context
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase
import com.infomaniak.multiplatform_authenticator.core.internal.models.LegacyUser
import splitties.init.appCtx

@Database(entities = [LegacyUser::class], version = 1)
internal abstract class OTPUserDatabase : RoomDatabase() {

    abstract fun otpUserDao(): OTPUserDao

    companion object {
        val instance = buildDatabase(appCtx)

        private fun buildDatabase(context: Context) =
            Room.databaseBuilder(
                context.applicationContext,
                OTPUserDatabase::class.java, "Infomaniak.db"
            ).build()
    }
}
