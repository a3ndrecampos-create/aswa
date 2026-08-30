plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

configurations.all {
    // Evita o conflito clássico do CameraX: várias libs (Billing, Room, etc.) trazem
    // o stub "com.google.guava:listenablefuture" enquanto o CameraX espera a classe
    // real do Guava. Forçamos uma única implementação de ListenableFuture no classpath.
    exclude(group = "com.google.guava", module = "listenablefuture")
}

// Se as variáveis de ambiente da keystore estiverem presentes (ex: no GitHub Actions),
// o release já sai assinado automaticamente. Localmente, sem essas variáveis, o release
// fica sem assinatura no Gradle - use o assistente "Generate Signed Bundle" do Android
// Studio nesse caso, que assina por fora do Gradle e não é afetado por isto aqui.
val releaseKeystorePath = System.getenv("ANDROID_KEYSTORE_PATH")

android {
    namespace = "com.rotacerta.entregador"
    compileSdk = 36

    defaultConfig {
        // >>> Nome do pacote definitivo do "Flex Otimizador de Rota" — só pode mudar
        // ANTES do primeiro envio à Play Store; depois disso fica travado pra sempre. <<<
        applicationId = "com.flexotimizador.entregador"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseKeystorePath != null) {
            create("release") {
                storeFile = file(releaseKeystorePath)
                storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ANDROID_KEY_ALIAS")
                keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
            }
        }
    }

    // Separação segura entre "estou testando" e "isto é produção", garantida em tempo de
    // compilação (não é uma variável que dá pra mudar depois, é literalmente um build
    // diferente). A variante "closedTesting" SÓ deve ser enviada à faixa de teste fechado
    // do Play Console; "production" é a que vai pra faixa de Produção.
    flavorDimensions += "environment"
    productFlavors {
        create("production") {
            dimension = "environment"
            buildConfigField("boolean", "IS_CLOSED_TESTING", "false")
        }
        create("closedTesting") {
            dimension = "environment"
            buildConfigField("boolean", "IS_CLOSED_TESTING", "true")
            versionNameSuffix = "-teste-fechado"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (releaseKeystorePath != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            isMinifyEnabled = false
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
        buildConfig = true
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
    implementation("androidx.activity:activity-ktx:1.9.1")
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Guava real (não o stub listenablefuture) - necessário pelo CameraX (ProcessCameraProvider
    // retorna ListenableFuture) quando outras libs do projeto (Billing) também dependem disso.
    implementation("com.google.guava:guava:32.1.3-android")

    // Room (persistência local - entregas e histórico)
    // v2.8.2: a 2.6.1 tem um bug conhecido de incompatibilidade com KSP2 (usado a partir
    // do Kotlin 2.0+), corrigido a partir da 2.7.0.
    implementation("androidx.room:room-runtime:2.8.2")
    implementation("androidx.room:room-ktx:2.8.2")
    ksp("androidx.room:room-compiler:2.8.2")

    // DataStore (config do app)
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Networking (ViaCEP + Nominatim)
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // OCR (foto do CEP) via ML Kit, e câmera embutida via CameraX.
    // v1.5.1 do CameraX corrige o alinhamento de 16KB do libimage_processing_util_jni.so
    // (exigência do Google Play; versões anteriores a 1.4.x tinham esse problema).
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("androidx.camera:camera-core:1.5.1")
    implementation("androidx.camera:camera-camera2:1.5.1")
    implementation("androidx.camera:camera-lifecycle:1.5.1")
    implementation("androidx.camera:camera-view:1.5.1")

    // Leitura de QR/código de barras (código de rastreio do pacote) - ZXing.
    // Antes usava com.google.mlkit:barcode-scanning, mas a biblioteca nativa dele
    // (libbarhopper_v3.so) é incompatível com a exigência de 16KB do Google Play e o
    // Google ainda não corrigiu isso em nenhuma versão publicada. ZXing é 100%
    // Java/Kotlin, sem biblioteca nativa - não tem esse problema.
    implementation("com.google.zxing:core:3.5.3")

    // Google Play Billing - assinatura mensal "Flex Pro" (10 dias grátis, depois cobra).
    // v8.3.0: exigência do Google Play a partir de 31/08/2026 é 8.0.0 ou superior.
    implementation("com.android.billingclient:billing-ktx:8.3.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Mapa (osmdroid — mapas do OpenStreetMap, nativo, sem precisar de API key/cadastro)
    implementation("org.osmdroid:osmdroid-android:6.1.20")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
