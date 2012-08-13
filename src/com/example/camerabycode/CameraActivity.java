package com.example.camerabycode;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.Camera.AutoFocusCallback;
import android.hardware.Camera.Parameters;
import android.hardware.SensorManager;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;

public class CameraActivity extends Activity {
	private static final String TAG = "CameraPreview";
	public static final int MEDIA_TYPE_VIDEO = 2;
	private CameraPreview mPreview;
	private Camera mCamera;
	private CameraAutoFocusCallback mAutoFocusCallback;
	private boolean mAlreadyFocused;
	private AccelerometerListener mAccelerometerListener;
	private MediaRecorder mMediaRecorder;
	private SensorManager sensorManager;
	private String videoFileName;

	private boolean isRecording = false;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_camera);

		mAccelerometerListener = new AccelerometerListener();
		sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
	}

	@Override
	protected void onPause() {
		super.onPause();
		isRecording = false;
		sensorManager.unregisterListener(mAccelerometerListener);
		releaseMediaRecorder(); // 如果正在使用MediaRecorder，首先需要释放它。
		releaseCamera(); // 在暂停事件中立即释放摄像头
		removePreviewSurface();
	}

	@Override
	protected void onResume() {
		super.onResume();
		preparePreviewSurface();

	}

	private void preparePreviewSurface() {
		if (checkCameraHardware(CameraActivity.this)) {
			mCamera = getCameraInstance();
			if (mCamera != null) {
				// 没被占用
				mPreview = new CameraPreview(CameraActivity.this, mCamera);
				ViewGroup vg = (ViewGroup) findViewById(R.id.camera_preview_lay);
				vg.addView(mPreview);
			} else {
				// 告知用户摄像头被占用
			}
		}
	}

	private void removePreviewSurface() {
		ViewGroup vg = (ViewGroup) findViewById(R.id.camera_preview_lay);
		vg.removeView(mPreview);
	}

	/** Check if this device has a camera */
	private boolean checkCameraHardware(Context context) {
		if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)) {
			// this device has a camera
			return true;
		} else {
			// no camera on this device
			return false;
		}
	}

	/**
	 * A safe way to get an instance of the Camera object.
	 * 
	 * @return Camera object. If something wrong happened, it will return null
	 */
	public static Camera getCameraInstance() {
		Camera c = null;
		try {
			c = Camera.open(); // attempt to get a Camera instance
		} catch (Exception e) {
			// Camera is not available (in use or does not exist)
		}
		return c; // returns null if camera is unavailable
	}

	/** A basic Camera preview class */
	public class CameraPreview extends SurfaceView implements SurfaceHolder.Callback {
		private SurfaceHolder mHolder;
		private Camera mCamera;

		public CameraPreview(Context context, Camera camera) {
			super(context);
			mCamera = camera;

			// Install a SurfaceHolder.Callback so we get notified when the
			// underlying surface is created and destroyed.
			mHolder = getHolder();
			mHolder.addCallback(this);
			// deprecated setting, but required on Android versions prior to 3.0
			mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

			this.setKeepScreenOn(true);
		}

		public void surfaceCreated(SurfaceHolder holder) {
			// 旋转屏幕
			mCamera.setDisplayOrientation(90);

			try {
				mCamera.setPreviewDisplay(holder);
				mCamera.startPreview();

				// 实例化自动对焦回调
				mAutoFocusCallback = new CameraAutoFocusCallback();
				// 尝试设置自动对焦
				setCameraFocus(mAutoFocusCallback);

				sensorManager.registerListener(mAccelerometerListener,
						sensorManager.getDefaultSensor(SensorManager.SENSOR_ACCELEROMETER),
						SensorManager.SENSOR_DELAY_NORMAL);

			} catch (IOException e) {
				Log.d(TAG, "Error setting camera preview: " + e.getMessage());
			}
		}

		public void surfaceDestroyed(SurfaceHolder holder) {
			// empty. Take care of releasing the Camera preview in your
			// activity.
		}

		public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
			// If your preview can change or rotate, take care of those events
			// here.
			// Make sure to stop the preview before resizing or reformatting it.

			if (mHolder.getSurface() == null) {
				// preview surface does not exist
				return;
			}

			// stop preview before making changes
			try {
				mCamera.stopPreview();
			} catch (Exception e) {
				// ignore: tried to stop a non-existent preview
			}

			// start preview with new settings
			try {
				mCamera.setPreviewDisplay(mHolder);
				mCamera.startPreview();

			} catch (Exception e) {
				Log.d(TAG, "Error starting camera preview: " + e.getMessage());
			}
		}
	}

	private boolean prepareVideoRecorder() {

		mMediaRecorder = new MediaRecorder();

		// 第1步：解锁并将摄像头指向MediaRecorder
		mCamera.stopPreview();
		mCamera.unlock();
		mMediaRecorder.setCamera(mCamera);

		// // 第2步：指定源
		mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
		mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
		//
		// // 第3步：设置输出格式和编码格式（针对>=API Level 8版本）
		if (getAndroidSDKVersion() >= 8) {
			mMediaRecorder.setProfile(CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH));
		} else {
			mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
			mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT);
			mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.DEFAULT);
		}

		// 第4步：指定输出文件
		videoFileName = getOutputMediaFile(MEDIA_TYPE_VIDEO).toString();
		mMediaRecorder.setOutputFile(videoFileName);

		// 第5步：指定预览输出
		mMediaRecorder.setPreviewDisplay(mPreview.getHolder().getSurface());

		// 第6步：根据以上配置准备MediaRecorder
		try {
			mMediaRecorder.prepare();
		} catch (IllegalStateException e) {
			Log.d(TAG, "IllegalStateException preparing MediaRecorder: " + e.getMessage());
			releaseMediaRecorder();
			return false;
		} catch (IOException e) {
			Log.d(TAG, "IOException preparing MediaRecorder: " + e.getMessage());
			releaseMediaRecorder();
			return false;
		}
		return true;
	}

	/** Create a File for saving an image or video */
	private static File getOutputMediaFile(int type) {
		// To be safe, you should check that the SDCard is mounted
		// using Environment.getExternalStorageState() before doing this.
		File mediaStorageDir = new File(Environment.getExternalStorageDirectory(), "b5m");

		// Create the storage directory if it does not exist
		if (!mediaStorageDir.exists()) {
			if (!mediaStorageDir.mkdirs()) {
				Log.d("MyCameraApp", "failed to create directory");
				return null;
			}
		}
		// Create a media file name
		String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
		File mediaFile;
		if (type == MEDIA_TYPE_VIDEO) {
			mediaFile = new File(mediaStorageDir.getPath() + File.separator + "VID_" + timeStamp + ".mp4");
		} else {
			return null;
		}
		return mediaFile;
	}

	private void releaseMediaRecorder() {
		if (mMediaRecorder != null) {
			mMediaRecorder.reset(); // clear recorder configuration
			mMediaRecorder.release(); // release the recorder object
			mMediaRecorder = null;
			mCamera.lock(); // lock camera for later use
		}
	}

	private void releaseCamera() {
		if (mCamera != null) {
			mCamera.release(); // 为其它应用释放摄像头
			mCamera = null;
		}
	}

	// 使用MediaRecorder类开始和停止视频录制时，必须遵循以下特定顺序。
	// 1. 用Camera.unlock()解锁摄像头
	// 2. 如上代码所示配置MediaRecorder
	// 3. 用MediaRecorder.start()开始录制
	// 4. 记录视频
	// 5. 用MediaRecorder.stop()停止录制
	// 6. 用MediaRecorder.release()释放media recorder
	// 7. 用Camera.lock()锁定摄像头
	public void beginCapture(View v) {
		if (isRecording) {
			// 已经处于录像状态
			// 停止录像并释放camera
			mMediaRecorder.stop(); // 停止录像
			// releaseMediaRecorder(); // 释放MediaRecorder对象
			// mCamera.lock(); // 将控制权从MediaRecorder 交回camera
			// // 通知用户录像已停止
			// setTitle("Capture");
			// isRecording = false;

			Intent i = new Intent(this, CamcorderPlaybackActivity.class);
			i.putExtra("filepath", videoFileName);
			startActivity(i);

		} else {
			// 不在录像状态，准备录像
			// 先解除重力感应，解除自动对焦功能
			sensorManager.unregisterListener(mAccelerometerListener);
			// 初始化视频camera
			if (prepareVideoRecorder()) {
				// Camera已可用并解锁，MediaRecorder已就绪,
				// 现在可以开始录像
				mMediaRecorder.start();

				// 通知用户录像已开始
				setTitle("Stop");
				isRecording = true;
			} else {
				// 准备未能完成，释放camera
				releaseMediaRecorder();
				// 通知用户
			}
		}
	}

	/**
	 * 检查sdk版本
	 * 
	 * @return
	 */
	private int getAndroidSDKVersion() {
		int version = 0;
		try {
			version = Integer.valueOf(android.os.Build.VERSION.SDK);
		} catch (NumberFormatException e) {
		}
		return version;
	}

	/**
	 * 设置自动对焦功能
	 * 
	 * @param autoFocus
	 * @return 如果设备支持自动对焦，就返回true 否则为false
	 */
	private boolean setCameraFocus(AutoFocusCallback autoFocus) {
		if (mCamera.getParameters().getFocusMode().equals(Parameters.FOCUS_MODE_AUTO)
				|| mCamera.getParameters().getFocusMode().equals(Parameters.FOCUS_MODE_MACRO)) {
			mCamera.autoFocus(autoFocus);
			return true;
		}
		return false;
	}

	/**
	 * 镜头对焦回调
	 * 
	 * @author Emerson
	 * 
	 */
	private class CameraAutoFocusCallback implements AutoFocusCallback {

		@Override
		public void onAutoFocus(boolean success, Camera camera) {
			mAlreadyFocused = true;
		}
	}

	/**
	 * 重力感应监听，主要用于触发重新对焦动作
	 * 
	 * @author Emerson
	 * 
	 */
	private class AccelerometerListener implements SensorEventListener {
		private float mLastX = 0;
		private float mLastY = 0;
		private float mLastZ = 0;

		@Override
		public void onAccuracyChanged(Sensor sensor, int accuracy) {

		}

		@Override
		public void onSensorChanged(SensorEvent event) {

			float x = event.values[0];
			float y = event.values[1];
			float z = event.values[2];
			float deltaX = Math.abs(mLastX - x);
			float deltaY = Math.abs(mLastY - y);
			float deltaZ = Math.abs(mLastZ - z);
			// System.out.println(event.values[0] + " " + event.values[1] + "  "
			// + event.values[2] + "  "
			// + mAlreadyFocused);

			if (deltaX > 1 && mAlreadyFocused) { // AUTOFOCUS (while it is not
				mAlreadyFocused = false;
				setCameraFocus(mAutoFocusCallback);
			}
			if (deltaY > 1 && mAlreadyFocused) { // AUTOFOCUS (while it is not
				mAlreadyFocused = false;
				setCameraFocus(mAutoFocusCallback);
			}
			if (deltaZ > 1 && mAlreadyFocused) { // AUTOFOCUS (while it is not
				mAlreadyFocused = false;
				setCameraFocus(mAutoFocusCallback);
			}

			mLastX = x;
			mLastY = y;
			mLastZ = z;
		}
	}
}
