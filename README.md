Intended as a minimal Android camera2 demo to illustrate a quality issue with Chrome's getUserMedia implementation on Android.

Requests 640x480 at 30 FPS from the first camera ID, with output going to a SurfaceView (with an aspect ratio constraint so it is cropped to fill the screen, in portrait at least).
