# Keep runtime metadata required by Retrofit/Gson/Hilt and generic type parsing.
-keepattributes Signature
-keepattributes Exceptions
-keepattributes InnerClasses,EnclosingMethod
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,AnnotationDefault
-keepattributes *Annotation*

# Hide original source file names in shipped builds while keeping mapping useful.
-renamesourcefileattribute MegaWallet

# App entry point.
-keep class com.mtd.megawallet.MegaWalletApplication { *; }

# Hilt generated dependency graph.
-keep class dagger.hilt.internal.aggregatedroot.codegen.** { *; }
-keep class dagger.hilt.internal.processedrootsentinel.codegen.** { *; }
-keep class hilt_aggregated_deps.** { *; }
-dontwarn dagger.hilt.internal.**

# Retrofit service interfaces are discovered through annotations.
-keep,allowobfuscation interface com.mtd.data.service.** { *; }
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# Retrofit + suspend functions in R8 full mode need Continuation/generic metadata.
-if interface * { @retrofit2.http.* public *** *(...); }
-keep,allowoptimization,allowshrinking,allowobfuscation interface <1>
-if interface * { @retrofit2.http.* public *** *(...); }
-keep,allowoptimization,allowshrinking,allowobfuscation class kotlin.coroutines.Continuation
-keep,allowoptimization,allowshrinking,allowobfuscation class retrofit2.Response
-keep,allowoptimization,allowshrinking,allowobfuscation interface retrofit2.Call

# Gson reflective parsing.
-keep class * extends com.google.gson.reflect.TypeToken
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Jackson/Web3j reflective parsing used by JSON-RPC clients.
-keep class com.fasterxml.jackson.** { *; }
-keep class org.web3j.** { *; }
-keepclassmembers class * {
    @com.fasterxml.jackson.annotation.* <fields>;
    @com.fasterxml.jackson.annotation.* <methods>;
    @com.fasterxml.jackson.annotation.* <init>(...);
}

# Models that are serialized/deserialized reflectively or persisted locally.
-keep class com.mtd.data.dto.** { *; }
-keep class com.mtd.domain.model.** { *; }
-keep class com.mtd.core.model.** { *; }
-keep class com.mtd.data.socket.NotificationSocketManager$* { *; }
-keepclassmembers class com.mtd.data.repository.WalletRepositoryImpl$WalletStorageMetadata { *; }
-keepclassmembers class com.mtd.core.manager.CacheManager$CacheEntry { *; }

# JNI-backed crypto libraries.
-keepclasseswithmembernames class * {
    native <methods>;
}

# Common library noise that does not affect runtime behavior for this app.
-dontwarn org.bouncycastle.**
-dontwarn org.web3j.**
-dontwarn org.slf4j.**
-dontwarn javax.annotation.**
-dontwarn java.beans.ConstructorProperties
-dontwarn java.beans.Transient
-dontwarn com.fasterxml.jackson.databind.ext.**
-dontwarn groovy.lang.GroovyShell
-dontwarn reactor.blockhound.integration.BlockHoundIntegration
-dontwarn sun.misc.**


# اجازه دادن به R8 برای نادیده گرفتن خطاهای رفرنس‌های جاوا ۱۷ در جکسون ۳
-dontwarn tools.jackson.databind.JavaType
-dontwarn tools.jackson.databind.introspect.**
-dontwarn tools.jackson.databind.json.JsonMapper

# باز نگه داشتن کلاس‌های وب۳ و جکسون برای جلوگیری از حذف اشتباه کلاس‌های حیاتی
-keep class tools.jackson.** { *; }

# یک ترفند پروگارد برای بای‌پاس کردن خطای زمان اجرا در متدهای رفلکشن جکسون
-assumenosideeffects class tools.jackson.databind.JavaType {
    public boolean isRecordType();
}

-keepattributes SourceFile,LineNumberTable
# BouncyCastle & Bitcoin Libraries
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**
-keep class org.bitcoinj.** { *; }
-keep class org.bitcoin.** { *; }
# ML Kit Barcode Scanning
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_barcode** { *; }