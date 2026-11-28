# R8 rules for the demo's release build (F-010; rules from spike S-04).
# Component keep rules are generated into build/generated/ember-keep-rules.pro (task emberKeepRules).

# libGDX (adapted from the gdx-liftoff template): the Android backend and classes reached by reflection.
-dontwarn android.support.**
-dontwarn com.badlogic.gdx.backends.android.AndroidFragmentApplication
-keep public class com.badlogic.gdx.scenes.scene2d.** { *; }
-keep public class com.badlogic.gdx.graphics.g2d.BitmapFont { *; }
-keep public class com.badlogic.gdx.graphics.Color { *; }

# Readable stack traces (the mapping file is kept with releases from I-255).
-keepattributes LineNumberTable,SourceFile
-renamesourcefileattribute SourceFile

# Box2D (F-006B, the Crates scene): the native code calls back into World by name (contacts, filters, queries).
-keep class com.badlogic.gdx.physics.box2d.** { *; }
