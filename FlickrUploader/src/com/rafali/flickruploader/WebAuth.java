package com.rafali.flickruploader;

import java.net.URL;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;
import android.webkit.WebSettings.LayoutAlgorithm;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import com.google.analytics.tracking.android.EasyTracker;
import com.googlecode.androidannotations.annotations.AfterViews;
import com.googlecode.androidannotations.annotations.Background;
import com.googlecode.androidannotations.annotations.EActivity;
import com.googlecode.androidannotations.annotations.UiThread;
import com.googlecode.androidannotations.annotations.ViewById;
import com.googlecode.flickrjandroid.auth.Permission;
import com.googlecode.flickrjandroid.oauth.OAuth;
import com.googlecode.flickrjandroid.oauth.OAuthInterface;
import com.googlecode.flickrjandroid.oauth.OAuthToken;

@EActivity(R.layout.webauth)
public class WebAuth extends Activity {

	public static final int RESULT_CODE_AUTH = 2227;
	private static final String TAG = WebAuth.class.getSimpleName();

	@ViewById(R.id.web_view)
	WebView webView;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		requestWindowFeature(Window.FEATURE_PROGRESS);
		getActionBar().setDisplayHomeAsUpEnabled(true);
		super.onCreate(savedInstanceState);
		Mixpanel.track("Web authentication");
	}

	@SuppressLint({ "SetJavaScriptEnabled", "NewApi" })
	@AfterViews
	protected void onAfterViews() {
		webView.setWebViewClient(new MyWebViewClient());
		webView.getSettings().setJavaScriptEnabled(true);
		webView.getSettings().setDomStorageEnabled(true);

		webView.getSettings().setBuiltInZoomControls(false);
		webView.getSettings().setSupportZoom(false);
		webView.getSettings().setSavePassword(false);
		webView.getSettings().setSaveFormData(false);
		webView.getSettings().setAppCacheEnabled(false);
		webView.getSettings().setSupportMultipleWindows(false);
		webView.getSettings().setLayoutAlgorithm(LayoutAlgorithm.SINGLE_COLUMN);
		String userAgentString = webView.getSettings().getUserAgentString();
		Log.d(TAG, "userAgentString: " + userAgentString);
		webView.clearCache(true);
		webView.destroyDrawingCache();

		setProgressBarIndeterminateVisibility(true);
		setProgressBarVisibility(true);
		CookieSyncManager cookieSyncManager = CookieSyncManager.createInstance(this);
		CookieManager cookieManager = CookieManager.getInstance();
		cookieManager.setAcceptCookie(true);
		cookieManager.removeAllCookie();
		cookieSyncManager.sync();

		webView.setVisibility(View.VISIBLE);

		loadAuthorizationUrl();
	}

	@Background
	void loadAuthorizationUrl() {
		try {
			String callBackUrl = "flickrinstantupload";
			oauthToken = FlickrApi.get().getOAuthInterface().getRequestToken(callBackUrl);

			// build the Authentication URL with the required permission
			URL oauthUrl = FlickrApi.get().getOAuthInterface().buildAuthenticationUrl(Permission.WRITE, oauthToken);

			// Uri uriUrl = Uri.parse(oauthUrl.toString());
			// Intent launchBrowser = new Intent(Intent.ACTION_VIEW, uriUrl);
			// startActivity(launchBrowser);

			Log.d(TAG, "oauthUrl : " + oauthUrl);
			// redirect user to the genreated URL.
			// redirect(oauthUrl);
			loadUrl(oauthUrl.toString());
		} catch (Throwable e) {
			e.printStackTrace();
		}
	}

	@Override
	protected void onStart() {
		super.onStart();
		EasyTracker.getInstance().activityStart(this);
	}

	@Override
	protected void onStop() {
		Mixpanel.flush();
		super.onStop();
		EasyTracker.getInstance().activityStop(this);
	}

	@UiThread
	void loadUrl(String url) {
		webView.loadUrl(url);
	}

	private class MyWebViewClient extends WebViewClient {
		public MyWebViewClient() {
		}

		@Override
		public boolean shouldOverrideUrlLoading(WebView view, String url) {
			Log.d(TAG, "Visiting url: " + url);
			if (url.contains("oauth_token") && url.contains("oauth_verifier")) {
				doDataCallback(url);
			} else {
				view.loadUrl(url);
			}
			return true;
		}
	}

	boolean dataCallbackDone = false;

	private OAuthToken oauthToken;

	@Background
	void doDataCallback(String url) {
		if (!dataCallbackDone) {
			try {
				dataCallbackDone = true;
				OAuthInterface oAuthInterface = FlickrApi.get().getOAuthInterface();
				String substring = url.substring(url.indexOf("?") + 1);
				String oauth_token = null;
				String oauth_verifier = null;
				for (String string : substring.split("&")) {
					String[] split = string.split("=");
					if ("oauth_token".equals(split[0]))
						oauth_token = split[1];
					else if ("oauth_verifier".equals(split[0]))
						oauth_verifier = split[1];
				}
				Log.d(TAG, "oauth_token : " + oauth_token);
				Log.d(TAG, "oauth_verifier : " + oauth_verifier);
				Log.d(TAG, "oauthToken : " + oauthToken.getOauthToken() + ", " + oauthToken.getOauthTokenSecret());
				OAuth accessToken = oAuthInterface.getAccessToken(oauthToken.getOauthToken(), oauthToken.getOauthTokenSecret(), oauth_verifier);
				Log.d(TAG, "accessToken : " + accessToken);
				Utils.setStringProperty(STR.accessToken, accessToken.getToken().getOauthToken());
				Utils.setStringProperty(STR.accessTokenSecret, accessToken.getToken().getOauthTokenSecret());
				Utils.setStringProperty(STR.userId, accessToken.getUser().getId());
				Utils.setStringProperty(STR.userName, accessToken.getUser().getUsername());
				Utils.setLongProperty(STR.userDateCreated, System.currentTimeMillis());
				Mixpanel.reset();
				Mixpanel.track("Sign in success");
				FlickrApi.reset();
				FlickrApi.syncUploadedPhotosMap(true);
				setResult(RESULT_CODE_AUTH);
			} catch (Throwable e) {
				onFail(e);
			}
		}
		finish();
	}

	@UiThread
	void onFail(Throwable e) {
		Log.w(TAG, e);
		toast("An error occured, please retry : " + e.getMessage());
	}

	@UiThread
	void toast(String message) {
		Log.v(TAG, message);
		Toast.makeText(WebAuth.this, message, Toast.LENGTH_SHORT).show();
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == android.R.id.home) {
			finish();
		}
		return super.onOptionsItemSelected(item);
	}

}
