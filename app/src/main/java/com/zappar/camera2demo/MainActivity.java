package com.zappar.camera2demo;

import android.annotation.SuppressLint;
import android.content.Context;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.OutputConfiguration;
import android.hardware.camera2.params.SessionConfiguration;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.util.Range;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity implements SurfaceHolder.Callback {

    private CameraManager cm;
    private CameraDevice camDevice;
    private CameraCaptureSession camCaptureSession;
    private CaptureRequest previewRequest;

    private boolean appResumed = false;
    private boolean hasPermission = false;

    private Surface camSurface;

    private boolean hasRequestedPermission = false;
    private TextView permissionTextView;
    private Button permissionButton;
    private SurfaceView surfaceView;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        cm = (CameraManager) this.getSystemService(Context.CAMERA_SERVICE);
        permissionTextView = findViewById(R.id.permissionTextView);
        permissionButton = findViewById(R.id.permissionButton);
        surfaceView = findViewById(R.id.surfaceView);
        surfaceView.getHolder().setFixedSize(640, 480);
        surfaceView.getHolder().addCallback(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        appResumed = true;
        hasPermission = CameraPermissionHelper.hasCameraPermission(this);
        updateViewVisibility();
        updateCameraState();

        // Request straight away on first resume
        if(!hasPermission && !hasRequestedPermission) {
            CameraPermissionHelper.requestCameraPermission(this);
            hasRequestedPermission = true;
        }

    }

    @Override
    protected void onPause() {
        super.onPause();
        appResumed = false;
        updateCameraState();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        hasPermission = CameraPermissionHelper.hasCameraPermission(this);
        updateViewVisibility();
        updateCameraState();
    }

    @Override
    public void surfaceCreated(@NonNull SurfaceHolder surfaceHolder) {
        camSurface = surfaceHolder.getSurface();
        updateCameraState();
    }

    @Override
    public void surfaceChanged(@NonNull SurfaceHolder surfaceHolder, int format, int width, int height) {
        Log.i("MainActivity", String.format("Surface changed: format %d, width %d, height %d", format, width, height));
    }

    @Override
    public void surfaceDestroyed(@NonNull SurfaceHolder surfaceHolder) {
        camSurface = null;
        updateCameraState();
    }

    private void updateViewVisibility() {
        if(hasPermission) {
            permissionTextView.setVisibility(View.INVISIBLE);
            permissionButton.setVisibility(View.INVISIBLE);
            surfaceView.setVisibility(View.VISIBLE);
            return;
        }

        // No permission yet - hide surface view
        surfaceView.setVisibility(View.INVISIBLE);

        // Update permission text, button action and visibility depending on status
        if(hasRequestedPermission) {
            if(CameraPermissionHelper.shouldShowRequestPermissionRationale(this)) {
                permissionButton.setText(R.string.request_permission);
                permissionButton.setOnClickListener(view -> CameraPermissionHelper.requestCameraPermission(this));
            } else {
                // Permission denied with checking "Do not ask again".
                permissionButton.setText(R.string.open_app_settings);
                permissionButton.setOnClickListener(view -> CameraPermissionHelper.launchPermissionSettings(this));
            }
            permissionTextView.setVisibility(View.VISIBLE);
            permissionButton.setVisibility(View.VISIBLE);
        } else {
            permissionTextView.setVisibility(View.INVISIBLE);
            permissionButton.setVisibility(View.INVISIBLE);
        }
    }

    private void updateCameraState() {
        boolean runCamera = appResumed && hasPermission && camSurface != null;
        if(runCamera) {
            startCamera();
        } else {
            if(camCaptureSession != null) {
                camDevice.close();
                camCaptureSession = null;
                camDevice = null;
                previewRequest = null;
            }
        }
    }

    @SuppressLint("MissingPermission")
    void startCamera() {
        try {
            String[] camIds = cm.getCameraIdList();
            if (camIds.length < 1) return;
            cm.openCamera(camIds[0], new CameraDevice.StateCallback() {
                @Override
                public void onOpened(@NonNull CameraDevice cameraDevice) {
                    Log.i("MainActivity", "Camera device opened!");
                    camDevice = cameraDevice;
                    createSession();
                }

                @Override
                public void onDisconnected(@NonNull CameraDevice cameraDevice) {
                    Log.w("MainActivity", "Camera device disconnected");
                    if(cameraDevice == camDevice) camDevice = null;
                }

                @Override
                public void onError(@NonNull CameraDevice cameraDevice, int i) {
                    Log.e("MainActivity", "Camera device error: " + i);
                    if(cameraDevice == camDevice) camDevice = null;
                }
            }, null);
        } catch (CameraAccessException e) {
            throw new RuntimeException(e);
        }
    }

    void createSession() {
        try {
            ArrayList<Surface> surfaceList = new ArrayList<>();
            surfaceList.add(camSurface);

            CaptureRequest.Builder requestBuilder = camDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            requestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, new Range<>(30, 30));
            requestBuilder.addTarget(camSurface);
            previewRequest = requestBuilder.build();

            CameraCaptureSession.StateCallback stateCallback = new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    Log.i("MainActivity", "Camera Session Configured");
                    camCaptureSession = cameraCaptureSession;
                    startRepeatingRequests();
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                    Log.e("MainActivity", "Camera Session Configure Failed");
                }
            };

            if(Build.VERSION.SDK_INT >= 28) {
                ArrayList<OutputConfiguration> outputConfigs = new ArrayList<>();
                outputConfigs.add(new OutputConfiguration(camSurface));
                SessionConfiguration configuration = new SessionConfiguration(SessionConfiguration.SESSION_REGULAR, outputConfigs, getMainExecutor(), stateCallback);
                configuration.setSessionParameters(previewRequest);
                camDevice.createCaptureSession(configuration);
            } else {
                camDevice.createCaptureSession(surfaceList, stateCallback, null);
            }

        } catch (CameraAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private long previousTimestamp = 0;

    void startRepeatingRequests() {
        try {
            camCaptureSession.setRepeatingRequest(previewRequest, new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);
                    Long timestamp = result.get(TotalCaptureResult.SENSOR_TIMESTAMP);
                    long ts = timestamp != null ? timestamp : 0;
                    Log.i("MainActivity", "Sensor timestamp delta: " + (ts - previousTimestamp));
                    previousTimestamp = ts;
                }
            }, null);
        } catch (CameraAccessException e) {
            throw new RuntimeException(e);
        }
    }

}