plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("io.github.takahirom.roborazzi")
}

android {
    namespace = "ru.capital.idle"
    compileSdk = 35

    defaultConfig {
        applicationId = "ru.capital.idle"
        minSdk = 26
        targetSdk = 35
        versionCode = 6
        versionName = "1.2"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
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
    }

    testOptions {
        unitTests {
            // Robolectric нужен доступ к ресурсам приложения, иначе Compose не отрендерится
            isIncludeAndroidResources = true
        }
    }
}

// Эталонные скриншоты лежат в исходниках и коммитятся; расхождения — в build/outputs/roborazzi
roborazzi {
    outputDir.set(file("src/test/screenshots"))
}

// Скриншот-тесты идут только через задачи Roborazzi (record/verify/compare).
// Обычный `./gradlew test` их пропускает: иначе прогон без флагов молча перезаписал бы эталоны,
// да и поднимать Robolectric ради чистых расчётных тестов незачем.
val screenshotRun = gradle.startParameter.taskNames.any { it.contains("Roborazzi", ignoreCase = true) }
tasks.withType<Test>().configureEach {
    if (!screenshotRun) exclude("ru/capital/idle/screenshot/**")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.02")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    testImplementation("junit:junit:4.13.2")

    // скриншот-тесты вёрстки: Compose рендерится на обычной JVM, без устройства и эмулятора
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test.ext:junit:1.2.1")
    testImplementation(composeBom)
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.32.2")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.32.2")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-junit-rule:1.32.2")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
