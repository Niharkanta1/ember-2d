# Rules every game's release build gets from ember-android (R8).

# Gamepads (F-005, I-530): gdx-controllers creates its Android manager by class name.
-keep class com.badlogic.gdx.controllers.android.** { *; }
