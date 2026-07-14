# Build notes

- XML resources and AndroidManifest were parsed successfully.
- Kotlin source files passed a basic syntax/balance check.
- A full Android Gradle build was not run in the generation environment because the Android SDK and Gradle dependency cache were not installed there.
- GitHub Actions workflow is included to perform the real `:app:assembleDebug` build.

After the first CI build, resolve any dependency availability issue by updating versions in `build.gradle.kts`; the app source itself does not depend on proprietary libraries.
