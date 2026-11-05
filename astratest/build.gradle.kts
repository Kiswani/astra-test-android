import com.vanniktech.maven.publish.SonatypeHost

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("com.vanniktech.maven.publish")
}

// Published as dev.astratest:astratest. Version comes from -PVERSION_NAME (sdks/publish.sh sets it).
mavenPublishing {
    coordinates("dev.astratest", "astratest", (findProperty("VERSION_NAME") as String?) ?: "0.1.0")
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL, automaticRelease = true)
    // Signing is required by Maven Central, so only on when a key is configured (local publishes stay unsigned).
    if (findProperty("signingInMemoryKey") != null) signAllPublications()
    pom {
        name.set("Astra Test for Android")
        description.set("In-app bug reporting for Astra Test: testers report from inside your app with a screenshot, logs, requests and device details attached.")
        url.set("https://astratest.dev")
        licenses { license { name.set("MIT"); url.set("https://opensource.org/licenses/MIT") } }
        developers { developer { id.set("kiswani"); name.set("Fahad Kiswani"); url.set("https://astratest.dev") } }
        scm { url.set("https://github.com/Kiswani/astra-test-android"); connection.set("scm:git:https://github.com/Kiswani/astra-test-android.git") }
    }
}

android {
    namespace = "dev.astratest"
    compileSdk = 35
    defaultConfig {
        minSdk = 23
        consumerProguardFiles("consumer-rules.pro")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    testOptions { unitTests.isReturnDefaultValues = true; targetSdk = 35 }
}

dependencies {
    // Only needed if the app uses OkHttp; the interceptor is skipped otherwise.
    compileOnly("com.squareup.okhttp3:okhttp:4.12.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
