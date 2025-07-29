# TecniApp ICE

TecniApp ICE is an Android application developed in Kotlin to manage field operations for the ICE organization. It integrates Firebase services, Google Maps and a local Room database to provide modules such as meter management, location, inventory, luminarias and more.

## Build requirements

- **Android Studio Flamingo or newer** with Android SDK 34
- **Gradle 8.7** (provided via the Gradle wrapper)
- **JDK 17** or newer

## Setup

1. Clone this repository.
2. Ensure a `local.properties` file exists in the project root with the path to your Android SDK. Android Studio creates this automatically. Example:

   ```properties
   sdk.dir=/path/to/Android/Sdk
   ```
3. Place your Firebase configuration file (`google-services.json`) under `app/` if it is not already present.
4. (Optional) Update the `google_maps_key` in `app/src/main/res/values/strings.xml` with your own Google Maps API key.

## Building and running

Open the project with Android Studio and let it synchronize the Gradle configuration. You can then build and run the application on a device or emulator using the Run action. From the command line you may also build with:

```bash
./gradlew assembleDebug
```

## Usage

After launching the app you will be presented with a login screen using Firebase Authentication. Once logged in, the main screen allows navigation to modules such as:

- **Home** – quick overview and links to features.
- **Medidor** – meter queries and updates.
- **Localización** – map view and location sharing.
- **Averías** – reporting and viewing service issues.
- **Luminarias** – management for public lighting.
- **Inventario** – equipment and inventory management.
- **Programación** – scheduling tasks or events.
- **Reportes** – access to generated reports.

The application synchronizes data with Firebase and stores information locally using Room to support offline use.

