# Linphone usa JNI: no se ofusca
-keep class org.linphone.** { *; }
-dontwarn org.linphone.**
-keepparameternames
-keepattributes Exceptions,InnerClasses,Signature,Deprecated,SourceFile,LineNumberTable,*Annotation*,EnclosingMethod
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}
