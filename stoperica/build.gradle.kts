// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
}
buildscript {
    dependencies {
        classpath("com.google.firebase:firebase-crashlytics-gradle:3.0.3")
        classpath("com.google.android.gms:oss-licenses-plugin:0.10.6")
    }
}