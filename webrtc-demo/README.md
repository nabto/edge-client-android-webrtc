# webrtc-demo

A small Kotlin demo app (Jetpack Compose) that uses the `webrtc` library to show a video
feed from a Nabto Edge WebRTC device.

The app works with the device demo in
[edge-device-webrtc/examples/simple-webrtc](https://github.com/nabto/edge-device-webrtc/tree/main/examples/simple-webrtc):
after the Nabto connection and WebRTC signaling are established, the app sends a single
`POST /webrtc/tracks` CoAP request (no payload) and the device attaches its video track to
the connection.

## Running

Build and install on a connected device:

```shell
./gradlew :webrtc-demo:installDebug
```

The main screen shows the video feed at the top and a status log below it that narrates
every step of the connection (Nabto connection events, WebRTC signaling, track requests
and any errors), mirrored to logcat with the tag `WebRTCDemo`.

## Configuration

Use *Settings* in the top bar to set the product id, device id and server connect token of
the device to connect to. The values are persisted and take effect immediately when
returning to the video screen.
