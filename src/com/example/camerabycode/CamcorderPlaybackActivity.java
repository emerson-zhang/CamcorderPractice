package com.example.camerabycode;

import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.MediaController;
import android.widget.VideoView;
import android.app.Activity;
import android.content.Intent;

public class CamcorderPlaybackActivity extends Activity {
	private ToolbarItemClickListener toolbarClickListener;
	private Button retakeButton;
	private Button uploadButton;
	private VideoView videoView;

	// private ImageView coverImage;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_camcorder_playback);

		bindAllViews();
		setViewListeners();

		Intent i = getIntent();
		String filepath = i.getStringExtra("filepath");
		initVideoView(filepath);
	}

	@Override
	protected void onPause() {
		super.onPause();
		videoView.stopPlayback();
	}

	private void bindAllViews() {
		retakeButton = (Button) findViewById(R.id.camcorder_playback_retake);
		uploadButton = (Button) findViewById(R.id.camcorder_playback_upload);
		videoView = (VideoView) findViewById(R.id.camcorder_playback_video);
		// coverImage = (ImageView) findViewById(R.id.camcorder_playback_cover);
	}

	private void setViewListeners() {
		toolbarClickListener = new ToolbarItemClickListener();
		retakeButton.setOnClickListener(toolbarClickListener);
		uploadButton.setOnClickListener(toolbarClickListener);
		// coverImage.setOnClickListener(toolbarClickListener);
	}

	private void initVideoView(String filepath) {

		videoView.setVideoPath(filepath);
		MediaController controller = new MediaController(this);
		videoView.setMediaController(controller);
		videoView.requestFocus();
	}

	private class ToolbarItemClickListener implements OnClickListener {

		@Override
		public void onClick(View v) {
			switch (v.getId()) {
			case R.id.camcorder_playback_retake: {
				videoView.stopPlayback();
				break;
			}
			case R.id.camcorder_playback_upload: {
				break;
			}
			default:
				break;
			}
		}

	}

}
