# pytorch jni的so库在初始化时会调用其在java中方法，所以需要保留这些类和方法
-keep class org.pytorch.PyTorchAndroid { *; }
-keep class org.pytorch.NativePeer { *; }

-keep class com.vitals.sdk.parcel.** { *; }
