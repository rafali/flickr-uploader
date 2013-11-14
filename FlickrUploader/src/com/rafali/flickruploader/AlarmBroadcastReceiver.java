package com.rafali.flickruploader;

import org.slf4j.LoggerFactory;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;

public class AlarmBroadcastReceiver extends BroadcastReceiver {
	private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(AlarmBroadcastReceiver.class);

	@Override
	public void onReceive(Context context, Intent intent) {
		PowerManager.WakeLock wl = null;
		try {
			PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
			wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "");
			wl.acquire();
			if (Utils.canAutoUploadBool()) {
				UploadService.checkNewFiles();
			} else {
				initAlarm();
			}
		} catch (Throwable e) {
			LOG.error(Utils.stack2string(e));
		} finally {
			if (wl != null) {
				wl.release();
			}
		}
	}

	public static void initAlarm() {
		try {
			Context context = FlickrUploader.getAppContext();
			if (Utils.canAutoUploadBool()) {
				if (!Utils.getBooleanProperty(STR.alarmSet, false)) {
					AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
					Intent i = new Intent(context, AlarmBroadcastReceiver.class);
					PendingIntent pi = PendingIntent.getBroadcast(context, 0, i, 0);
					am.setInexactRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis(), AlarmManager.INTERVAL_HOUR, pi);
					Utils.setBooleanProperty(STR.alarmSet, true);
				}
			} else {
				Intent intent = new Intent(context, AlarmBroadcastReceiver.class);
				PendingIntent sender = PendingIntent.getBroadcast(context, 0, intent, 0);
				AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
				alarmManager.cancel(sender);
				Utils.setBooleanProperty(STR.alarmSet, false);
			}
		} catch (Throwable e) {
			LOG.error(Utils.stack2string(e));
		}
	}
}
