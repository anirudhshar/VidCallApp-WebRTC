# VideoCallApp

## Overview
VideoCallApp is a video calling application built using **Jetpack Compose**, **Firebase Firestore**, and the **WebRTC Framework**. The app enables seamless one-on-one video calls between users by leveraging modern Android development practices.

## Features
- **Real-time Video Calls:** Supports high-quality video calling using WebRTC.
- **Firebase Firestore Integration:** Manages signaling and room participant data.
- **Jetpack Compose:** Implements a fully declarative UI.
- **Room Management:** Dynamically handles room creation and participant limits.

## Tech Stack
- **Kotlin**
- **Jetpack Compose** for UI
- **Firebase Firestore** for signaling and database management
- **WebRTC Framework** for real-time communication
- **Timber** for logging
- **Coroutines and Executors** for background tasks

## Prerequisites
1. **Firebase Setup**:
   - Add a Firebase project and configure Firestore.
   - Download the `google-services.json` file and place it in the `app/` directory.
   - Enable Firestore in the Firebase Console.

2. **Dependencies**:
   Add the following dependencies to your `build.gradle` file:
   ```kotlin
   implementation "org.webrtc:google-webrtc:1.0.32006"
   implementation "com.google.firebase:firebase-firestore-ktx:24.7.1"
   implementation "androidx.compose.ui:ui:1.6.0"
   implementation "androidx.compose.material3:material3:1.1.0"
   implementation "com.jakewharton.timber:timber:5.0.1"
   ```

3. **Internet Permission**:
   Ensure your `AndroidManifest.xml` has the necessary permissions:
   ```xml
   <uses-permission android:name="android.permission.INTERNET"/>
   ```

## Architecture
The app follows a declarative UI paradigm with business logic separated from the UI layer. It uses **Jetpack Compose** for building the UI and **Firestore** for managing real-time updates.

### Key Components
- **WebRTC Initialization**:
  - Creates and configures `PeerConnectionFactory`, `PeerConnection`, and media constraints.
- **Firestore Integration**:
  - Handles room creation, signaling messages, and participant count updates.
- **Signaling**:
  - Uses Firestore to exchange SDP offers, answers, and ICE candidates.

## Room Workflow
1. **Room Initialization**:
   - On joining, the app checks the participant count in the Firestore room document.
   - If the room is full, the user is prevented from joining.
   - Otherwise, the participant count is updated, and the user proceeds.

2. **Signaling**:
   - Exchanges SDP offers and answers through Firestore.
   - Shares ICE candidates for establishing a peer-to-peer connection.

3. **Video Rendering**:
   - Local and remote video streams are rendered using `SurfaceViewRenderer`.

## Setup Instructions
1. Clone this repository:
   ```bash
   git clone <repository-url>
   cd VideoCallApp
   ```

2. Configure Firebase:
   - Add the `google-services.json` file to your app module.

3. Build and run the project in Android Studio.

## Code Highlights
### Main Video Call Composable
```kotlin
@Composable
fun VideoCallScreen(roomID: String, onNavigateBack: () -> Unit) {
    val firestore = FirebaseFirestore.getInstance()
    val eglBase = EglBase.create()

    var peerConnection: PeerConnection? = null
    val sdpMediaConstraints = MediaConstraints().apply {
        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", true.toString()))
    }

    val localRenderer = remember { SurfaceViewRenderer(LocalContext.current) }
    val remoteRenderer = remember { SurfaceViewRenderer(LocalContext.current) }

    // Set up WebRTC and signaling...
}
```

### Signaling Message Handling
```kotlin
fun handleSignalingMessage(data: MutableMap<String, Any>) {
    if (data["sdpOffer"] != null) {
        val offerSdp = data["sdpOffer"] as String
        val offer = SessionDescription(SessionDescription.Type.OFFER, offerSdp)
        peerConnection?.setRemoteDescription(sdpObserver, offer)
        createAnswer()
    }
}
```

## Room Management
```kotlin
fun checkRoomCapacityAndSetup(onProceed: () -> Unit) {
    val roomDocRef = FirebaseFirestore.getInstance().collection("rooms").document(roomID)
    roomDocRef.get().addOnSuccessListener { document ->
        if (document.exists()) {
            val participantCount = (document["participantCount"] as? Long)?.toInt() ?: 0
            if (participantCount >= 2) {
                Toast.makeText(context, "Room is FULL", Toast.LENGTH_SHORT).show()
                onNavigateBack()
            } else {
                roomDocRef.update("participantCount", participantCount + 1)
                onProceed()
            }
        } else {
            roomDocRef.set(mapOf("participantCount" to 1))
            onProceed()
        }
    }
}
```

## License
This project is not licensed.

## Future Improvements
- Support for multiple participants (group calls).
- Enhanced error handling and reconnection logic.
- Integration with Firebase Authentication for user management.

