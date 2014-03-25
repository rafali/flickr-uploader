package com.rafali.flickruploader.ui.activity;

import org.slf4j.LoggerFactory;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.TextView;

import com.google.analytics.tracking.android.EasyTracker;
import com.googlecode.androidannotations.annotations.AfterViews;
import com.googlecode.androidannotations.annotations.Background;
import com.googlecode.androidannotations.annotations.Click;
import com.googlecode.androidannotations.annotations.EActivity;
import com.googlecode.androidannotations.annotations.UiThread;
import com.googlecode.androidannotations.annotations.ViewById;
import com.rafali.common.STR;
import com.rafali.common.ToolString;
import com.rafali.flickruploader.AppInstall;
import com.rafali.flickruploader.api.FlickrApi;
import com.rafali.flickruploader.tool.RPC;
import com.rafali.flickruploader.tool.Utils;
import com.rafali.flickruploader2.R;

@EActivity(R.layout.flickr_web_auth_activity)
public class FlickrWebAuthActivity extends Activity {

	public static final int RESULT_CODE_AUTH = 2227;
	static final org.slf4j.Logger LOG = LoggerFactory.getLogger(FlickrWebAuthActivity.class);

	@ViewById(R.id.progress_container)
	View progressContainer;

	@ViewById(R.id.error_container)
	View errorContainer;

	@ViewById(R.id.progress_text)
	TextView progressText;

	@ViewById(R.id.error_text)
	TextView errorText;

	@Click(R.id.error_button)
	void onErrorClick() {
		loadAuthorizationUrl();
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		requestWindowFeature(Window.FEATURE_PROGRESS);
		getActionBar().setDisplayHomeAsUpEnabled(true);
		super.onCreate(savedInstanceState);
	}

	@SuppressLint({ "SetJavaScriptEnabled", "NewApi" })
	@AfterViews
	protected void onAfterViews() {
		setLoading("Opening browser...");
		loadAuthorizationUrl();
	}

	@Background
	void loadAuthorizationUrl() {
		try {
			Utils.saveAndroidDevice();
			String url = "http://ra-fa-li.appspot.com/flickr?oauth_redirect=true&device_id=" + Utils.getDeviceId();
			Intent i = new Intent(Intent.ACTION_VIEW);
			i.setData(Uri.parse(url));
			startActivity(i);
			setLoading("Waiting for browser info...");
		} catch (Throwable e) {
			setError(e);
		}
	}

	@Override
	protected void onStart() {
		super.onStart();
		EasyTracker.getInstance(this).activityStart(this);
	}

	boolean paused = false;

	@Override
	protected void onStop() {
		super.onStop();
		EasyTracker.getInstance(this).activityStop(this);
	}

	@Override
	protected void onPause() {
		paused = true;
		super.onPause();
	}

	@Override
	protected void onResume() {
		if (paused) {
			doDataCallback();
		}
		paused = false;
		super.onResume();
	}

	@UiThread
	void setLoading(String message) {
		errorContainer.setVisibility(View.GONE);
		progressContainer.setVisibility(View.VISIBLE);
		progressText.setText(message);
	}

	@UiThread
	void setError(Throwable e) {
		LOG.error(ToolString.stack2string(e));
		errorContainer.setVisibility(View.VISIBLE);
		progressContainer.setVisibility(View.GONE);
		errorText.setText("Error: " + (ToolString.isBlank(e.getMessage()) ? e.getClass().getName() : e.getMessage()));
	}

	@Background
	void doDataCallback() {
		setLoading("Almost done...");
		try {
			AppInstall appInstall = RPC.getRpcService().ensureInstall(Utils.createAndroidDevice());
			if (ToolString.isBlank(appInstall.getFlickrToken())) {
				setError(new Exception("app not in sync with server"));
			} else {
				Utils.setStringProperty(STR.accessToken, appInstall.getFlickrToken());
				Utils.setStringProperty(STR.accessTokenSecret, appInstall.getFlickrTokenSecret());
				Utils.setStringProperty(STR.userId, appInstall.getFlickrUserId());
				Utils.setStringProperty(STR.userName, appInstall.getFlickrUserName());
				Utils.setLongProperty(STR.userDateCreated, System.currentTimeMillis());
				FlickrApi.reset();
				FlickrApi.syncMedia();
				setResult(RESULT_CODE_AUTH);
				finish();
			}
		} catch (Throwable e) {
			setError(e);
		}
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == android.R.id.home) {
			finish();
		}
		return super.onOptionsItemSelected(item);
	}


}
