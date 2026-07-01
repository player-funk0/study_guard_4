# StudyGuard

> An intelligent, privacy-first Android study companion powered by on-device AI.

StudyGuard is a comprehensive educational application designed to enhance productivity and learning efficiency. Built with a strong focus on privacy, it operates entirely offline, leveraging on-device LLMs (Large Language Models) via MediaPipe to provide intelligent summarization and tutoring without mandatory cloud dependencies.

---

## Table of Contents
- [Key Features](#key-features)
- [Technology Stack & Architecture](#technology-stack--architecture)
- [Project Structure](#project-structure)
- [Getting Started](#getting-started)
  - [Prerequisites](#prerequisites)
  - [Installation](#installation)
- [AI Model Configuration](#ai-model-configuration)
- [Application Modules](#application-modules)
- [Data & Storage](#data--storage)
- [Testing](#testing)
- [Troubleshooting](#troubleshooting)
- [Contributing](#contributing)
- [License](#license)

---

## Key Features

- **On-Device AI Tutor**: Engage with a fully localized, intelligent tutoring system capable of adapting to various subjects (e.g., Mathematics, Physics) using the Gemma LLM.
- **Advanced Summarization Engine**:
  - *Text Summarizer*: Supports both AI-driven summaries and a fast, local TextRank algorithm fallback.
  - *Book Summarizer*: Efficiently processes large `.txt` files using a two-stage Map-Reduce pipeline and text chunking.
- **Productivity Tracking**:
  - *Study Timer*: A robust background timer with foreground notifications, customizable study/break intervals, and automated session recording.
  - *Session Analytics*: Comprehensive tracking of daily/weekly study metrics, streaks, and historical logs.
- **Resource Management**: Manage and categorize learning materials with responsive UI layouts adapted for diverse screen sizes.
- **Digital Wellbeing**: Monitor daily application usage and digital habits directly within the app (requires Usage Access permissions).

## Technology Stack & Architecture

StudyGuard is engineered using modern Android development practices to ensure scalability, performance, and maintainability.

- **Language**: Kotlin (`1.9.22`)
- **UI Toolkit**: Jetpack Compose + Material Design 3
- **Architecture**: Single-Activity, Model-View-ViewModel (MVVM)
- **Dependency Injection**: Manual DI via ServiceLocator
- **Local Persistence**: Room Database (SQLite), DataStore Preferences
- **Concurrency**: Kotlin Coroutines & Flows
- **AI Integration**: MediaPipe `tasks-genai` for on-device inference

## Project Structure

```text
StudyGuard/
├── app/
│   ├── src/main/java/com/obrynex/studyguard/
│   │   ├── ai/                 # On-device model management, validation, and logging
│   │   ├── aitutor/            # AI Tutor interface and view models
│   │   ├── booksummarizer/     # Map-Reduce pipeline and text chunking logic
│   │   ├── data/               # Room entities, DAOs, and repository layer
│   │   ├── timer/              # Foreground timer service
│   │   ├── tracker/            # Analytics and session history
│   │   └── ...                 # Additional feature modules (UI, Islamic, Wellbeing)
│   └── src/test/               # Comprehensive unit test suites
├── build.gradle.kts            # Project-level Gradle configuration
├── app/build.gradle.kts        # Module-level Gradle configuration
└── build.sh                    # Automated build script
```

## Getting Started

### Prerequisites

To build and run this project, ensure your development environment meets the following requirements:
- **IDE**: Android Studio (latest stable release)
- **Java**: JDK 17
- **Android SDK**: Compile and Target SDK 34 (Minimum SDK 26)
- **AGP**: Android Gradle Plugin `8.2.2`

### Installation

1. **Clone the repository** and open the `StudyGuard` directory in Android Studio.
2. **Sync Gradle** to download necessary dependencies.
3. **Build the application** using the IDE or via command line:

**Windows (PowerShell):**
```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat installDebug
```

**macOS/Linux:**
```bash
chmod +x gradlew
./gradlew assembleDebug
./gradlew installDebug
```

## AI Model Configuration

For the AI-powered features (Tutor, Book Summarizer, and AI Summarizer mode) to function, a compatible model must be loaded onto the device.

1. **Download the Model**:
   - Required filename: `gemma-2b-it-cpu-int4.bin` (Approx. 1.3GB)
   - [Download Source (Kaggle)](https://www.kaggle.com/models/google/gemma/tfLite/gemma-2b-it-cpu-int4)
2. **Initialize App Storage**: Launch the app once to generate the required application-specific directories.
3. **Transfer the Model**:
   Push the model file to the device via ADB. Replace `[package_name]` with `com.obrynex.studyguard` for Release or `com.obrynex.studyguard.debug` for Debug builds:
   ```bash
   adb push gemma-2b-it-cpu-int4.bin /sdcard/Android/data/[package_name]/files/models/
   ```
4. **Verification**: Navigate to `More > Debug Info` in the app. The AI Engine status should read **Ready**.

## Application Modules

- **Timer Tab**: Configure study sessions. Sessions are automatically logged to the Room database upon completion.
- **Materials Tab**: Organize study notes. Use the `+` action to add, and utilize the search/filter functionalities to retrieve specific content.
- **Summarizer Tab**: Paste text to generate concise summaries. Toggle between local AI or algorithm-based modes depending on performance needs.
- **Tracker Tab**: Visualize study productivity through detailed statistical breakdowns and historical logs.
- **More Tab**: Access supplementary tools including the Book Summarizer, Digital Wellbeing metrics, and diagnostic information.

## Data & Storage

All user data is strictly localized. No external databases are utilized.
- **Relational Data (Room)**: Stores `study_sessions` and `learning_materials`.
- **Preferences (DataStore)**: Manages application state, onboarding status, and AI model metadata.

## Testing

The project maintains test coverage for critical algorithms and use cases.

Run Unit Tests:
```bash
./gradlew testDebugUnitTest
```

Run Instrumented Tests:
```bash
./gradlew connectedDebugAndroidTest
```

Code Quality (Linting):
```bash
./gradlew lint
```

## Troubleshooting

- **AI Initialization Fails (`NotFound` or `SizeTooSmall`)**: Ensure the `.bin` file is correctly named, fully downloaded, and placed in the precise directory path. Refer to the Debug Info screen for specific error logs.
- **Missing Timer Notifications**: Verify that notifications are explicitly enabled for the application in Android System Settings (Requires `POST_NOTIFICATIONS` permission on Android 13+).
- **No Digital Wellbeing Data**: The app requires `Usage Access` permission. Grant this in your device's security or privacy settings.

## Contributing

Contributions are welcome. Please adhere to the following workflow:
1. Fork the repository.
2. Create a feature branch (`git checkout -b feature/AmazingFeature`).
3. Ensure all tests pass prior to submission.
4. Open a Pull Request detailing the changes and their impact.

## License

This project is provided "as-is" for educational and personal use. All rights reserved.
