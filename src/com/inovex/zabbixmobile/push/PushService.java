package com.inovex.zabbixmobile.push;

import java.util.HashMap;
import java.util.concurrent.ArrayBlockingQueue;

import org.json.JSONException;
import org.json.JSONObject;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.IBinder;
import android.os.SystemClock;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.inovex.zabbixmobile.R;
import com.inovex.zabbixmobile.activities.ProblemsActivity;
import com.inovex.zabbixmobile.model.ZaxPreferences;

/**
 * Push service maintaining the connection to Pubnub and showing notifications
 * when Pubnub sends data.
 * 
 */
public class PushService extends Service {
	// Callback Interface when a channel is connected
	class ConnectCallback implements Callback {
		@Override
		public boolean execute(Object message) {
			Log.i("PushService", message.toString());
			return false;
		}
	}

	// Callback Interface when a channel is disconnected
	class DisconnectCallback implements Callback {
		@Override
		public boolean execute(Object message) {
			Log.i("PushService", message.toString());
			return false;
		}
	}

	// Callback Interface when error occurs
	class ErrorCallback implements Callback {
		@Override
		public boolean execute(Object message) {
			Log.i("PushService", message.toString());
			return false;
		}
	}

	class PushListener extends AsyncTask<String, Void, Boolean> {
		@Override
		protected Boolean doInBackground(String... params) {
			try {
				HashMap<String, Object> args = new HashMap<String, Object>(2);
				args.put("channel", params[0]);
				args.put("callback", mPushReceiver);
				args.put("connect_cb", new ConnectCallback()); // callback to
																// get connect
																// event
				args.put("disconnect_cb", new DisconnectCallback()); // callback
																		// to
																		// get
																		// disconnect
																		// event
				args.put("reconnect_cb", new ReconnectCallback()); // callback
																	// to get
																	// reconnect
																	// event
				args.put("error_cb", new ErrorCallback()); // callback to get
															// error event
				pubnub.subscribe(args);
				Log.i("PushService", "subscribe");
			} catch (Exception e) {
				e.printStackTrace();
			}

			return Boolean.TRUE; // Return your real result here
		}
	}

	int numNotifications = 0;
	protected static final int NUM_STACKED_NOTIFICATIONS = 5;
	ArrayBlockingQueue<CharSequence> previousMessages = new ArrayBlockingQueue<CharSequence>(
			NUM_STACKED_NOTIFICATIONS);

	class PushReceiver implements Callback {

		@Override
		public boolean execute(Object input) {
			Log.i("PushService", "execute");
			try {
				if (input instanceof JSONObject) {
					JSONObject jsonObj = (JSONObject) input;
					String status = null, message = null;
					Long triggerid = null;

					try {
						status = jsonObj.getString("status");
					} catch (JSONException e) {
						e.printStackTrace();
					}
					try {
						message = jsonObj.getString("message");
					} catch (JSONException e) {
						e.printStackTrace();
					}
					try {
						triggerid = jsonObj.getLong("triggerid");
					} catch (JSONException e) {
						e.printStackTrace();
					}

					int notIcon;
					if (status != null && status.equals("OK")) {
						notIcon = R.drawable.ok;
					} else if (status != null && status.equals("PROBLEM")) {
						notIcon = R.drawable.problem;
					} else {
						notIcon = R.drawable.icon;
					}
					String notMessage;
					if (message != null && message.length() > 0) {
						notMessage = message;
					} else {
						notMessage = jsonObj.toString();
					}

					NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(
							PushService.this);
					notificationBuilder.setLargeIcon(BitmapFactory
							.decodeResource(getResources(), notIcon));
					notificationBuilder.setSmallIcon(R.drawable.icon);
					notificationBuilder.setTicker(notMessage);
					notificationBuilder.setWhen(System.currentTimeMillis());

					// we do not start MainActivity directly, but send a
					// broadcast which will be received by a
					// NotificationBroadcastReceiver which resets the
					// notification status and starts MainActivity.
					Intent notificationIntent = new Intent();
					notificationIntent.setAction(ACTION_ZABBIX_NOTIFICATION);
					PendingIntent pendingIntent = PendingIntent.getBroadcast(
							PushService.this, uniqueRequestCode(),
							notificationIntent,
							PendingIntent.FLAG_CANCEL_CURRENT);
					notificationBuilder.setContentTitle(getResources()
							.getString(R.string.notification_title));
					notificationBuilder.setContentText(message);
					notificationBuilder.setContentIntent(pendingIntent);
					notificationBuilder.setNumber(++numNotifications);

					notificationBuilder.setAutoCancel(true);
					notificationBuilder.setOnlyAlertOnce(false);

					if (previousMessages.size() == NUM_STACKED_NOTIFICATIONS)
						previousMessages.poll();
					previousMessages.offer(message);
					// if there are several notifications, we stack them in the
					// extended view
					if (numNotifications > 1) {
						NotificationCompat.InboxStyle inboxStyle = new NotificationCompat.InboxStyle();
						// Sets a title for the Inbox style big view
						inboxStyle.setBigContentTitle(getResources().getString(
								R.string.notification_title));
						// Moves events into the big view
						for (CharSequence prevMessage : previousMessages) {
							inboxStyle.addLine(prevMessage);
						}
						if (numNotifications > NUM_STACKED_NOTIFICATIONS) {
							inboxStyle
									.setSummaryText((numNotifications - NUM_STACKED_NOTIFICATIONS)
											+ " more");
						}
						// Moves the big view style object into the notification
						// object.
						notificationBuilder.setStyle(inboxStyle);
					}

					ZaxPreferences preferences = ZaxPreferences
							.getInstance(PushService.this);
					String strRingtonePreference = preferences
							.getPushRingtone();
					if (strRingtonePreference != null) {
						notificationBuilder.setSound(Uri
								.parse(strRingtonePreference));
					}

					Notification notification = notificationBuilder.build();

					NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
					// We use the same ID because we want to stack the
					// notifications and we don't really care about the trigger
					// ID anyway (clicking the notification just starts the main
					// activity).
					mNotificationManager.notify(0, notification);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}

			return true;
		}
	}

