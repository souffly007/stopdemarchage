plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("com.google.dagger.hilt.android")
    id("kotlin-kapt")
    id("kotlin-parcelize")
}

android {
    namespace = "fr.bonobo.stopdemarchage"
    compileSdk = 36

    defaultConfig {
        applicationId = "fr.bonobo.stopdemarchage"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "2.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        externalNativeBuild {
            cmake {
                cppFlags += ""
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    kapt {
        correctErrorTypes = true
    }

    buildFeatures {
        viewBinding = true
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}

dependencies {
    // AndroidX & UI
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation(libs.material)
    implementation("com.google.android.material:material:1.13.0")
    implementation("com.google.code.gson:gson:2.13.2")

    // Fragments
    // La dépendance 'fragment-ktx' inclut déjà 'fragment'
    implementation("androidx.fragment:fragment-ktx:1.8.9")
    implementation("androidx.appcompat:appcompat:1.7.1")

    // ViewPager2
    // Une seule dépendance suffit
    implementation("androidx.viewpager2:viewpager2:1.1.0")

    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("androidx.loader:loader:1.1.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.2.0")

    // Hilt & Architecture
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.10.0")
    implementation("com.google.dagger:hilt-android:2.59")
    kapt("com.google.dagger:hilt-android-compiler:2.59")
    kapt("androidx.hilt:hilt-compiler:1.3.0")
    kapt ("androidx.annotation:annotation:1.9.1")

    // Room
    implementation("androidx.room:room-runtime:2.8.4")
    kapt("androidx.room:room-compiler:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")

    // Third-Party Libraries
    implementation("com.googlecode.libphonenumber:libphonenumber:9.0.22")
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")
    implementation("com.airbnb.android:lottie:6.7.1")
    implementation("com.mikhaellopez:circularprogressbar:3.1.0")
    implementation("com.github.skydoves:progressview:1.1.3")
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}