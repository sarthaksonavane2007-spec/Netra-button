# Netra Cane Test

A minimal, standard Android application project configured with Kotlin and Gradle Kotlin DSL (`build.gradle.kts`), targeting Android 13 (API 33).

## Project Overview

- **Application Name**: Netra Cane Test
- **Package / Namespace**: `com.netra.smartcane`
- **Compile SDK**: `33`
- **Target SDK**: `33`
- **Min SDK**: `24`
- **Language**: Kotlin 1.9.22
- **Build System**: Gradle Kotlin DSL (`*.gradle.kts`) with AGP 8.2.2 & Gradle 8.2

## Key Dependencies Included

- **AndroidX AppCompat**: `androidx.appcompat:appcompat:1.6.1`
- **AndroidX Core KTX**: `androidx.core:core-ktx:1.10.1`
- **Google Material Components**: `com.google.android.material:material:1.9.0`
- **ConstraintLayout**: `androidx.constraintlayout:constraintlayout:2.1.4`
- **Lifecycle Runtime KTX**: `androidx.lifecycle:lifecycle-runtime-ktx:2.6.2`
- **Kotlin Coroutines Core**: `org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3`
- **Kotlin Coroutines Android**: `org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3`

## Directory Structure

```text
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/
│       ├── androidTest/java/com/netra/smartcane/ExampleInstrumentedTest.kt
│       ├── test/java/com/netra/smartcane/ExampleUnitTest.kt
│       └── main/
│           ├── AndroidManifest.xml
│           ├── java/com/netra/smartcane/MainActivity.kt
│           └── res/
│               ├── drawable/
│               ├── layout/activity_main.xml
│               ├── mipmap-anydpi-v26/
│               ├── values/ (strings.xml, colors.xml, themes.xml)
│               ├── values-night/ (themes.xml)
│               └── xml/ (backup_rules.xml, data_extraction_rules.xml)
├── gradle/wrapper/
│   └── gradle-wrapper.properties
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── gradlew
├── gradlew.bat
└── .gitignore
```

## Opening and Running the Project

1. Launch **Android Studio**.
2. Select **Open** and choose the root directory (`netra cane reciever`).
3. Allow Gradle to sync dependencies.
4. Select an emulator or connected device running Android (API 24 to 33+) and click **Run**.
