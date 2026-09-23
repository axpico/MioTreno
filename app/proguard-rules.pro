# WorkManager instantiates Worker subclasses by class name via reflection (the class is only
# reachable through a generic type parameter at the call site) — without this, minification
# would strip or rename them and every scheduled job would crash at runtime with a
# ClassNotFoundException/NoSuchMethodException that no build-time check catches.
-keep public class it.picone.miotreno.work.** extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
