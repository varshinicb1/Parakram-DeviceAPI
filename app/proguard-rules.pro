# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Keep line numbers and source files for beautiful, readable crash reporting and stack traces
-keepattributes SourceFile,LineNumberTable,Signature,InnerClasses,EnclosingMethod,*Annotation*

# Obfuscate but preserve the name of source files
-renamesourcefileattribute SourceFile

# Optimize Kotlin standard library and Coroutines
-keep class kotlin.Metadata { *; }
-keep class kotlinx.coroutines.** { *; }
-keepclassmembers class * extends kotlinx.coroutines.internal.MainDispatcherFactory {
    public *;
}

# Preserve all serializable/deserializable network and database request/response data classes in com.example.data
-keep class com.example.data.** { *; }

# Prevent obfuscating types needed for JSON and database persistence integration
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
    @com.squareup.moshi.Json <fields>;
}

# Ktor Server and Clients Keep Rules
-keep class io.ktor.** { *; }
-keep class io.netty.** { *; }

# Moshi and OkHttp configuration
-keep class com.squareup.moshi.** { *; }
-keep class okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**

# Retrofit Keep Rules
-keep class retrofit2.** { *; }
-keepclassmembers class * {
    @retrofit2.http.* <methods>;
}
-dontwarn retrofit2.**

# Jetpack Compose and Material 3
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# Firebase AI and Services Keep Rules
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

