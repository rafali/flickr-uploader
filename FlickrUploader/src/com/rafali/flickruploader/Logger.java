package com.rafali.flickruploader;

import android.util.Log;

import com.google.common.base.Joiner;

public class Logger {
	/**
	 * Use this boolean to enable full LOG (VERBOSE)
	 */
	private static final boolean DEBUG = FlickrUploader.isDebug();

	private static final String MAIN_TAG = "FlickrUploader";

	/**
	 * @param level
	 *            the level to check
	 * @return true if the level is loggable
	 */
	private static boolean isLoggable(int level) {
		return DEBUG || Log.isLoggable(MAIN_TAG, level);
	}

	/**
	 * <b>DEBUG ONLY</b>: NOT VISIBLE BY THE USER
	 * 
	 * @see Log#v(String, String)
	 * @param tag
	 *            the class tag
	 * @param msg
	 *            the message
	 */
	public static void v(String tag, String msg) {
		if (isLoggable(Log.VERBOSE)) {
			Log.v(MAIN_TAG, format("%s>%s", tag, msg));
		}
	}

	/**
	 * <b>DEBUG ONLY</b>: NOT VISIBLE BY THE USER
	 * 
	 * @see Log#v(String, String)
	 * @param tag
	 *            the class tag
	 * @param msg
	 *            the message
	 * @param args
	 *            the message arguments
	 */
	public static void v(String tag, String msg, Object... args) {
		if (isLoggable(Log.VERBOSE)) {
			Log.v(MAIN_TAG, format("%s>%s", tag, format(msg, args)));
		}
	}

	private static String format(String msg, Object... args) {
		if (msg != null) {
			return String.format(msg, args);
		} else {
			return "";
		}
	}

	/**
	 * <b>DEBUG ONLY</b>: NOT VISIBLE BY THE USER should only be used to temporary trace stuff
	 * 
	 * @param msg
	 *            the message
	 */
	public static void trace(Object... objects) {
		if (isLoggable(Log.DEBUG)) {
			StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
			StackTraceElement stackTraceElement = stackTrace[3];
			StackTraceElement stackTraceElement2 = stackTrace[4];
			String classname = stackTraceElement.getClassName().substring(stackTraceElement.getClassName().lastIndexOf('.') + 1);
			String classname2 = stackTraceElement2.getClassName().substring(stackTraceElement2.getClassName().lastIndexOf('.') + 1);
			Log.i(MAIN_TAG,
					format("%s.%s() > %s.%s()", classname2, stackTraceElement2.getMethodName(), classname, stackTraceElement.getMethodName()) + " " + Joiner.on(",").useForNull("null").join(objects));
		}
	}

	/**
	 * <b>DEBUG ONLY</b>: NOT VISIBLE BY THE USER
	 * 
	 * @see Log#d(String, String)
	 * @param tag
	 *            the class tag
	 * @param msg
	 *            the message
	 */
	public static void d(String tag, String msg) {
		if (isLoggable(Log.DEBUG)) {
			Log.d(MAIN_TAG, format("%s>%s", tag, msg));
		}
	}

	/**
	 * <b>DEBUG ONLY</b>: NOT VISIBLE BY THE USER
	 * 
	 * @see Log#d(String, String)
	 * @param tag
	 *            the class tag
	 * @param msg
	 *            the message
	 * @param args
	 *            the message arguments
	 */
	public static void d(String tag, String msg, Object... args) {
		if (isLoggable(Log.DEBUG)) {
			Log.d(MAIN_TAG, format("%s>%s", tag, format(msg, args)));
		}
	}

	/**
	 * <b>DEBUG ONLY</b>: NOT VISIBLE BY THE USER
	 * 
	 * @see Log#d(String, String, Throwable)
	 * @param tag
	 *            the class tag
	 * @param t
	 *            the error
	 * @param msg
	 *            the message
	 * @param args
	 *            the message arguments
	 */
	public static void d(String tag, Throwable t, String msg, Object... args) {
		if (isLoggable(Log.DEBUG)) {
			Log.d(MAIN_TAG, format("%s>%s", tag, format(msg, args)), t);
		}
	}

	/**
	 * <b>VISIBLE BY THE USER</b>
	 * 
	 * @see Log#i(String, String)
	 * @param tag
	 *            the class log
	 * @param msg
	 *            the message
	 */
	public static void i(String tag, String msg) {
		if (isLoggable(Log.INFO)) {
			Log.i(MAIN_TAG, format("%s>%s", tag, msg));
		}
	}

	/**
	 * <b>VISIBLE BY THE USER</b>
	 * 
	 * @see Log#i(String, String)
	 * @param tag
	 *            the class log
	 * @param msg
	 *            the message
	 * @param args
	 *            the message arguments
	 */
	public static void i(String tag, String msg, Object... args) {
		if (isLoggable(Log.INFO)) {
			Log.i(MAIN_TAG, format("%s>%s", tag, format(msg, args)));
		}
	}

	/**
	 * <b>VISIBLE BY THE USER</b>
	 * 
	 * @see Log#w(String, String)
	 * @param tag
	 *            the class tag
	 * @param msg
	 *            the message
	 * @param args
	 *            the message arguments
	 */
	public static void w(String tag, String msg, Object... args) {
		if (isLoggable(Log.WARN)) {
			Log.w(MAIN_TAG, format("%s>%s", tag, args.length == 0 ? msg : format(msg, args)));
		}
	}

	/**
	 * <b>VISIBLE BY THE USER</b>
	 * 
	 * @see Log#w(String, String, Throwable)
	 * @param tag
	 *            the class tag
	 * @param t
	 *            the error
	 * @param msg
	 *            the message
	 * @param args
	 *            the message arguments
	 */
	public static void w(String tag, Throwable t, String msg, Object... args) {
		if (isLoggable(Log.WARN)) {
			Log.w(MAIN_TAG, format("%s>%s", tag, format(msg, args)), t);
		}
	}

	/**
	 * <b>VISIBLE BY THE USER</b>
	 * 
	 * @see Log#e(String, String, Throwable)
	 * @param tag
	 *            the class tag
	 * @param t
	 *            the error
	 * @param msg
	 *            the message
	 */
	public static void e(String tag, Throwable t, String msg) {
		if (isLoggable(Log.ERROR)) {
			Log.e(MAIN_TAG, format("%s>%s", tag, msg), t);
		}
	}

	public static void e(String tag, Throwable e) {
		e(tag, e, e.getMessage());
	}

}
