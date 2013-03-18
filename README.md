Flickr Uploader
===============

The goal of this project is to help Flickr users automatically backup the photos from their Android phone.
It works like [Google+ Instant Upload](http://support.google.com/plus/answer/2910392?hl=en) or [Dropbox Camera Upload](https://blog.dropbox.com/2012/02/your-photos-simplified-part-1/).

The main benefit of this app over Google+ Instant Upload is the fact that you can upload an **unlimited** number of photos in **high resolution** with a relatively cheap Flick Pro account.

You can download it at : https://play.google.com/store/apps/details?id=com.rafali.flickruploader


It uses a few open source libraries:
- [flickrj-android](https://code.google.com/p/flickrj-android/) : a modified version of the old java flickr lib optimized for Android and Google App Engine
- [Android-BitmapCache](https://github.com/chrisbanes/Android-BitmapCache) : takes care of bitmaps caching/recycling for you
- [Android-ViewPagerIndicator](https://github.com/JakeWharton/Android-ViewPagerIndicator) : displays a nice tab UI above a ViewPager
- [ACRA](https://github.com/ACRA/acra) : sends crash report to a configurable url
- [android-donations-lib](https://github.com/dschuermann/android-donations-lib) : allows users to make donations using Google Play, Paypal or Flattr
- [AndroidAnnotation](https://github.com/excilys/androidannotations) : simplifies Android development with annotations to define code scope (UI thread, background thread…)
- [google-collections](https://code.google.com/p/google-collections/) : simplifies use of lists, maps and multimaps 
