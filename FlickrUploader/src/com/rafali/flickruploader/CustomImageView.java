package com.rafali.flickruploader;

import org.slf4j.LoggerFactory;

import uk.co.senab.bitmapcache.CacheableImageView;
import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;

public class CustomImageView extends CacheableImageView {

	static final org.slf4j.Logger LOG = LoggerFactory.getLogger(CustomImageView.class);

	public CustomImageView(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	@Override
	protected void onDraw(Canvas canvas) {
		try {
			super.onDraw(canvas);
		} catch (Throwable e) {
			LOG.warn(e.getMessage(), e);
		}
	}
}
