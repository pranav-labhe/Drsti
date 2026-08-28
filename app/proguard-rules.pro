# Add project specific ProGuard rules here.
-keepattributes *Annotation*
-keep class com.pranav.drsti.database.entity.** { *; }
-keep class com.pranav.drsti.model.** { *; }

# R8 missing rules for ErrorProne annotations
-dontwarn com.google.errorprone.annotations.CanIgnoreReturnValue
-dontwarn com.google.errorprone.annotations.CheckReturnValue
-dontwarn com.google.errorprone.annotations.Immutable
-dontwarn com.google.errorprone.annotations.RestrictedApi
