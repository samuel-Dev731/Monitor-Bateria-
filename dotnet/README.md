# .NET component status

MonitorCore is the C# calculation layer planned for the Android application.
It deliberately contains no Android permissions, hardware access, or fabricated
sensor values. It consumes kernel-counter samples supplied by the native/Kotlin
layer.

Important: this project is a .NET Standard library scaffold. It is not yet
linked into the Android APK. APK integration requires a verified .NET for
Android binding/runtime packaging path and an end-to-end device build. Do not
describe C#/.NET as integrated until that build and runtime test pass.
