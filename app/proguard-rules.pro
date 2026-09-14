# Mediara AI release rules (R8)
#
# Moshi (kotlin-codegen): keep generated adapters and JsonClass classes,
# otherwise DTO de/serialization breaks under obfuscation.
-keepattributes Signature, *Annotation*, InnerClasses, EnclosingMethod
-keep @com.squareup.moshi.JsonClass class *
-keepclassmembers class * {
    @com.squareup.moshi.JsonClass *;
}
-keepclassmembers class * {
    @com.squareup.moshi.FromJson <methods>;
    @com.squareup.moshi.ToJson <methods>;
}
-keepnames class kotlin.jvm.internal.DefaultConstructorMarker

# Room ships its own consumer rules; keep entities + DAO implementations reachable.
-keep class com.mediara.app.data.local.Entities { *; }
-keep class com.mediara.app.data.local.MediationDao { *; }

# OkHttp / Retrofit keep their own consumer rules; silence reflection noise.
-dontwarn okhttp3.internal.**
-dontwarn org.codehaus.mojo.animal_sniffer.**

# security-crypto (Tink) references errorprone annotations that are compile-only.
-dontwarn com.google.errorprone.annotations.**

# Preserve stack-trace source info for crash reports.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile