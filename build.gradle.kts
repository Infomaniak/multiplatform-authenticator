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

import co.touchlab.skie.configuration.DefaultArgumentInterop
import com.android.build.api.dsl.androidLibrary
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

buildscript {
    extra.apply {
        set("androidCompileSdk", 36)
        set("androidMinSdk", 27)
        set("javaVersion", JavaVersion.VERSION_17)
    }
}

plugins {
    alias(kmpAuthenticator.plugins.kotlin.multiplatform)
    alias(kmpAuthenticator.plugins.android.kmp.library)
    alias(kmpAuthenticator.plugins.kotlin.serialization)
    alias(kmpAuthenticator.plugins.skie)
    alias(kmpAuthenticator.plugins.androidx.room)
    alias(kmpAuthenticator.plugins.ksp)
    kotlin("plugin.parcelize") version kmpAuthenticator.versions.kotlin
    id("infomaniak.publishPlugin")
}

val androidCompileSdk: Int by rootProject.extra
val androidMinSdk: Int by rootProject.extra
val javaVersion: JavaVersion by rootProject.extra

kotlin {
    @Suppress("UnstableApiUsage")
    androidLibrary {
        namespace = "com.infomaniak.auth.multiplatform"
        compileSdk = androidCompileSdk
        minSdk = androidMinSdk

        withDeviceTestBuilder {
            sourceSetTreeName = "test"
        }
        withHostTest {}
        compilerOptions {
            jvmTarget.set(JvmTarget.fromTarget(javaVersion.toString()))
        }
    }

    val xcframeworkName = "CoreAuthenticator"
    val xcf = project.XCFramework(xcframeworkName)
    listOf(
        iosArm64(),
        iosSimulatorArm64(),
        macosArm64(),
    ).forEach {
        it.binaries.framework {
            baseName = xcframeworkName
            binaryOption("bundleId", "com.infomaniak.multiplatform-authenticator.${xcframeworkName}")
            xcf.add(this)
            linkerOpts.add("-lsqlite3")
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(kmpAuthenticator.androidx.room.runtime)
            implementation(kmpAuthenticator.androidx.sqlite.bundled)
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(kmpAuthenticator.kotlinx.coroutines.core)
                implementation(kmpAuthenticator.kotlinx.serialization.json)
                implementation(kmpAuthenticator.kotlinx.serialization.cbor)
                implementation(kmpAuthenticator.ktor.client.core)
                implementation(kmpAuthenticator.ktor.client.auth)
                implementation(kmpAuthenticator.ktor.client.content.negociation)
                implementation(kmpAuthenticator.ktor.client.json)
                implementation(kmpAuthenticator.ktor.client.encoding)
                implementation(kmpAuthenticator.okio)
                implementation(kmpAuthenticator.osmerion.kotlin.base32)
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(kmpAuthenticator.kotlinx.coroutines.test)
                implementation(kmpAuthenticator.ktor.client.mock)
            }
        }
        androidMain {
            dependencies {
                implementation(kmpAuthenticator.ktor.client.okhttp)
                implementation(kmpAuthenticator.splitties.appctx)
                implementation(kmpAuthenticator.splitties.bitflags)
            }
        }
        appleMain {
            dependencies {
                implementation(kmpAuthenticator.ktor.client.darwin)
            }
        }

        listOf("iosArm64", "iosSimulatorArm64", "macosArm64").forEach { target ->
            getByName("${target}Main") {
                kotlin.srcDir(layout.buildDirectory.dir("generated/ksp/$target/${target}Main/kotlin"))
            }
        }

        val androidDeviceTest by getting {
            dependencies {
                implementation(kmpAuthenticator.androidx.junit)
                implementation(kmpAuthenticator.androidx.espresso.core)
            }
        }
    }

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
        freeCompilerArgs.add("-Xreturn-value-checker=full")
    }
}

skie {
    features {
        group {
            DefaultArgumentInterop.Enabled(true)
            DefaultArgumentInterop.MaximumDefaultArgumentCount(7)
        }
    }
    build {
        produceDistributableFramework()
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    add("kspAndroid", kmpAuthenticator.androidx.room.compiler)
    add("kspIosSimulatorArm64", kmpAuthenticator.androidx.room.compiler)
    add("kspIosArm64", kmpAuthenticator.androidx.room.compiler)
    add("kspMacosArm64", kmpAuthenticator.androidx.room.compiler)
}

listOf("IosArm64", "IosSimulatorArm64", "MacosArm64").forEach { target ->
    tasks.named("compileKotlin$target") {
        dependsOn("kspKotlin$target")
    }
}
