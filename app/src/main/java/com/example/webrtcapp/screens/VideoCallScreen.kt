package com.example.webrtcapp.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults.topAppBarColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnection.IceServer
import org.webrtc.PeerConnectionFactory
import org.webrtc.RendererCommon
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.SurfaceViewRenderer
import timber.log.Timber
import java.util.concurrent.Executors


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoCallScreen(roomID: String, onNavigateBack: () -> Unit) {

    val executor = remember { Executors.newSingleThreadExecutor() }
    val firestore = remember { FirebaseFirestore.getInstance() }

    val eglBase = remember { EglBase.create() }
    var peerConnectionFactory: PeerConnectionFactory? = remember { null }
    var peerConnection: PeerConnection? = remember { null }

    val queuedRemoteCandidates = remember { arrayListOf<IceCandidate>() }
    val localCandidatesToShare = remember { arrayListOf<Map<String, Any?>>() }
    var isOfferer = remember { false }
    var remoteDescriptionSet = remember { false }
    val sdpMediaConstraints: MediaConstraints = remember {
        MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", true.toString()))
        }
    }

    val context = LocalContext.current

    //renderers
    var localRenderer by remember { mutableStateOf<SurfaceViewRenderer?>(null) }
    var remoteRenderer by remember { mutableStateOf<SurfaceViewRenderer?>(null) }

    fun sendSignalingMessage(message: Map<String, Any?>) {
        Timber.d("sendSignalingMessage() :: ")

        val signalingRef = firestore.collection("rooms").document(roomID)
        signalingRef.set(message, SetOptions.merge())
    }

    fun createPeerConnection() {
        Timber.d("createPeerConnection")

        val iceServers = IceServer.builder(
            listOf(
                "stun:stun1.l.google.com:19302",
                "stun:stun2.l.google.com:19302"
            )
        )

        val rtcCong = PeerConnection.RTCConfiguration(listOf(iceServers.createIceServer()))

        peerConnection = peerConnectionFactory?.createPeerConnection(
            rtcCong,
            object : PeerConnection.Observer {
                override fun onSignalingChange(p0: PeerConnection.SignalingState?) {

                }

                override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
                    super.onConnectionChange(newState)
                    Timber.d("createPeerConnection() :: onConnectionChange $newState")
                }

                override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState?) {

                }

                override fun onIceConnectionReceivingChange(p0: Boolean) {

                }

                override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {

                }

                override fun onIceCandidate(candidate: IceCandidate?) {

                    candidate?.let {

                        val key = if (isOfferer) "iceOffer" else "iceAnswer"
                        localCandidatesToShare.add(
                            mapOf(
                                "candidate" to it.sdp,
                                "sdpMid" to it.sdpMid,
                                "sdpMLineIndex" to it.sdpMLineIndex
                            )
                        )

                        // send to signalling server.
                        sendSignalingMessage(
                            mapOf(key to localCandidatesToShare)
                        )
                    }

                }

                override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {

                }

                override fun onAddStream(streams: MediaStream?) {
                    Timber.d("onAddStream")
                    streams?.videoTracks?.firstOrNull()?.addSink(remoteRenderer)
                }

                override fun onRemoveStream(p0: MediaStream?) {

                }

                override fun onDataChannel(p0: DataChannel?) {

                }

                override fun onRenegotiationNeeded() {

                }

                override fun onAddTrack(p0: RtpReceiver?, p1: Array<out MediaStream>?) {

                }

            }
        )

    }

    fun initializeWebRTC() {
        Timber.d("initializeWebRTC() :: ")


        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions
                .builder(context)
                .setEnableInternalTracer(true)
                .createInitializationOptions()
        )

        val videoEncoderFactory = DefaultVideoEncoderFactory(
            /* eglContext = */ eglBase.eglBaseContext,
            /* enableIntelVp8Encoder = */ true,
            /* enableH264HighProfile = */ false
        )

        val videoDecoderFactory = DefaultVideoDecoderFactory(eglBase.eglBaseContext)

        peerConnectionFactory = PeerConnectionFactory
            .builder()
            .setVideoEncoderFactory(videoEncoderFactory)
            .setVideoDecoderFactory(videoDecoderFactory)
            .createPeerConnectionFactory()
    }

    var localSdp: SessionDescription? = remember { null }

    val sdpObserver = object : SdpObserver {
        override fun onCreateSuccess(sessionDescription: SessionDescription) {
            Timber.d("onCreateSuccess")
            if (localSdp != null) {
                Timber.e("Multiple Session Description created.")
                return
            }

            localSdp = sessionDescription

            executor.execute {
                peerConnection?.setLocalDescription(this, sessionDescription)
            }
        }

        override fun onSetSuccess() {

            if (localSdp == null) return
            executor.execute {
                if (isOfferer) {
                    if (peerConnection?.remoteDescription == null) { //answer not receiver.
                        sendSignalingMessage(
                            mapOf(
                                "type" to "offer",
                                "sdpOffer" to localSdp?.description
                            )
                        )
                    } else {
                        remoteDescriptionSet = true
                        addQueuedCandidates()
                    }
                } else {
                    if (peerConnection?.localDescription != null) {
                        sendSignalingMessage(
                            mapOf(
                                "type" to "answer",
                                "sdpAnswer" to localSdp?.description
                            )
                        )
                        remoteDescriptionSet = true
                        addQueuedCandidates()
                    }
                }
            }

        }

        private fun addQueuedCandidates() {
            queuedRemoteCandidates.forEach {
                peerConnection?.addIceCandidate(it)
            }
            queuedRemoteCandidates.clear()
        }

        override fun onCreateFailure(p0: String?) {

        }

        override fun onSetFailure(p0: String?) {

        }

    }

    fun createAnswer() {
        peerConnection?.createAnswer(sdpObserver, sdpMediaConstraints)
    }

    fun handleSignallingMessage(data: MutableMap<String, Any>) {

        //candidates.
        if (isOfferer && data["iceAnswer"] != null) {
            executor.execute {
                val cMDataList = data["iceAnswer"] as List<*>
                cMDataList.forEach { map ->
                    val cData = map as HashMap<*, *>
                    val candidate = IceCandidate(
                        /* sdpMid = */ cData["sdpMid"] as String,
                        /* sdpMLineIndex = */ (cData["sdpMLineIndex"] as Long).toInt(),
                        /* sdp = */ cData["candidate"] as String,
                    )

                    //remoteDescription.
                    if (remoteDescriptionSet) {
                        peerConnection?.addIceCandidate(candidate)
                    } else {
                        queuedRemoteCandidates.add(candidate)
                    }
                }
                //clear out.
                sendSignalingMessage(mapOf("iceAnswer" to null))
            }
        }

        if (!isOfferer && data["iceOffer"] != null) {
            executor.execute {
                val cMDataList = data["iceOffer"] as List<*>
                cMDataList.forEach { map ->
                    val cData = map as HashMap<*, *>
                    val candidate = IceCandidate(
                        /* sdpMid = */ cData["sdpMid"] as String,
                        /* sdpMLineIndex = */ (cData["sdpMLineIndex"] as Long).toInt(),
                        /* sdp = */ cData["candidate"] as String,
                    )

                    //remoteDescription.
                    if (remoteDescriptionSet) {
                        peerConnection?.addIceCandidate(candidate)
                    } else {
                        queuedRemoteCandidates.add(candidate)
                    }
                }
                //clear out.
                sendSignalingMessage(mapOf("iceOffer" to null))
            }
        }


        if (isOfferer.not() && data["sdpOffer"] != null) {
            executor.execute {
                val offerSdp = data["sdpOffer"] as String
                val offer = SessionDescription(SessionDescription.Type.OFFER, offerSdp)
                sendSignalingMessage(mapOf("sdpOffer" to null))

                peerConnection?.setRemoteDescription(sdpObserver, offer)
                createAnswer()
            }
        }

        if (isOfferer && data["sdpAnswer"] != null) {
            executor.execute {
                val answerSdp = data["sdpAnswer"] as String
                val answer = SessionDescription(SessionDescription.Type.ANSWER, answerSdp)
                sendSignalingMessage(mapOf("sdpAnswer" to null))
                peerConnection?.setRemoteDescription(sdpObserver, answer)
            }
        }
    }

    fun deleteFirestoreDoc() {
        val signalingRef = firestore.collection("rooms").document(roomID)
        signalingRef.delete()
    }

    fun setupFirebaseListeners() {
        Timber.d("setupFirebaseListeners")

        val signalingRef = firestore.collection("rooms").document(roomID)

        signalingRef.addSnapshotListener { value, error ->
            if (error != null) {
                error.printStackTrace()
                return@addSnapshotListener
            }
            value?.data?.let {
                handleSignallingMessage(it)
            }
        }
    }

    fun checkRoomCapacityAndSetup(
        onProceed: () -> Unit
    ) {
        val roomDocRef = firestore.collection("rooms").document(roomID)

        roomDocRef.get().addOnSuccessListener { document ->
            Timber.d("Firebase firestore success")
            if (document != null && document.exists()) {
                val participantCount = (document["participantCount"] as? Long)?.toInt() ?: 0
                if (participantCount >= 2) {
                    Toast.makeText(
                        context,
                        "Room is FULL. Cannot join at the moment.!",
                        Toast.LENGTH_SHORT
                    ).show()
                    onNavigateBack.invoke()
                } else {
                    roomDocRef.update("participantCount", participantCount + 1)
                    onProceed.invoke()
                }
            } else {
                //create room.
                roomDocRef.set(mapOf("participantCount" to 1))
                isOfferer = true
                onProceed.invoke()
            }
        }.addOnFailureListener {
            Timber.e("Firebase Failed to get Firestore DB.")
            Toast.makeText(
                context,
                "Firebase Failed to get Firestore DB.",
                Toast.LENGTH_SHORT
            ).show()
            onNavigateBack.invoke()
        }

    }

    fun createCameraCapturer(): CameraVideoCapturer? {
        val cameraEnumator = Camera2Enumerator(context)
        val devicesNames = cameraEnumator.deviceNames

        for (deviceName in devicesNames) {
            if (cameraEnumator.isFrontFacing(deviceName)) {
                val videoCapturer = cameraEnumator.createCapturer(deviceName, null)
                if (videoCapturer != null) {
                    return videoCapturer
                }
            }
        }

        for (deviceName in devicesNames) {
            if (!cameraEnumator.isFrontFacing(deviceName)) {
                val videoCapturer = cameraEnumator.createCapturer(deviceName, null)
                if (videoCapturer != null) {
                    return videoCapturer
                }
            }
        }

        return null

    }

    //local rendering.
    fun initializeLocalMediaStream() {
        Timber.d("initializeLocalMediaStream")

        val videoSource = peerConnectionFactory?.createVideoSource(false)
        videoSource ?: run {
            Timber.e("Video Source are null!")
        }

        val videoCapturer = createCameraCapturer()

        videoCapturer?.let { vc ->

            vc.initialize(
                SurfaceTextureHelper.create("CaptureThread", eglBase.eglBaseContext),
                context,
                videoSource?.capturerObserver
            )

            vc.startCapture(1280, 720, 30)
        }

        val videoTrack = peerConnectionFactory?.createVideoTrack("1001", videoSource)
        val mediaStream = peerConnectionFactory?.createLocalMediaStream("mediaStream")
        mediaStream?.addTrack(videoTrack)
        peerConnection?.addStream(mediaStream)
        videoTrack?.addSink(localRenderer)
    }

    fun createOffer() {
        Timber.d("createOffer()")
        peerConnection?.createOffer(sdpObserver, sdpMediaConstraints)
    }

    DisposableEffect(Unit) {
        onDispose {
            executor.execute {
                localRenderer?.release()?.also {
                    localRenderer = null
                }
                remoteRenderer?.release()?.also {
                    remoteRenderer = null
                }

                peerConnection?.dispose()?.also {
                    peerConnection = null
                }
                peerConnectionFactory?.dispose()?.also {
                    peerConnectionFactory = null
                }
                eglBase.release()

                PeerConnectionFactory.stopInternalTracingCapture()
                PeerConnectionFactory.shutdownInternalTracer()
                deleteFirestoreDoc()
            }
            executor.shutdown()
        }
    }

    LaunchedEffect(Unit) {
        checkRoomCapacityAndSetup(
            onProceed = {
                executor.execute {
                    initializeWebRTC()
                    createPeerConnection()
                    initializeLocalMediaStream()
                    setupFirebaseListeners()
                    if (isOfferer) {
                        createOffer()
                    }
                }
            }
        )
    }

    Scaffold(topBar = {
        TopAppBar(colors = topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            titleContentColor = MaterialTheme.colorScheme.primary
        ), title = {
            Text(text = "Video Call", fontWeight = FontWeight.Bold)
        }, navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
            }
        }
        )
    }) { innerPadding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues = innerPadding)
        ) {

            AndroidView(
                factory = { context ->
                    SurfaceViewRenderer(context).apply {
                        init(eglBase.eglBaseContext, null)
                        setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
                        localRenderer = this
                    }
                },
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f)
            )

            Spacer(modifier = Modifier.height(10.dp))

            AndroidView(
                factory = { context ->
                    SurfaceViewRenderer(context).apply {
                        init(eglBase.eglBaseContext, null)
                        setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
                        remoteRenderer = this
                    }
                }, modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f)
            )
        }
    }
}

