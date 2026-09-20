# Add project specific ProGuard rules here.

# Keep line number and source file info for crash reporting, but obfuscate their names.
-keepattributes SourceFile,LineNumberTable,*Annotation*,Signature,EnclosingMethod,InnerClasses
-renamesourcefileattribute SourceFile

# Keep JavascriptInterfaces because WebView relies on reflection/names to invoke them from JS
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep the specific classes/members registered as Javascript Interfaces
-keep class com.gsvn.aamusic.web.ImeKeyboardBridge { *; }
-keepclassmembers class com.gsvn.aamusic.web.ImeKeyboardBridge {
    public <methods>;
}

# Android Auto and general androidx.car.app rules
-keep class androidx.car.app.** { *; }
-dontwarn androidx.car.app.**

# Keep standard Android classes
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider

# Keep custom binding classes if reflection is used
-keep class com.gsvn.aamusic.databinding.** { *; }
