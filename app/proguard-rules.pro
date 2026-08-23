# TZVPN VPN ProGuard Rules

# Keep JNI methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep NativeBridge class and its JNI callback methods
-keep class app.tzvpn.data.native.NativeBridge { *; }
-keep class app.tzvpn.data.native.NativeCallback { *; }
-keep class app.tzvpn.data.native.NativeConfig { *; }
-keep class app.tzvpn.data.native.NativeStats { *; }

# Keep SlipstreamBridge and tunnel classes for JNI
-keep class app.tzvpn.tunnel.SlipstreamBridge { *; }
-keep class app.tzvpn.tunnel.** { *; }
-keepclassmembers class app.tzvpn.tunnel.SlipstreamBridge {
    native <methods>;
    private native <methods>;
}
# Prevent R8 from optimizing away native method declarations
-keepclasseswithmembers class * {
    native <methods>;
}

# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.lifecycle.HiltViewModel { *; }
-keep class app.tzvpn.Hilt_* { *; }
-keep class app.tzvpn.*_GeneratedInjector { *; }
-keep class dagger.hilt.internal.** { *; }
-keep class * implements dagger.hilt.internal.GeneratedComponent { *; }
-keep class * implements dagger.hilt.internal.GeneratedComponentManager { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# DataStore
-keep class androidx.datastore.** { *; }

# Kotlin Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# Gson - preserve generic type information for TypeToken
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Keep domain models for Gson serialization
-keep class app.tzvpn.domain.model.** { *; }
-keep class app.tzvpn.data.local.database.** { *; }
-keep class app.tzvpn.data.mapper.** { *; }

# JSch SSH library
-keep class com.jcraft.jsch.** { *; }
-dontwarn com.jcraft.jsch.**

# Compose
-dontwarn androidx.compose.**

# ZXing QR code
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**
-keep class com.journeyapps.barcodescanner.** { *; }
-dontwarn com.journeyapps.barcodescanner.**
