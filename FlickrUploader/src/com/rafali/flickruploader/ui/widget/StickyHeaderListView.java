package com.rafali.flickruploader.ui.widget;

import org.slf4j.LoggerFactory;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ListAdapter;
import android.widget.ListView;

public class StickyHeaderListView extends ListView implements AbsListView.OnScrollListener {

	static final org.slf4j.Logger LOG = LoggerFactory.getLogger(StickyHeaderListView.class);

	protected OnScrollListener subclassOnScrollListener;

	protected View floatingHeader;
	protected float floatingHeaderOffset;
	protected int floatingHeaderPosition = -1;

	protected int widthMode;
	protected int heightMode;
	protected View touchTarget;
	protected MotionEvent touchDownEvent;
	protected float touchDownX = -1;
	protected float touchDownY = -1;
	protected int maxMoveDistanceForTouch;

	public static class Header {
		public String id;
		public String title;
		public int count = 1;
		public boolean collapsed = false;
		public boolean selected = false;

		public Header(String id, String title) {
			this.id = id;
			this.title = title;
		}

		@Override
		public String toString() {
			return id + ":" + count + ":" + title + ":collapsed=" + collapsed + ":selected=" + selected;
		}
	}

	public static interface HeaderAdapter extends ListAdapter {
		int getHeaderPosition(int firstVisibleItem);
	}

	public StickyHeaderListView(Context context) {
		super(context);
		setup();
	}

	public StickyHeaderListView(Context context, AttributeSet attrs) {
		super(context, attrs);
		setup();
	}

