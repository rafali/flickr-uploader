package com.rafali.flickruploader.service;

import com.rafali.flickruploader.model.Media;

public class UploadService {

	public static class UploadException extends RuntimeException {

		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;

		public UploadException(String string, boolean b) {
			// TODO Auto-generated constructor stub
		}

		public UploadException(String message, Throwable throwable) {
			// TODO Auto-generated constructor stub
		}
		
	}

	public static void onUploadProgress(Media media) {
		// TODO Auto-generated method stub
		
	}
	
}
