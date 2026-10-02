# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class com.weightnote.data.backup.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