	// Callback Interface when a channel is reconnected
	class ReconnectCallback implements Callback {
		@Override
		public boolean execute(Object message) {
			Log.i("PushService", message.toString());
			return false;
		}
	}

	public static final String ACTION_ZABBIX_NOTIFICATION = "com.inovex.zabbixmobile.push.PushService.ACTION_ZABBIX_NOTIFICATION";

	/**
	 * This broadcast receiver reacts on a click on a notification by performing
	 * the following tasks:
	 * 
	 * 1. Reset the notification numbers and previous messages.
	 * 
	 * 2. Start the main activity.
	 * 
	 */
	public class NotificationBroadcastReceiver extends BroadcastReceiver {

		public NotificationBroadcastReceiver() {
			super();
		}

		@Override
		public void onReceive(Context context, Intent intent) {
			numNotifications = 0;
			previousMessages.clear();
			Intent notificationIntent = new Intent(context,
					ProblemsActivity.class);
			notificationIntent.putExtra(
					ProblemsActivity.ARG_START_FROM_NOTIFICATION, true);
			notificationIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK
					| Intent.FLAG_ACTIVITY_NEW_TASK);
			context.startActivity(notificationIntent);
		}

	}

	private static int lastRequestCode = 0;
	String PUSHCHANNEL = "zabbixmobile";
	Pubnub pubnub;
	PushReceiver mPushReceiver = new PushReceiver();
	PushListener mPushListener = new PushListener();
	private BroadcastReceiver mNotificationBroadcastReceiver;

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	@Override
	public void onCreate() {
		super.onCreate();

		ZaxPreferences preferences = ZaxPreferences.getInstance(this);
		String subscribe_key = preferences.getPushSubscribeKey();

		pubnub = new Pubnub("", // PUBLISH_KEY
				subscribe_key, // SUBSCRIBE_KEY
				"", // SECRET_KEY
				"", // CIPHER_KEY
				false // SSL_ON?
		);
		Log.i("PushService", "create");
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		super.onStartCommand(intent, flags, startId);
		Log.d("PushService", "starting");
		if (mPushListener.getStatus() != AsyncTask.Status.RUNNING) {
			mPushListener.execute(PUSHCHANNEL);
			Log.i("PushService", "start ");
		}
		// Register the notification broadcast receiver.
		IntentFilter filter = new IntentFilter();
		filter.addAction(ACTION_ZABBIX_NOTIFICATION);
		mNotificationBroadcastReceiver = new NotificationBroadcastReceiver();
		registerReceiver(mNotificationBroadcastReceiver, filter);
		return START_NOT_STICKY;
	}

	@Override
	public void onDestroy() {
		Log.d("PushService", "onDestroy");
		mPushListener.cancel(true);
		HashMap<String, Object> args = new HashMap<String, Object>(1);
		args.put("channel", PUSHCHANNEL);
		pubnub.unsubscribe(args);
		unregisterReceiver(mNotificationBroadcastReceiver);
	}

	private int uniqueRequestCode() {
		return lastRequestCode++;
	}

	private static AlarmManager am;

	/**
	 * This starts or stops the push service depending on the user's settings.
	 * 
	 * @param context
	 */
	public static void startOrStopPushService(Context context) {
		// start the push receiver, if it is enabled
		ZaxPreferences preferences = ZaxPreferences.getInstance(context);
		boolean push = preferences.isPushEnabled();
		if (am == null)
			am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
		PendingIntent pendingIntent = PendingIntent.getService(context, 0,
				new Intent(context, PushService.class), 0);
		if (push) {
			setRepeatingAlarm(pendingIntent);
		} else {
			stopRepeatingAlarm(pendingIntent);
		}

	}

	private static void setRepeatingAlarm(PendingIntent pendingIntent) {

		Log.d("PushServiceAlarm", "setRepeatingAlarm");

		am.cancel(pendingIntent);
		// wake up every 10 minutes to ensure service stays alive
		int alarmFrequency = 10 * 60 * 1000;
		// start service after one minute to avoid wasting precious CPU time
		// after device boot
		am.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,
				SystemClock.elapsedRealtime() + 1 * 60 * 1000, alarmFrequency,
				pendingIntent);
	}

	private static void stopRepeatingAlarm(PendingIntent pendingIntent) {

		Log.d("PushServiceAlarm", "stopRepeatingAlarm");

		am.cancel(pendingIntent);
	}
}
