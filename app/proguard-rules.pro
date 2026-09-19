# GXcomputer - container/motor sınıfları reflection ve JNI ile çalıştığı için korunuyor
-keep class com.gxcomputer.app.container.** { *; }
-keep class com.github.luben.zstd.** { *; }
-dontwarn org.apache.commons.compress.**
