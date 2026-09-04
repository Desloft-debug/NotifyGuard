plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.guard.notifyguard"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.guard.notifyguard"
        minSdk = 29
        targetSdk = 34
        versionCode = 15
        versionName = "3.1"

        // Репозиторий, откуда берутся обновления, онлайн-словарь и куда уходят issue.
        // Должен совпадать с `git remote get-url origin`: если сменится имя аккаунта,
        // а здесь останется старое, приложение продолжит ходить на старый репозиторий
        // и не увидит ни новых релизов, ни правок словаря.
        buildConfigField("String", "GITHUB_OWNER", "\"Desloft-debug\"")
        buildConfigField("String", "GITHUB_REPO", "\"NotifyGuard\"")

        // В APK попадают только нужные локали
        resourceConfigurations += setOf("en", "ru")
    }

    // IzzyOnDroid и F-Droid не принимают APK с зашифрованным блоком
    // метаданных зависимостей: прочитать его может только Google.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    signingConfigs {
        create("release") {
            val path = System.getenv("KEYSTORE_PATH")
            if (!path.isNullOrBlank()) {
                storeFile = file(path)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // Убирает неиспользуемый код и ресурсы: меньше APK,
            // меньше классов на загрузку при старте
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (!System.getenv("KEYSTORE_PATH").isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            isMinifyEnabled = false
        }
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "DebugProbesKt.bin",
                "kotlin-tooling-metadata.json"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            // Тесты в app/src/test не трогают Android: словари, границы слов,
            // сравнение версий и разбор номера — чистый Kotlin, Robolectric не нужен.
            // Флаг на случай, если тест всё же дотянется до заглушки android.jar:
            // она вернёт значение по умолчанию вместо RuntimeException("Stub!").
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
}
