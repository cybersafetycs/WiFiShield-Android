# =====================================================================
#  WiFiShield — R8 / ProGuard rules (release build)
#  Used with proguard-android-optimize.txt (optimization enabled, so
#  -assumenosideeffects actually takes effect).
# =====================================================================

# ---------------------------------------------------------------------
# 1) Crashlytics needs real file names + line numbers in stack traces.
#    -keepattributes SourceFile,LineNumberTable  -> keep those attributes
#    -renamesourcefileattribute SourceFile       -> collapse every file
#       name to the same string "SourceFile" (still keeps line numbers,
#       but reveals less about your file layout).
#    If missing: NO crash — but every Crashlytics stack trace shows
#    "Unknown Source" and you can't tell which file/line crashed.
# ---------------------------------------------------------------------
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ---------------------------------------------------------------------
# 2) Aggressive shrinking / renaming (the core "hard to copy" settings).
#    -repackageclasses ''  -> move every renamed class into the single
#       empty root package, so package names leak nothing about your
#       structure (no com.cybersafetycs.Analyzer visible to jadx).
#    -allowaccessmodification -> let R8 widen package-private members
#       to public while merging/repackaging, enabling more optimization.
#    -overloadaggressively   -> allow methods with identical signatures
#       after renaming to be merged/overloaded.
#    If wrong (a class you reach via reflection got renamed): crashes
#    like ClassNotFoundException / NoSuchMethodError. That is why every
#    reflection / JNI target below is explicitly kept.
# ---------------------------------------------------------------------
-repackageclasses ''
-allowaccessmodification
-overloadaggressively

# ---------------------------------------------------------------------
# 3) Strip developer logging from release builds.
#    -assumenosideeffects tells R8: "these calls do nothing, delete them
#    and their unused results." Only d/v/i (debug/verbose/info) are
#    removed — w/e stay so real problems are still visible in logcat.
#    Requires the OPTIMIZING proguard file (proguard-android-optimize),
#    otherwise the rule is silently ignored.
#    If wrong: if you added a method that only LOOKS like Log but is
#    actually needed, it would be deleted — so only android.util.Log
#    is listed here, nothing custom.
# ---------------------------------------------------------------------
-assumenosideeffects class android.util.Log {
    public static int d(java.lang.String, java.lang.String);
    public static int d(java.lang.String, java.lang.String, java.lang.String);
    public static int v(java.lang.String, java.lang.String);
    public static int v(java.lang.String, java.lang.String, java.lang.String);
    public static int i(java.lang.String, java.lang.String);
    public static int i(java.lang.String, java.lang.String, java.lang.String);
}

# ---------------------------------------------------------------------
# 4) JNI (NDK native code) — NativeAnalyzer.
#    Java_<class>_<method> symbols are looked up by NAME at runtime.
#    If R8 renames the class or the native method ->
#    UnsatisfiedLinkError and the app crashes the moment analysis runs.
#    Keeping "native <methods>" keeps the class name AND the method
#    names, but still lets R8 rename all NON-native members.
# ---------------------------------------------------------------------
-keep class com.cybersafetycs.wifishield.NativeAnalyzer { native <methods>; }
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# ---------------------------------------------------------------------
# 5) App entry point (manifest-launched activity).
#    The manifest already protects it, but the explicit keep documents
#    intent and survives manifest merges. Wrong -> "Unable to
#    instantiate activity ... ClassNotFoundException" at app launch.
# ---------------------------------------------------------------------
-keep public class com.cybersafetycs.wifishield.MainActivity {
    public <init>();
}

# ---------------------------------------------------------------------
# 6) FbKit reflection facade + Firebase wiring classes.
#    FbKit looks classes up with Class.forName("...fb.Fb") because the
#    no-Firebase build simply doesn't have them. Renaming would make
#    every Firebase call silently no-op (Firebase "mysteriously stops
#    working" — no crash, but push/crash reports die).
# ---------------------------------------------------------------------
-keep class com.cybersafetycs.wifishield.FbKit { *; }
-keep class com.cybersafetycs.wifishield.fb.** { *; }

# ---------------------------------------------------------------------
# 7) Firebase core classes referenced by name / kept for the SDK.
#    Wrong -> crashes inside Firebase init at first app start
#    (e.g. FirebaseApp.getInstance() fails, app dies on launch).
# ---------------------------------------------------------------------
-keep class com.google.firebase.FirebaseApp { *; }
-keep class com.google.firebase.FirebaseOptions { *; }
-keep class com.google.firebase.analytics.FirebaseAnalytics { *; }
-keep class com.google.firebase.messaging.FirebaseMessagingService { *; }
-keep class com.google.firebase.remoteconfig.FirebaseRemoteConfig { *; }

# ---------------------------------------------------------------------
# 8) Firestore (model classes + anything Firebase reflects over).
#    Firestore de/serializes POJOs by READING FIELD NAMES + annotations
#    at runtime. If fields are renamed, documents silently write/read
#    the wrong keys — no crash, but data ends up under different field
#    names than your rules expect.
#      - keep classes annotated with @PropertyName / @Exclude members
#      - keep the public API surface Firestore hands to your code
#    (The Firestore SDK also ships its own consumer rules in the AAR;
#    these are the extra rules for YOUR model layer.)
# ---------------------------------------------------------------------
-keepclassmembers class * {
    @com.google.firebase.firestore.PropertyName <fields>;
    @com.google.firebase.firestore.PropertyName <methods>;
    @com.google.firebase.firestore.Exclude <fields>;
    @com.google.firebase.firestore.Exclude <methods>;
}
-keep class com.google.firebase.firestore.FirebaseFirestore { *; }
-keep class com.google.firebase.firestore.CollectionReference { *; }
-keep class com.google.firebase.firestore.DocumentReference { *; }
-keep class com.google.firebase.firestore.Query { *; }
-keep class com.google.firebase.firestore.DocumentSnapshot { *; }
-keep class com.google.firebase.firestore.QuerySnapshot { *; }

# ---------------------------------------------------------------------
# 9) Firebase Auth classes you touch from the app side.
#    Wrong -> FirebaseAuth.getInstance()/currentUser misbehaves or the
#    SDK's reflection into your callbacks breaks after renaming.
# ---------------------------------------------------------------------
-keep class com.google.firebase.auth.FirebaseAuth { *; }
-keep class com.google.firebase.auth.FirebaseUser { *; }

# ---------------------------------------------------------------------
# 10) Keep generic signatures + runtime annotations (used by Gson-style
#     reflection, Firestore, and Firebase's own listeners).
#     Wrong -> TypeNotPresentException / missing annotations in
#     Firestore POJO mapping (silent data problems, occasionally
#     crashes when a reflected generic type disappears).
# ---------------------------------------------------------------------
-keepattributes Signature
-keepattributes *Annotation*

# ---------------------------------------------------------------------
# 11) Library warnings: Firebase/Play-services reference optional
#     classes (ads, wear, auth.ui ...) that are not on our classpath.
#     Suppressing keeps R8 from failing the build on those missing
#     optional deps. This does NOT keep any code alive.
# ---------------------------------------------------------------------
-dontwarn com.google.firebase.auth.**
-dontwarn com.google.android.gms.**
