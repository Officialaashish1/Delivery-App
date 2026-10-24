# Kotlin / coroutines
-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }
# Hilt / Dagger generated code is kept via consumer rules. Keep Application subclass.
-keep class in.localrush.** extends android.app.Application { *; }
# Firebase
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**
# Razorpay (when added): -keep class com.razorpay.** { *; }  -dontwarn com.razorpay.**
# Enum values used by serialization
-keepclassmembers enum * { public static **[] values(); public static ** valueOf(java.lang.String); }
# Source file names for readable crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Tink (EncryptedSharedPreferences) references optional Google HTTP client / Joda classes that are not shipped on Android.
-dontwarn com.google.api.client.http.**
-dontwarn org.joda.time.**
-dontwarn com.google.errorprone.annotations.**
