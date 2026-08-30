# M1racleReader release R8 rules
# Goal: shrink Compose + PDFBox without breaking PDF metadata / Room / navigation.

# ── BuildConfig / app entry ──
-keep class com.m1racle.reader.BuildConfig { *; }
-keep class com.m1racle.reader.MainActivity { *; }

# ── Room ──
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# ── Kotlin / Coroutines ──
-dontwarn kotlinx.coroutines.**
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# ── Jetpack Compose (R8 full mode is generally safe; keep preview only if needed) ──
-dontwarn androidx.compose.**

# ── Backdrop liquid glass (AGSL / RuntimeShader / graphics layers) ──
-keep class com.kyant.backdrop.** { *; }
-keep class com.kyant.shapes.** { *; }
-dontwarn com.kyant.backdrop.**
-dontwarn com.kyant.shapes.**
-dontwarn org.jetbrains.compose.**

# ── PDFBox Android ──
# Display uses Android PdfRenderer. PDFBox is used for outline, page
# extract/export, fonts, and encrypted-PDF security.
# R40 experiment: do not blanket-keep com.tom_roush.**. R8 traces the
# app's static entry points. The AAR consumer rules already keep:
#   - public constructors of every SecurityHandler subclass
#   - com.tom_roush.pdfbox.pdmodel.documentinterchange.**
# The rules below cover remaining reflection / optional paths only.

-dontwarn com.tom_roush.**
-dontwarn org.apache.fontbox.**
-dontwarn org.apache.pdfbox.**
-dontwarn org.bouncycastle.**
-dontwarn com.gemalto.jp2.**

# SecurityProvider constructs BouncyCastleProvider directly and also
# loads it by binary name as a fallback.
-keep class org.bouncycastle.jce.provider.BouncyCastleProvider {
    public <init>();
}

# PDNumberTreeNode uses
# valueType.getDeclaredConstructor(base.getClass()).newInstance(base).
# Proven value types in pdfbox-android 2.0.27.0:
#   PDPageLabelRange(COSDictionary)
#   PDParentTreeValue(COSArray|COSDictionary) — kept by AAR
#     documentinterchange.** rule.
-keep class com.tom_roush.pdfbox.pdmodel.common.PDPageLabelRange {
    public <init>(com.tom_roush.pdfbox.cos.COSDictionary);
}

# FilterFactory constructs JPXFilter statically. JPXFilter looks up the
# optional com.gemalto.jp2.JP2Decoder (not shipped). Keep the filter so
# the optional path remains; do not keep a decoder we do not ship.
-keep class com.tom_roush.pdfbox.filter.JPXFilter {
    public <init>();
}

# ── Serialization / Parcelable if any ──
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# ── Prefer smaller stack traces in field only if needed ──
# -renamesourcefileattribute SourceFile
# -keepattributes SourceFile,LineNumberTable

# ── Release diagnostics ──
# Debug/verbose telemetry is useful in QA but must not execute in release.
# Warning/error logs remain available for actionable failures.
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
}
