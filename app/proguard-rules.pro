-keep class org.lsposed.hiddenapibypass.** { *; }

# 保留 WebView JavascriptInterface 方法，避免 3D 車模 JS Bridge 被混淆裁切
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# 保留內部數據模型 class 與欄位
-keepclassmembers class com.aibox.launcher.** {
    public <fields>;
}
