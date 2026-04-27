# Add project specific ProGuard rules here.

# ---- MPAndroidChart ----
-keep class com.github.mikephil.charting.** { *; }

# ---- Room Database ----
# エンティティ・DAO のフィールド名を保持（Room はリフレクションを使用）
-keep class com.example.behavioranalysis.data.entity.** { *; }
-keep class com.example.behavioranalysis.data.dao.** { *; }
-keepclassmembers class * {
    @androidx.room.* <fields>;
    @androidx.room.* <methods>;
}

# ---- Kotlin Coroutines ----
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# ---- ViewBinding ----
-keep class com.example.behavioranalysis.databinding.** { *; }

# ---- クラッシュレポートのためスタックトレースを保持 ----
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
