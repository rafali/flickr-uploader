package com.rafali.flickruploader;

import uk.co.senab.bitmapcache.CacheableImageView;
import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.util.Log;

public class CustomImageView extends CacheableImageView {

	static final String TAG = CustomImageView.class.getSimpleName();

	public CustomImageView(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	@Override
	protected void onDraw(Canvas canvas) {
		try {
			super.onDraw(canvas);
		} catch (Throwable e) {
			Log.w(TAG, e.getMessage(), e);
		}
	}
}
