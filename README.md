# Timesheet Tracker (Android)

A simple and efficient Android application to track your working hours and manage project-based timesheets.

## Features

- **Project Management**: Create and manage multiple projects.
- **Time Tracking**: Easily clock in and out of tasks.
- **Manual Entries**: Add or edit timesheet entries manually.
- **History & Reports**: View your daily working entries and overall history.

## Download APK

You can download the latest version of the app as an APK file from the project directory:
- [Download Release APK](app/build/outputs/apk/release/app-release.apk) (Signed)

## Run Locally

**Prerequisites:** [Android Studio](https://developer.android.com/studio)

1. **Clone the repository**:
   ```bash
   git clone https://github.com/kunalagrawal2611/timesheet-tracker.git
   ```
2. **Open in Android Studio**: Select **Open** and choose the directory containing this project.
3. **Configure API Key**:
   - Create a file named `.env` in the project root directory.
   - Add your Gemini API key: `GEMINI_API_KEY=your_api_key_here`. (Refer to `.env.example` for the format).
4. **Build and Run**:
   - Allow Android Studio to sync Gradle and download dependencies.
   - Run the app on an emulator or a physical device.

## Building the APK

To build a release APK, run the following command in the terminal:
```bash
./gradlew :app:assembleRelease
```
The generated APK will be located at `app/build/outputs/apk/release/app-release-unsigned.apk`.

---
*Note: This project uses a conditional signing configuration. If no keystore file is provided, the build will produce an unsigned APK.*
