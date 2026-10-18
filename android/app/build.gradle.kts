import java.net.URI
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// La firma propia llega por GitHub Secrets (FIRMA_*), como en la pizarra y la
// app de la Hilux. Si no están, se firma con la clave de depuración y el
// workflow lo avisa. El keystore no entra nunca a este repositorio, que es
// público.
val firma = Properties().apply {
    val f = rootProject.file("key.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.maurogasta.gestos"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.maurogasta.gestos"
        minSdk = 26
        targetSdk = 35
        // El número de corrida de GitHub: sube en cada tanda y nunca retrocede.
        versionCode = (project.findProperty("codigoVersion") as String?)?.toIntOrNull() ?: 1
        versionName = "gestos-apk-3"
        // Sólo arm64: los tres Redmi del ecosistema lo son, y las librerías de
        // MediaPipe para los cuatro procesadores triplicaban el APK (56 MB).
        ndk { abiFilters += "arm64-v8a" }
    }

    signingConfigs {
        if (firma.getProperty("storeFile") != null) {
            create("propia") {
                storeFile = rootProject.file("app/" + firma.getProperty("storeFile"))
                storePassword = firma.getProperty("storePassword")
                keyAlias = firma.getProperty("keyAlias")
                keyPassword = firma.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("propia") ?: signingConfigs.getByName("debug")
        }
    }

    // El modelo va sin comprimir: MediaPipe lo abre mapeado en memoria.
    androidResources { noCompress += "task" }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // Las tres que no se pueden evitar: la cámara (CameraX), el reconocimiento
    // de manos (MediaPipe, el mismo modelo que la página) y la Activity con
    // ciclo de vida que CameraX necesita. El sonido es propio: AudioTrack.
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.camera:camera-camera2:1.4.2")
    implementation("androidx.camera:camera-lifecycle:1.4.2")
    implementation("androidx.camera:camera-view:1.4.2")
    implementation("com.google.mediapipe:tasks-vision:0.10.29")
    testImplementation("junit:junit:4.13.2")
}

// El modelo de manos: el MISMO archivo que baja la página desde el CDN. Se baja
// una vez y queda en assets; no se versiona.
val bajarModelo by tasks.registering {
    val destino = file("src/main/assets/hand_landmarker.task")
    outputs.file(destino)
    doLast {
        if (!destino.exists()) {
            destino.parentFile.mkdirs()
            URI("https://storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/1/hand_landmarker.task")
                .toURL().openStream().use { entrada -> destino.outputStream().use { entrada.copyTo(it) } }
        }
    }
}
tasks.named("preBuild") { dependsOn(bajarModelo) }
