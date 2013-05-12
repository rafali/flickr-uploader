package com.rafali.uploader;

public class Log {

	public static void d(String tag, String string) {
		System.out.println(tag + " > " + string);
	}

	public static void e(String tag, String message, Throwable e) {
		e.printStackTrace();
	}

	public static void w(String tag, String string) {
		System.out.println(tag + " > " + string);
	}

	public static void i(String tag, String string) {
		System.out.println(tag + " > " + string);
	}

}
