package com.rafali.flickruploader;

import java.util.Date;

import org.json.JSONObject;
import org.slf4j.LoggerFactory;

import com.googlecode.androidannotations.api.BackgroundExecutor;
import com.mixpanel.android.mpmetrics.MixpanelAPI;
import com.mixpanel.android.mpmetrics.MixpanelAPI.People;

public class Mixpanel {

	static final org.slf4j.Logger LOG = LoggerFactory.getLogger(Mixpanel.class);

	private static MixpanelAPI mixpanel;

	private static void ensureMixpanel() {
		if (mixpanel == null) {
			mixpanel = MixpanelAPI.getInstance(FlickrUploader.getAppContext(), "ac478922090ee3d05c1d05f9b609ab5d");
			try {
				MixpanelAPI.People people = mixpanel.getPeople();
				people.identify(Utils.getEmail());
				people.set("$email", Utils.getEmail());
				String userId = Utils.getStringProperty(STR.userId);
				if (userId != null) {
					people.set("userId", userId);
					people.set("$name", Utils.getStringProperty(STR.userName));
					Date dateCreated = new Date(Utils.getLongProperty(STR.userDateCreated));
					people.set("$created", dateCreated);
					people.set(STR.hasRated, Utils.getBooleanProperty(STR.hasRated, false));
					JSONObject json = new JSONObject();
					json.put("$created", dateCreated);
					mixpanel.registerSuperProperties(json);
				}
			} catch (Throwable e) {
				LOG.error(e.getMessage(), e);
			}

		}
	}

	public static void reset() {
		mixpanel = null;
	}

	public static void increment(final String key, final long nb) {
		BackgroundExecutor.execute(new Runnable() {
			@Override
			public void run() {
				try {
					ensureMixpanel();
					People people = mixpanel.getPeople();
					people.identify(Utils.getEmail());
					people.increment(key, nb);
				} catch (Throwable e) {
					LOG.error(e.getMessage(), e);
				}
			}
		});
	}

	public static void flush() {
		if (mixpanel != null) {
			mixpanel.flush();
		}
	}

	public static void track(final String event, final Object... objects) {
		BackgroundExecutor.execute(new Runnable() {
			@Override
			public void run() {
				try {
					ensureMixpanel();
					JSONObject jsonObject = null;
					int length = objects.length;
					if (length > 0) {
						jsonObject = new JSONObject();
						for (int i = 0; i < length / 2; i++) {
							jsonObject.put((String) objects[2 * i], objects[2 * i + 1]);
						}
					}
					mixpanel.identify(Utils.getEmail());
					mixpanel.track(event, jsonObject);
				} catch (Throwable e) {
					LOG.error(e.getMessage(), e);
				}
			}
		});

	}

}
