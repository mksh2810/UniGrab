# Rules for youtubedl-android
-keep class com.yausername.youtubedl.** { *; }
-keep class com.yausername.youtubedl_android.** { *; }
-keep class com.yausername.ffmpeg.** { *; }
-keep class com.yausername.aria2c.** { *; }

# Keep Apache Commons Compress which is used internally by youtubedl-android
-keep class org.apache.commons.compress.archivers.zip.** { *; }
-keep class org.apache.commons.compress.compressors.** { *; }

# Keep Room compiler generated classes
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Suppress warnings for optional compression formats in Apache Commons Compress
-dontwarn org.tukaani.xz.**
-dontwarn org.apache.commons.compress.**
