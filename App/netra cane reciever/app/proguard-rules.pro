# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in C:\Users\...\AppData\Local\Android\Sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.

# Keep Kotlin Coroutines internal names if minifying
-keepattributes *Annotation*,InnerClasses,EnclosingMethod

# Keep Netra Cane data models or activities
-keep class com.netra.smartcane.** { *; }
