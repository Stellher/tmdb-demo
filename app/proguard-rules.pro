# Keep only the DTO reflection contract required by Gson.
# Field names (including unannotated fields such as 'name') are part of JSON API.
# Everything else remains eligible for R8 shrinking, obfuscation and optimization.
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*
-keep class com.stellher.tmdbdemo.core.data.*Dto { *; }

# Retrofit reflects on this endpoint interface. The rest of core:data is optimized.
-keep interface com.stellher.tmdbdemo.core.data.TmdbApi { *; }
