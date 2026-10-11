# PdfBox loads fonts, filters and resources reflectively.
-keep class com.tom_roush.pdfbox.** { *; }
-keep class com.tom_roush.fontbox.** { *; }
-dontwarn org.bouncycastle.**
-dontwarn com.gemalto.jp2.**
