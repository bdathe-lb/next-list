# Firebase — keep model classes and reflection targets
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }

# Hilt — generated components
-keep class dagger.hilt.** { *; }
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }

# Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Coil — image loading
-keep class coil3.** { *; }

# Keep data classes used in Firestore deserialization
-keepclassmembers class com.example.nextlist.** {
    public <init>(...);
}