	public StickyHeaderListView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		setup();
	}

	@Override
	public void setAdapter(ListAdapter adapter) {
		super.setAdapter(adapter);
	}

	@Override
	public void setOnScrollListener(OnScrollListener onScrollListener) {
		this.subclassOnScrollListener = onScrollListener;
	}

	@Override
	public void onScrollStateChanged(AbsListView view, int scrollState) {
		// Bubble action up to parent if necessary
		if (subclassOnScrollListener != null) {
			subclassOnScrollListener.onScrollStateChanged(view, scrollState);
		}
	}

	@Override
	public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
		// Bubble action up to parent if necessary
		if (subclassOnScrollListener != null) {
			subclassOnScrollListener.onScroll(view, firstVisibleItem, visibleItemCount, totalItemCount);
		}

		if (getAdapter() != null) {
			int headerPosition = getHeaderAdapter().getHeaderPosition(firstVisibleItem);
			if (headerPosition != floatingHeaderPosition) {
				floatingHeaderPosition = headerPosition;
				floatingHeader = getAdapter().getView(headerPosition, null, this);
				updateDimensionsForHeader(floatingHeader);
			}
		}
	}

	HeaderAdapter getHeaderAdapter() {
		return (HeaderAdapter) getAdapter();
	}

	@Override
	public boolean dispatchTouchEvent(MotionEvent ev) {
		if (floatingHeader != null) {
			float touchX = ev.getX();
			float touchY = ev.getY();
			int touchAction = ev.getAction();

			if (touchAction == MotionEvent.ACTION_DOWN && touchTarget == null && isTouchInFloatingSectionHeader(touchX, touchY)) {
				touchTarget = floatingHeader;
				touchDownX = touchX;
				touchDownY = touchY;
				touchDownEvent = MotionEvent.obtain(ev);
			} else if (touchTarget != null) {
				if (isTouchInFloatingSectionHeader(touchX, touchY)) {
					touchTarget.dispatchTouchEvent(ev);
				}

				if (touchAction == MotionEvent.ACTION_UP) {
					if (touchTarget.equals(floatingHeader)) {
						clearTouch();
						clickFloatingSectionHeader(ev);
					} else {
						clearTouch();
					}
				} else if (touchAction == MotionEvent.ACTION_CANCEL) {
					clearTouch();
				} else if (touchAction == MotionEvent.ACTION_MOVE && (Math.abs(touchDownX - touchX) > maxMoveDistanceForTouch || Math.abs(touchDownY - touchY) > maxMoveDistanceForTouch)) {
					MotionEvent event = MotionEvent.obtain(ev);
					if (event != null) {
						event.setAction(MotionEvent.ACTION_CANCEL);
						touchTarget.dispatchTouchEvent(event);
						event.recycle();
					}

					super.dispatchTouchEvent(touchDownEvent);
					super.dispatchTouchEvent(ev);
					clearTouch();
				}

				return true;
			}
		}

		return super.dispatchTouchEvent(ev);
	}

	@Override
	protected void dispatchDraw(Canvas canvas) {
		super.dispatchDraw(canvas);
		if (floatingHeader != null) {
			floatingHeaderOffset = 0;
			for (int i = 0; i < getChildCount(); i++) {
				View headerView = getChildAt(i);
				if (headerView.getTag() == Header.class && floatingHeader.getTag(Header.class.hashCode()) != headerView.getTag(Header.class.hashCode())) {
					if (headerView.getTop() <= floatingHeader.getMeasuredHeight()) {
						floatingHeaderOffset = headerView.getTop() - floatingHeader.getMeasuredHeight();
					}
					break;
				}
			}
			int count = canvas.save();
			canvas.translate(0, floatingHeaderOffset);
			canvas.clipRect(0, 0, getWidth(), floatingHeader.getMeasuredHeight());
			floatingHeader.draw(canvas);
			canvas.restoreToCount(count);
		}
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		super.onMeasure(widthMeasureSpec, heightMeasureSpec);

		widthMode = MeasureSpec.getMode(widthMeasureSpec);
		heightMode = MeasureSpec.getMode(heightMeasureSpec);
	}

	protected void searchForClickableChildren(ViewGroup parent, float touchX, float touchY) {
		for (int i = 0; i < parent.getChildCount(); i++) {
			View child = parent.getChildAt(i);
			if (child != null) {
				if (child instanceof ViewGroup) {
					if (((ViewGroup) child).getChildCount() > 0) {
						searchForClickableChildren((ViewGroup) child, touchX, touchY);
					}
				}

				if (child.isClickable()) {
					if (isTouchInView(child, touchX, touchY)) {
						child.performClick();
					}
				}
			}
		}
	}

	protected void clickFloatingSectionHeader(final MotionEvent ev) {
		LOG.info("CLICK");
		post(new Runnable() {
			@Override
			public void run() {
				searchForClickableChildren((ViewGroup) floatingHeader, ev.getRawX(), ev.getRawY());
			}
		});
	}

	protected boolean isTouchInView(View view, float touchX, float touchY) {
		// TODO check touchY
		return view.getLeft() <= touchX && view.getRight() >= touchX;
	}

	protected boolean isTouchInFloatingSectionHeader(float touchX, float touchY) {
		if (floatingHeader != null) {
			Rect hitRect = new Rect();
			floatingHeader.getHitRect(hitRect);

			hitRect.top += floatingHeaderOffset;
			hitRect.bottom += floatingHeaderOffset + getPaddingBottom();
			hitRect.left += getPaddingLeft();
			hitRect.right += getPaddingRight();

			return hitRect.contains((int) touchX, (int) touchY);
		}

		return false;
	}

	public View getFloatingSectionHeader() {
		return floatingHeader;
	}

	protected void clearTouch() {
		touchTarget = null;
		touchDownX = -1;
		touchDownY = -1;

		if (touchDownEvent != null) {
			touchDownEvent.recycle();
			touchDownEvent = null;
		}
	}

	protected void updateDimensionsForHeader(View headerView) {
		if (headerView != null && headerView.isLayoutRequested()) {
			int widthMeasure = MeasureSpec.makeMeasureSpec(getMeasuredWidth(), widthMode);
			int heightMeasure;

			ViewGroup.LayoutParams layoutParams = headerView.getLayoutParams();
			if (layoutParams != null && layoutParams.height > 0) {
				heightMeasure = MeasureSpec.makeMeasureSpec(layoutParams.height, MeasureSpec.EXACTLY);
			} else {
				heightMeasure = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
			}

			headerView.measure(widthMeasure, heightMeasure);
			headerView.layout(0, 0, headerView.getMeasuredWidth(), headerView.getMeasuredHeight());
		}
	}

	protected void setup() {
		super.setOnScrollListener(this);
		maxMoveDistanceForTouch = ViewConfiguration.get(getContext()).getScaledTouchSlop();
	}
}
