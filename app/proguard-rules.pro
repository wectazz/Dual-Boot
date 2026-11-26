-dontobfuscate
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Keep AIDL & RootService classes
-keep class com.dualboot.dsu.IPrivilegedService** { *; }
-keep class com.dualboot.dsu.service.** { *; }
-keep class android.gsi.** { *; }
-keep class android.os.storage.** { *; }
-keep class com.topjohnwu.superuser.** { *; }
-keep class org.lsposed.hiddenapibypass.** { *; }
