package com.rafali.flickruploader.widget;

import java.util.Locale;
import android.content.Context;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import com.rafali.flickruploader2.R;
import com.viewpagerindicator.TitlePageIndicator;

public abstract class TabView extends LinearLayout implements ViewPager.OnPageChangeListener {

	private final int nbTabs;

	public TabView(Context context, AttributeSet attrs) {
		this(context, attrs, 2, 0);
	}

	public TabView(Context context, AttributeSet attrs, int nbTabs, int defaultTabIndex) {
		super(context, attrs);
		setOrientation(VERTICAL);
		this.nbTabs = nbTabs;
		View.inflate(context, R.layout.tab_view, this);
		titleIndicator = (TitlePageIndicator) findViewById(R.id.titles);
		pager = (ViewPager) findViewById(R.id.pager);
		gridViewsArray = new View[nbTabs];
		feedPagerAdapter = new FeedPagerAdapter();
		pager.setAdapter(feedPagerAdapter);
		titleIndicator.setViewPager(pager);
		titleIndicator.setOnPageChangeListener(this);
		titleIndicator.setCurrentItem(defaultTabIndex);
	}

	protected TitlePageIndicator titleIndicator;

	protected ViewPager pager;

	protected final View[] gridViewsArray;

	private FeedPagerAdapter feedPagerAdapter;

	@Override
	public void onPageSelected(int position) {
	}

	@Override
	public void onPageScrolled(int arg0, float arg1, int arg2) {
	}

	@Override
	public void onPageScrollStateChanged(int arg0) {
	}

	protected abstract View createTabViewItem(int position);

	protected abstract int getTabViewItemTitle(int position);

	class FeedPagerAdapter extends PagerAdapter {

		{
			for (int i = 0; i < nbTabs; i++) {
				gridViewsArray[i] = createTabViewItem(i);
			}
		}

		@Override
		public Object instantiateItem(ViewGroup container, int position) {
			View view;
			if (gridViewsArray[position] != null) {
				view = gridViewsArray[position];
			} else {
				view = createTabViewItem(position);
				gridViewsArray[position] = view;
			}
			if (view.getParent() != container)
				container.addView(view);
			return view;
		}

		@Override
		public int getItemPosition(Object object) {
			for (int i = 0; i < gridViewsArray.length; i++) {
				if (gridViewsArray[i] == object)
					return i;
			}
			return POSITION_NONE;
		}

		@Override
		public void destroyItem(ViewGroup container, int position, Object object) {
			container.removeView((View) object);
		}

		@Override
		public int getCount() {
			return nbTabs;
		}

		@Override
		public boolean isViewFromObject(View view, Object o) {
			return view == o;
		}

		@Override
		public CharSequence getPageTitle(int position) {
			return getContext().getResources().getString(getTabViewItemTitle(position)).toUpperCase(Locale.US);
		}

	}

	public void destroyView(int position) {
		if (gridViewsArray[position] != null) {
			gridViewsArray[position] = null;
		}
		feedPagerAdapter.notifyDataSetChanged();
	}

	public void setCurrentItem(int position) {
		titleIndicator.setCurrentItem(position);
	}

	public View getCurrentView() {
		return pager != null ? gridViewsArray[pager.getCurrentItem()] : null;
	}

	public int getCurrentItem() {
		return pager != null ? pager.getCurrentItem() : 0;
	}

	public View getTabView(int position) {
		return pager != null ? gridViewsArray[position] : null;
	}

	public int getTabCount() {
		return nbTabs;
	}

}