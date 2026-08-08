# MarkLeaf release R8 rules
# Goal: shrink Compose + PDFBox without breaking PDF metadata / Room / navigation.

# ── BuildConfig / app entry ──
-keep class com.markleaf.app.BuildConfig { *; }
-keep class com.markleaf.app.MainActivity { *; }

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

# ── PDFBox Android (reflection / service loaders / font maps) ──
# Used for PDF metadata/outline; page bitmaps use Android PdfRenderer.
-keep class com.tom_roush.** { *; }
-keep class org.apache.fontbox.** { *; }
-keep class org.apache.pdfbox.** { *; }
-dontwarn com.tom_roush.**
-dontwarn org.apache.fontbox.**
-dontwarn org.apache.pdfbox.**
-dontwarn org.bouncycastle.**

# Keep PDFBox service files
-keepclassmembers class * {
    @com.tom_roush.pdfbox.pdmodel.** *;
}

# ── Serialization / Parcelable if any ──
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# ── Prefer smaller stack traces in field only if needed ──
# -renamesourcefileattribute SourceFile
# -keepattributes SourceFile,LineNumberTable
