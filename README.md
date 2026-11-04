# AstraTest for Android

```kotlin
implementation("dev.astratest:astratest:0.1.0")

class App : Application() {
  override fun onCreate() { super.onCreate(); AstraTest.start(this, "pk_test_…") }
}
OkHttpClient.Builder().addInterceptor(AstraTest.okHttpInterceptor())
```

- Report button on every screen; shake to report; or `AstraTest.showReport()`.
- Screenshots use PixelCopy (maps and video show) and black out password fields and views passed to `AstraTest.markPrivate(view)`.
- The app's own logcat lines are attached. No dependencies; OkHttp is optional.
