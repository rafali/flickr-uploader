package com.rafali.flickruploader;

import java.net.URL;

import org.slf4j.LoggerFactory;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
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
	static final org.slf4j.Logger LOG = LoggerFactory.getLogger(WebAuth.class);

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
		webView.getSettings().setSaveFormData(false);
		webView.getSettings().setAppCacheEnabled(false);
		webView.getSettings().setSupportMultipleWindows(false);
		webView.getSettings().setLayoutAlgorithm(LayoutAlgorithm.SINGLE_COLUMN);
		webView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
		String userAgentString = webView.getSettings().getUserAgentString();
		LOG.debug("userAgentString: " + userAgentString);
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

			LOG.debug("oauthUrl : " + oauthUrl);
			// redirect user to the genreated URL.
			// redirect(oauthUrl);
			loadUrl(oauthUrl.toString());
		} catch (Throwable e) {
			LOG.error(Utils.stack2string(e));
			onNetworkError();
		}
	}

	@UiThread
	void onNetworkError() {
		new AlertDialog.Builder(WebAuth.this).setTitle("Error connecting to Flickr")
				.setMessage("An error occured while connecting to Flickr. Please make sure your internet access works and/or retry later.")
				.setPositiveButton("OK", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						finish();
					}
				}).setNegativeButton(null, null).setCancelable(false).show();
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
			LOG.debug("Visiting url: " + url);
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
				LOG.debug("oauth_token : " + oauth_token);
				LOG.debug("oauth_verifier : " + oauth_verifier);
				LOG.debug("oauthToken : " + oauthToken.getOauthToken() + ", " + oauthToken.getOauthTokenSecret());
				OAuth accessToken = oAuthInterface.getAccessToken(oauthToken.getOauthToken(), oauthToken.getOauthTokenSecret(), oauth_verifier);
				LOG.debug("accessToken : " + accessToken);
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
		LOG.warn(e.getMessage(), e);
		toast("An error occured, please retry : " + e.getMessage());
	}

	@UiThread
	void toast(String message) {
		LOG.debug(message);
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
