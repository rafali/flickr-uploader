package com.rafali.flickruploader;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.googlecode.androidannotations.annotations.AfterViews;
import com.googlecode.androidannotations.annotations.EViewGroup;
import com.googlecode.androidannotations.annotations.ViewById;

@EViewGroup(R.layout.drawer_handle_view)
public class DrawerHandleView extends RelativeLayout {

	public DrawerHandleView(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	@ViewById(R.id.image)
	CustomImageView image;

	@ViewById(R.id.title)
	TextView title;

	@ViewById(R.id.sub_title)
	TextView subTitle;

	@ViewById(R.id.message)
	TextView message;

	@ViewById(R.id.progressContainer)
	View progressContainer;

	@AfterViews
	void afterViews() {
		if ("".isEmpty()) {
			progressContainer.setVisibility(View.GONE);
			message.setVisibility(View.VISIBLE);
			message.setText("Upload queue is empty");
		} else {
			progressContainer.setVisibility(View.VISIBLE);
			message.setVisibility(View.GONE);
			title.setText("Upload queue is empty");
			subTitle.setText("");
		}
	}
}
