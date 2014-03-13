package com.rafali.flickruploader;

import java.net.URL;
import org.slf4j.LoggerFactory;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;
import android.webkit.WebSettings.LayoutAlgorithm;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;
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
import com.rafali.common.STR;
import com.rafali.common.ToolString;
import com.rafali.flickruploader2.R;

@EActivity(R.layout.webauth)
public class WebAuth extends Activity {

	public static final int RESULT_CODE_AUTH = 2227;
	static final org.slf4j.Logger LOG = LoggerFactory.getLogger(WebAuth.class);

	@ViewById(R.id.web_view)
	WebView webView;

	@ViewById(R.id.progress_container)
	View progressContainer;

	@ViewById(R.id.progress_text)
	TextView progressText;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		requestWindowFeature(Window.FEATURE_PROGRESS);
		getActionBar().setDisplayHomeAsUpEnabled(true);
		super.onCreate(savedInstanceState);
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
			LOG.error(ToolString.stack2string(e));
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
		super.onStop();
		EasyTracker.getInstance().activityStop(this);
	}

	@UiThread
	void loadUrl(String url) {
		LOG.debug("requesting loading url: " + url);
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

		@Override
		public void onPageStarted(WebView view, String url, Bitmap favicon) {
			try {
				String host = new URL(url).getHost();
				setLoading(true, "Loading\n" + host);
			} catch (Throwable e) {
				LOG.error(ToolString.stack2string(e));
			}
			super.onPageStarted(view, url, favicon);
		}

		@Override
		public void onPageFinished(WebView view, String url) {
			try {
				setLoading(false);
				String host = new URL(url).getHost();
				if (host.contains("yahoo.com") && url.contains("login")) {
					loginDisplayed = true;
				}
				if (url.contains("/oauth/authorize/")) {
					loadUrl("javascript:try{var ok_button = document.getElementById('dismiss4eva');var e = document.createEvent('MouseEvents'); e.initEvent( 'click', true, true ); ok_button.dispatchEvent(e);} catch(err) {}");
				}
			} catch (Throwable e) {
				LOG.error(ToolString.stack2string(e));
			}
			super.onPageFinished(view, url);
		}
	}

	boolean loginDisplayed = false;

	boolean dataCallbackDone = false;

	private OAuthToken oauthToken;

	void setLoading(boolean loading) {
		setLoading(loading, null);
	}

	@UiThread
	void setLoading(boolean loading, String message) {
		if (loading) {
			progressContainer.setVisibility(View.VISIBLE);
			progressText.setText(message);
		} else {
			progressContainer.setVisibility(View.GONE);
		}
	}

	@Background
	void doDataCallback(String url) {
		setLoading(true, "Almost done...");
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
				FlickrApi.reset();
				FlickrApi.syncUploadedPhotosMap(true);
				setResult(RESULT_CODE_AUTH);
				try {
					RPC.getRpcService().saveFlickrData(Utils.createAndroidDevice(), Utils.getStringProperty(STR.userId), Utils.getStringProperty(STR.userName),
							Utils.getStringProperty(STR.accessToken), Utils.getStringProperty(STR.accessTokenSecret));
				} catch (Throwable e) {
					LOG.error(ToolString.stack2string(e));
				}
			} catch (Throwable e) {
				onFail(e);
			}
		}
		setLoading(false);
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
			if (!userIssueAsked && loginDisplayed && Utils.getStringProperty(STR.userId) == null) {
				askUserIssue();
			} else {
				finish();
			}
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	public void onBackPressed() {
		if (!userIssueAsked && loginDisplayed && Utils.getStringProperty(STR.userId) == null) {
			askUserIssue();
		} else {
			super.onBackPressed();
		}
	}

	boolean userIssueAsked = false;

	@UiThread
	void askUserIssue() {
		userIssueAsked = true;
		LOG.debug("diplaying login issue dialog");
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle("Login Issues").setMessage(
				"If you had some issue to login into Flickr, you may find some information in the FAQ. If you do not see a solution in the FAQ, feel free to contact me at flickruploader@rafali.com");
		builder.setNegativeButton("Later", new OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				LOG.debug("cancelling login issue dialog");
				finish();
			}
		});
		builder.setPositiveButton("Open FAQ", new OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				LOG.debug("opening the FAQ");
				String url = "https://github.com/rafali/flickr-uploader/wiki/FAQ";
				Intent i = new Intent(Intent.ACTION_VIEW);
				i.setData(Uri.parse(url));
				startActivity(i);
			}
		});
		builder.create().show();
	}
}
