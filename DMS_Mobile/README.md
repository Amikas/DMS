# DMS Archive for Android

Browse and search documents, then tap a document to see its metadata and saved AI summary. The app reads the same REST API as the web app; it does not upload or modify documents.

## Run on an Android emulator

Start the DMS stack with `docker compose up -d --build` from the repository root. With Flutter 3.50 and the Android SDK installed, start an Android emulator and run:

```sh
cd DMS_Mobile
flutter run
```

The emulator app uses `http://10.0.2.2` to reach the host's Compose proxy. For a different server use `flutter run --dart-define=DMS_API_URL=https://your-dms-host`. A physical phone must be able to reach that host; `10.0.2.2` only works in the Android emulator. Plain HTTP is allowed in debug builds for local testing; use HTTPS for non-debug builds.

Run `flutter analyze` and `flutter test` from this directory.
