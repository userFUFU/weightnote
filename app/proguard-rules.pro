# kotlinx.serialization（备份文件格式）
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class com.weightnote.data.backup.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.weightnote.data.backup.**$$serializer { *; }

# 枚举名会写入数据库 / 设置，保持名称不变
-keepclassmembers,allowoptimization enum com.weightnote.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    <fields>;
}
-keepnames enum com.weightnote.** { *; }

# 后台任务与广播接收器通过类名反射创建
-keep class com.weightnote.data.backup.BackupWorker { <init>(...); }
-keep class com.weightnote.reminder.** extends android.content.BroadcastReceiver { <init>(); }
