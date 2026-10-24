plugins { id("com.android.application"); id("org.jetbrains.kotlin.android"); id("org.jetbrains.kotlin.plugin.compose"); id("org.jetbrains.kotlin.kapt") }
android {
 namespace = "com.panosphere.player"
 compileSdk = 35
 defaultConfig { applicationId = "com.panosphere.player"; minSdk = 26; targetSdk = 35; versionCode = 3; versionName = "1.2"; testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }
 buildFeatures { compose = true }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
 testOptions { unitTests.isReturnDefaultValues = true }
}
dependencies {
 implementation(platform("androidx.compose:compose-bom:2024.12.01"))
 implementation("androidx.activity:activity-compose:1.10.0")
 implementation("androidx.compose.material3:material3")
 implementation("androidx.compose.ui:ui")
 implementation("androidx.media3:media3-exoplayer:1.9.3")
 implementation("androidx.media3:media3-ui:1.9.3")
 implementation("androidx.room:room-runtime:2.6.1")
 implementation("androidx.room:room-ktx:2.6.1")
 kapt("androidx.room:room-compiler:2.6.1")
 implementation("androidx.datastore:datastore-preferences:1.1.1")
 implementation("androidx.exifinterface:exifinterface:1.3.6")
 implementation("androidx.documentfile:documentfile:1.0.1")
 testImplementation("junit:junit:4.13.2")
 androidTestImplementation("androidx.test.ext:junit:1.2.1")
 androidTestImplementation("androidx.test:runner:1.6.2")
}
kapt { arguments { arg("room.schemaLocation", "$projectDir/schemas") } }

