/*
 * Infomaniak Authenticator - Multiplatform
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

import org.gradle.kotlin.dsl.kotlin

plugins {
    alias(kmpAuthenticator.plugins.kotlin.multiplatform) apply false
    alias(kmpAuthenticator.plugins.android.kmp.library) apply false
    alias(kmpAuthenticator.plugins.kotlin.serialization) apply false
    alias(kmpAuthenticator.plugins.skie) apply false
    alias(kmpAuthenticator.plugins.androidx.room) apply false
    alias(kmpAuthenticator.plugins.ksp) apply false
    kotlin("plugin.parcelize") version kmpAuthenticator.versions.kotlin apply false
}
