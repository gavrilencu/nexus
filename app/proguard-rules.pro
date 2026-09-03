# NEXUS release (R8) keep rules.

# --- BouncyCastle (MITM CA generation / signing) ---
# Providers are looked up reflectively by name; keep the whole tree and silence
# warnings about JCE/JDK classes BC references but Android does not ship.
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**
-dontwarn javax.naming.**

# --- OkHttp / Okio ---
# Both ship consumer rules; these silence optional platform integrations that
# R8 would otherwise warn about.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
-dontwarn org.bouncycastle.jsse.**

# --- Kotlin metadata (safe defaults) ---
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault,Signature,InnerClasses,EnclosingMethod

# Keep enum values() / valueOf() used across the UI state machines.
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
