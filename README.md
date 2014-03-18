Flickr Uploader
===============

The goal of this project is to help Flickr users automatically backup the photos from their Android phone.
It works like [Google+ Instant Upload](http://support.google.com/plus/answer/2910392?hl=en) or [Dropbox Camera Upload](https://blog.dropbox.com/2012/02/your-photos-simplified-part-1/).

The main benefit of this app over Google+ Instant Upload is the fact that you can upload ONE FREAKING TERABYTE of photos&videos in **high resolution** for free!

You can download it at : https://play.google.com/store/apps/details?id=com.rafali.flickruploader2

You can fork this project or use the source code for any project. It is licensed under the GPL v2. I just request that you do not blatantly copy the app to republish it on the Play Store.
You may however use it as a base to create an uploader to a service other than Flickr. You should just have to republish the source code as the GPL v2 demand.

It uses a few open source libraries:
- [flickrj-android](https://code.google.com/p/flickrj-android/) : a modified version of the old java flickr lib optimized for Android and Google App Engine
- [Android-BitmapCache](https://github.com/chrisbanes/Android-BitmapCache) : takes care of bitmaps caching/recycling for you
- [Android-ViewPagerIndicator](https://github.com/JakeWharton/Android-ViewPagerIndicator) : displays a nice tab UI above a ViewPager
- [ACRA](https://github.com/ACRA/acra) : sends crash report to a configurable url
- [AndroidAnnotation](https://github.com/excilys/androidannotations) : simplifies Android development with annotations to define code scope (UI thread, background thread…)
- [google-collections](https://code.google.com/p/google-collections/) : simplifies use of lists, maps and multimaps 
