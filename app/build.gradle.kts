plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.hanson.fplimit"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.hanson.fplimit"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
        debug {
            signingConfig = signingConfigs.getByName("debug")
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
        // YukiHookAPI 的 KSP 处理器需要读取 BuildConfig 里的包名来生成 xposed_init
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Xposed API 82（编译期 stub，运行时由 LSPosed 提供）。直接内置 jar，避免依赖已失效的仓库。
    compileOnly(files("libs/xposed-api-82.jar"))

    // YukiHookAPI 生成代码里用到了 androidx.annotation.Keep
    compileOnly("androidx.annotation:annotation:1.8.1")

    implementation("com.highcapable.yukihookapi:api:1.3.1")
    ksp("com.highcapable.yukihookapi:ksp-xposed:1.3.1")
}
