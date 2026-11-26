# Nearby Connections ships its own consumer rules; keep our protocol models readable in crash logs.
-keepattributes SourceFile,LineNumberTable
-keep class com.intercom.ride.net.Message** { *; }
