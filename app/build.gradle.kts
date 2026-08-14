import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

// A chave do Google Maps NUNCA fica no código-fonte (que vai pro Git) — ela mora só no
// arquivo local.properties (que está no .gitignore, nunca é versionado). Cada pessoa que
// clonar o repositório precisa criar esse arquivo na raiz do projeto com a linha:
//   MAPS_API_KEY=sua_chave_aqui
// Sem isso, o valor fica vazio e o mapa mostra um aviso "for developers" do Google em vez
// de travar o build — assim ninguém esquece de configurar sem perceber o motivo.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) FileInputStream(file).use { load(it) }
}
val mapsApiKey: String = localProperties.getProperty("MAPS_API_KEY", "")

android {
    namespace = "com.rotacerta.entregador"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.rotacerta.entregador"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// Faz o Room salvar um .json do schema de cada versão do banco em app/schemas/.
// É esse histórico que garante que uma futura Migration bata exatamente com o
// schema real de cada versão, em vez de ser escrita "de memória" e arriscar
// quebrar a validação do Room (ou pior, corromper dados) no aparelho do usuário.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    // Core / Compose
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Room (persistência local - entregas e histórico)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // DataStore (config do app)
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Networking (ViaCEP + Nominatim)
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // OCR (foto do CEP) e leitura de QR code/código de barras (scanner de etiqueta,
    // busca pelo código de rastreio do pacote), com câmera embutida via CameraX
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Mapa (Google Maps nativo, substitui o WebView+Leaflet usado antes)
    implementation("com.google.android.gms:play-services-maps:19.0.0")
    implementation("com.google.maps.android:maps-compose:4.3.3")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")

    // Core library desugaring habilitado acima — mantido como rede de segurança
    // para bibliotecas que usem APIs mais novas do Java em Android antigo.
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs_nio:2.0.4")
}
