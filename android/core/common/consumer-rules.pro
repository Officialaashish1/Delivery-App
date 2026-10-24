# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class in.localrush.**$$serializer { *; }
-keepclassmembers class in.localrush.** { *** Companion; }
-keepclasseswithmembers class in.localrush.** { kotlinx.serialization.KSerializer serializer(...); }
