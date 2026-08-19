# SuperFlow ProGuard rules.
#
# The app is currently shipped unminified (isMinifyEnabled = false in both
# build types). These rules are the safe floor for the day minification is
# turned on: keep the entry points that are reflected on or instantiated by
# the framework.

-keep class com.superflow.** { *; }

# WorkManager workers are instantiated by name.
-keep class com.superflow.work.** { *; }

# Broadcast receivers referenced from the manifest.
-keep class com.superflow.notify.** { *; }
-keep class com.superflow.widget.TodayWidget { *; }
