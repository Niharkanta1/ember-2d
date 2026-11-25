# SPIKE S-04 - R8 rules for the minified demo.

# libGDX (adapted from the gdx-liftoff template): the Android backend and Scene2D/BitmapFont/Color, which are
# reached by reflection (Json, skins).
-dontwarn android.support.**
-dontwarn com.badlogic.gdx.backends.android.AndroidFragmentApplication
-keep public class com.badlogic.gdx.scenes.scene2d.** { *; }
-keep public class com.badlogic.gdx.graphics.g2d.BitmapFont { *; }
-keep public class com.badlogic.gdx.graphics.Color { *; }
-keepattributes LineNumberTable,SourceFile
-renamesourcefileattribute SourceFile

# What a component registry (ember.data, I-130) would generate per registered component: keep the class, its
# no-argument constructor and its fields by name, so it can be created by name and filled from JSON.
-keep class dev.ember2d.demo.platformer.android.SpikeKeptComponent {
    <init>();
    <fields>;
}
-keep class dev.ember2d.demo.platformer.android.SpikeKeptJson {
    <init>();
    <fields>;
}
# SpikeUnkeptComponent and SpikeUnkeptJson deliberately have no rules.
