# Regras específicas do projeto — necessárias porque isMinifyEnabled = true no release
# (o R8 renomeia/remove código agressivamente por padrão; sem essas exceções, bibliotecas
# que usam reflexão internamente — Room, Retrofit/Gson, ML Kit, Billing — quebram em
# runtime mesmo compilando sem erro nenhum).

# Room (entidades e DAOs)
-keep class com.rotacerta.entregador.data.** { *; }

# Retrofit / Gson (ViaCEP, Nominatim) — Gson usa reflexão pra (des)serializar os modelos
-keep class com.rotacerta.entregador.network.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**

# ML Kit (OCR do CEP)
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# ZXing (leitor de código de rastreio)
-keep class com.google.zxing.** { *; }

# osmdroid (mapa)
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**

# Google Play Billing (assinatura Flex Pro)
-keep class com.android.billingclient.** { *; }
-dontwarn com.android.billingclient.**

# Guava / ListenableFuture (usado pelo CameraX)
-dontwarn com.google.common.**
-dontwarn com.google.guava.**
