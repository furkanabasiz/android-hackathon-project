# Smile Hair Clinic Mobile Application

This project was developed as part of a hackathon and is an Android mobile application designed to guide patients through the necessary photo-taking for the **pre-hair transplant evaluation process** and to manage their appointments. The application supports two distinct user roles: patient and consultant.

## 🚀 Features

The application primarily includes the following main features:

* **User Authentication:** Secure login and registration system via Firebase Authentication.
* **Appointment Management:** Users can create new appointments and track their existing ones.
    * Appointments are stored in the Firestore database using the `Appointment.kt` model, which includes fields for **patient name**, **date/time**, and **status**.
    * Feedback messages for appointment creation and displaying selected appointment information are provided in Turkish (e.g., "Randevu oluşturuldu." - Appointment created).
* **Guided Photo Capture:** A specially designed camera interface and flow to ensure patients take photos from the correct angles.
    * An optimized camera experience is provided using the **CameraX** library.
    * The **ML Kit Face Detection** feature is used to help guide the user by ensuring the face is correctly framed and positioned.
    * Captured images are uploaded to **Firebase Storage**.
* **Consultant Panel:** Ability to review appointment requests and evaluation photos submitted by the patient (Inferred from role separation).
* **User Interface:** **View Binding** is utilized, and navigation is handled by the **Android Navigation Component** (fragment-based) for a modern and clean user experience.

---

## 🛠️ Technologies and Dependencies

This project is developed in Kotlin, adhering to modern Android development principles, and uses the following core libraries:

| Category | Technology / Library | Purpose |
| :--- | :--- | :--- |
| **Language** | Kotlin, Java 11 (Source/Target Compatibility) | Project language and compatibility level. |
| **Android Components** | AndroidX (Core, AppCompat, Activity, ConstraintLayout, Fragment, RecyclerView) | Core Android architecture and UI components. |
| **Navigation** | Navigation Component (`navigation-fragment`, `navigation-ui`, `safeargs`) | Managing screen transitions within the application. |
| **Camera** | CameraX (`camera-core`, `camera-camera2`, `camera-lifecycle`, `camera-view`) | Controlling the device camera and image capture. |
| **Machine Learning** | ML Kit Face Detection (`com.google.mlkit:face-detection:16.1.5`) | Guiding the user during photo capture by detecting the face. |
| **Backend** | **Firebase BOM** (`com.google.firebase:firebase-bom:34.5.0`) | Version management for all Firebase services. |
| | Firebase Authentication (`firebase-auth`) | User authentication. |
| | Firebase Firestore (`firebase-firestore`) | Storing appointment and user data (`Appointment` model). |
| | Firebase Storage (`firebase-storage`) | Storing captured photos. |
| | Firebase Analytics (`firebase-analytics`) | Usage analytics. |
| **Asynchronous Prog.**| Kotlin Coroutines (`kotlinx-coroutines-android:1.7.3`, `kotlinx-coroutines-play-services:1.7.3`) | Managing asynchronous operations (especially Firebase and network tasks). |
| **Image Loading**| Glide (`com.github.bumptech.glide:glide:4.16.0`) | Efficient image loading and caching. |

---

## ⚙️ Setup

Follow these steps to run the project on your local machine:

### Prerequisites

* Android Studio
* Java/Kotlin compatible environment (JVM Target 11).
* A **Firebase Project** with an app configured for the package name: `com.hackathon.smilehairclinic`.

### Steps

1.  **Clone the Repository:**
    ```bash
    git clone https://github.com/furkanabasiz/android-hackathon-project
    ```

2.  **Firebase Configuration:**
    * Place the `google-services.json` file, downloaded from your Firebase project, into the `app/` directory (Required by `com.google.gms.google-services` plugin).
    * For the project to run smoothly, Firebase Auth, Firestore, and Storage services must be enabled in the Firebase console.

3.  **Required Permissions:**
    The application requires the following Android permissions for core functionality (already defined in `AndroidManifest.xml`):
    * `android.permission.CAMERA`
    * `android.permission.INTERNET`
    * `android.permission.WRITE_EXTERNAL_STORAGE`
    * It also requires hardware support for the camera, accelerometer, and gyroscope.

4.  **Build and Run:**
    * Open the project in Android Studio.
    * Wait for Gradle synchronization to complete to download the necessary dependencies.
    * Run the project on an Android emulator or physical device. The main entry point is `MainActivity`.
