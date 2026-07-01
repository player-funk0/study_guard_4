// Top-level build file
plugins {
    id("com.android.application") version "8.2.2" apply false
    id("com.android.library")     version "8.2.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
    id("org.jetbrains.kotlin.kapt")    version "1.9.22" apply false
    id("com.google.devtools.ksp")      version "1.9.22-1.0.17" apply false
}

allprojects {
    extra["compileSdk"]        = 34
    extra["minSdk"]            = 26
    extra["targetSdk"]         = 34
    extra["composeBom"]        = "2024.02.00"
    extra["roomVersion"]       = "2.6.1"
    extra["lifecycleVersion"]  = "2.7.0"
    extra["navigationVersion"] = "2.7.7"
    extra["mediaPipeVersion"]  = "0.10.14"
    extra["dataStoreVersion"]  = "1.0.0"
}

tasks.register<Delete>("clean") {
    delete(rootProject.buildDir)
}
