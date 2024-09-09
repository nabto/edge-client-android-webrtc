# Nabto Edge WebRTC Android Library

This library is used to implement Nabto Edge WebRTC in an Android app. This library is used to implement the demo app on [Google Play](https://play.google.com/store/apps/details?id=com.nabto.edge.webrtcdemo) and [GitHub](https://github.com/nabto/edge-android-webrtc).


# Signaling V2 notes

In signalign V2 the peerconnection is not a part of the API. With this change it is up to the user to find an appropriate webrtc library, e.g. the getstream webrtc-android library.

The getstream android library just exposes the org.webrtc classes, but because several packages are exposing this namespace it is not a good idea to have that library as a direct dependency.