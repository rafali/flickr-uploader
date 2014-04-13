package com.rafali.flickruploader.api;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.json.JSONObject;
import org.slf4j.LoggerFactory;

import se.emilsjolander.sprinkles.CursorList;
import se.emilsjolander.sprinkles.Query;
import se.emilsjolander.sprinkles.Transaction;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.googlecode.flickrjandroid.Flickr;
import com.googlecode.flickrjandroid.FlickrException;
import com.googlecode.flickrjandroid.Parameter;
import com.googlecode.flickrjandroid.RequestContext;
import com.googlecode.flickrjandroid.oauth.OAuth;
import com.googlecode.flickrjandroid.oauth.OAuthToken;
import com.googlecode.flickrjandroid.photos.Photo;
import com.googlecode.flickrjandroid.photos.PhotoList;
import com.googlecode.flickrjandroid.photos.SearchParameters;
import com.googlecode.flickrjandroid.photosets.Photoset;
import com.googlecode.flickrjandroid.tags.Tag;
import com.googlecode.flickrjandroid.uploader.UploadMetaData;
import com.rafali.common.STR;
import com.rafali.common.ToolString;
import com.rafali.flickruploader.enums.CAN_UPLOAD;
import com.rafali.flickruploader.enums.PRIVACY;
import com.rafali.flickruploader.model.FlickrSet;
import com.rafali.flickruploader.model.Media;
import com.rafali.flickruploader.service.UploadService.UploadException;
import com.rafali.flickruploader.tool.Utils;
import com.rafali.flickruploader2.R;

public class FlickrApi {
	static final org.slf4j.Logger LOG = LoggerFactory.getLogger(FlickrApi.class);

	private static final HashSet<String> EXTRAS_MACHINE_TAGS = Sets.newHashSet("machine_tags", "date_upload");

	private static final String API_SECRET = Utils.getString(R.string.flickr_api_secret);
	private static final String API_KEY = Utils.getString(R.string.flickr_api_key);
	private static Flickr flickr = new Flickr(API_KEY, API_SECRET);

	private static OAuth auth;

	public static Flickr get() {
		if (auth == null) {
			updateOauth();
		}
		if (RequestContext.getRequestContext().getOAuth() != auth) {
			RequestContext.getRequestContext().setOAuth(auth);
		}
		return flickr;
	}

	static ExecutorService executorService = Executors.newSingleThreadExecutor();

	public static void reset() {
		auth = null;
		updateOauth();
	}

	private static void updateOauth() {
		String accessToken = Utils.getStringProperty(STR.accessToken);
		String accessTokenSecret = Utils.getStringProperty(STR.accessTokenSecret);
		authentified = ToolString.isNotBlank(accessToken) && ToolString.isNotBlank(accessTokenSecret);
		auth = new OAuth();
		auth.setToken(new OAuthToken(accessToken, accessTokenSecret));
	}

	private static boolean authentified = false;

	static PRIVACY getPrivacy(Photo photo) {
		if (photo.isPublicFlag()) {
			return PRIVACY.PUBLIC;
		} else if (photo.isFamilyFlag() && photo.isFriendFlag()) {
			return PRIVACY.FRIENDS_FAMILY;
		} else if (photo.isFamilyFlag()) {
			return PRIVACY.FAMILY;
		} else if (photo.isFriendFlag()) {
			return PRIVACY.FRIENDS;
		}
		return PRIVACY.PRIVATE;
	}

	private static long lastSyncMedia = 0;

	public static void syncMedia() {
		if (System.currentTimeMillis() - lastSyncMedia > 10 * 60 * 1000L) {
			lastSyncMedia = System.currentTimeMillis();
			executorService.execute(new Runnable() {
				@Override
				public void run() {
					try {
						List<Media> medias = Utils.loadMedia(true);
						Collections.reverse(medias);
						final Map<String, Media> hashMedia = new ConcurrentHashMap<String, Media>();
						for (Media media : medias) {
							hashMedia.put(media.getSha1Tag(), media);
						}
						final Map<String, String> uploadedPhotos = new ConcurrentHashMap<String, String>();
						final Map<String, PRIVACY> photosPrivacy = new ConcurrentHashMap<String, PRIVACY>();
						int totalPage = 10;
						int page = 1;
						int per_page = 100;
						int count = 0;

						// fetching all uploaded photos
						// the flickr query is not consistent (not all
						// photos are retrieved)
						while (page <= totalPage) {
							SearchParameters params = new SearchParameters();
							params.setUserId(Utils.getStringProperty(STR.userId));
							params.setMachineTags(new String[] { "file:sha1sig=" });
							params.setSort(SearchParameters.DATE_POSTED_DESC);
							params.setExtras(EXTRAS_MACHINE_TAGS);
							final PhotoList photoList = FlickrApi.get().getPhotosInterface().search(params, per_page, page);
							totalPage = photoList.getPages();
							per_page = photoList.getPerPage();
							count += photoList.size();
							if (!photoList.isEmpty()) {
								Transaction t = new Transaction();
								LOG.debug(count + " photos with machine tag fetched. page:" + page + "/" + totalPage);
								try {
									for (final Photo photo : photoList) {
										for (String tag : photo.getMachineTags()) {
											if (tag.startsWith("file:sha1sig")) {
												String flickrId = photo.getId();
												uploadedPhotos.put(tag, flickrId);
												photosPrivacy.put(flickrId, getPrivacy(photo));
												Media media = hashMedia.get(tag);
												if (media != null) {
													media.setFlickrId(flickrId);
													media.setTimestampUploaded(photo.getDatePosted());
													media.setPrivacy(photosPrivacy.get(flickrId));
													media.save2(t);
												}
											}
										}
									}
									t.setSuccessful(true);
								} catch (Throwable e) {
									LOG.error(ToolString.stack2string(e));
								} finally {
									t.finish();
								}

								page++;
							} else {
								break;
							}
						}
						for (Media media : medias) {
							String persistedFlickrId = media.getFlickrId();
							String flickrId = uploadedPhotos.get(media.getSha1Tag());
							if (persistedFlickrId != null && flickrId == null) {
								// as all photos may not have been retrieved, we need to check which one
								// have really been deleted manually
								try {
									Photo photo = FlickrApi.get().getPhotosInterface().getPhoto(persistedFlickrId);
									if (photo != null) {
										LOG.debug(persistedFlickrId + "=" + media.getSha1Tag() + " still exist");
										media.setPrivacy(photosPrivacy.get(persistedFlickrId));
										media.save();
									}
								} catch (FlickrException e) {
									// Photo not found
									if ("1".equals(e.getErrorCode())) {
										LOG.debug(flickrId + "=" + media.getSha1Tag() + " no longer still exist");
										media.setFlickrId(null);
										media.setPrivacy(null);
										media.save();
									} else {
										LOG.error(ToolString.stack2string(e));
									}
								} catch (Throwable e) {
									LOG.error(ToolString.stack2string(e));
								}

							}
						}

					} catch (Throwable e) {
						LOG.error(ToolString.stack2string(e));
					}
				}
			});
		}
		executorService.execute(new Runnable() {
			@Override
			public void run() {
				getPhotoSets(true);
			}
		});
	}

	public static void upload(Media media) throws UploadException {
		if (media.getFlickrSetTitle() == null) {
			LOG.warn("photosetTitle should not be null here, setting it to default " + STR.instantUpload);
			media.setFlickrSetTitle(STR.instantUpload);
		}
		try {
			if (media.getFlickrId() == null) {
				String extension = getExtension(media);
				if (unsupportedExtensions.contains(extension)) {
					throw new UploadException("Unsupported extension: " + extension, false);
				}
				String uri = media.getPath();
				File file = new File(uri);
				if (!file.exists()) {
					throw new UploadException("File no longer exists: " + file.getAbsolutePath(), false);
				}
				if (file.length() <= 10) {
					throw new UploadException("File is empty: " + file.getAbsolutePath(), false);
				}
				if (file.length() > 1024 * 1024 * 1024L) {
					throw new UploadException("File too big: " + file.getAbsolutePath(), false);
				}
				String md5tag = media.getMd5Tag();
				String sha1tag = media.getSha1Tag();
				SearchParameters params = new SearchParameters();
				params.setUserId(Utils.getStringProperty(STR.userId));
				params.setMachineTags(new String[] { md5tag });
				PhotoList photoList = FlickrApi.get().getPhotosInterface().search(params, 1, 1);
				if (!photoList.isEmpty()) {
					LOG.warn("already uploaded : " + photoList.get(0).getId() + " = " + md5tag + " = " + uri);
					Photo photo = photoList.get(0);
					String flickrPhotoId = photo.getId();
					media.setFlickrId(flickrPhotoId);
					media.save();
					photo = FlickrApi.get().getPhotosInterface().getInfo(flickrPhotoId, photo.getSecret());
					if (photo != null) {
						List<String> tagstr = new ArrayList<String>();
						Collection<Tag> tags = photo.getTags();
						for (Tag tag : tags) {
							String value = tag.getValue();
							if (value.startsWith("file:sha1sig=") && !value.equals(sha1tag)) {
							} else {
								tagstr.add(value);
							}
							if (!tagstr.contains(sha1tag)) {
								tagstr.add(sha1tag);
								FlickrApi.get().getPhotosInterface().setTags(flickrPhotoId, tagstr.toArray(new String[tagstr.size()]));
							}
						}
					}
				} else {
					if (Utils.canUploadNow() != CAN_UPLOAD.ok) {
						throw new UploadException("status change : " + Utils.canUploadNow(), true);
					} else if (!media.isQueued()) {
						throw new UploadException("media no longer queued", true);
					}
					LOG.debug("uploading : " + uri);
					UploadMetaData metaData = new UploadMetaData();
					String uploadDescription = Utils.getUploadDescription();
					if (ToolString.isNotBlank(uploadDescription)) {
						metaData.setDescription(uploadDescription);
					}
					PRIVACY privacy = Utils.getDefaultPrivacy();
					metaData.setFriendFlag(privacy == PRIVACY.FRIENDS || privacy == PRIVACY.FRIENDS_FAMILY);
					metaData.setFamilyFlag(privacy == PRIVACY.FAMILY || privacy == PRIVACY.FRIENDS_FAMILY);
					metaData.setPublicFlag(privacy == PRIVACY.PUBLIC);
					List<String> tags = Lists.newArrayList(md5tag, sha1tag);
					String custom_tags = Utils.getStringProperty("custom_tags");
					if (ToolString.isNotBlank(custom_tags)) {
						tags.add(custom_tags);
					}
					metaData.setTags(tags);
					long start = System.currentTimeMillis();
					String flickrPhotoId = FlickrApi.get().getUploader().upload(media.getName(), file, metaData, media);
					LOG.debug("photo uploaded in " + (System.currentTimeMillis() - start) + "ms : " + flickrPhotoId);
					media.setFlickrId(flickrPhotoId);
					media.save();

				}
			}
			if (media.getFlickrId() != null) {
				Map<String, FlickrSet> photoSets = getPhotoSets(false);
				FlickrSet flickrSet = photoSets.get(media.getFlickrSetTitle());
				if (flickrSet == null) {
					photoSets = getPhotoSets(true);
					flickrSet = photoSets.get(media.getFlickrSetTitle());
				}
				try {
					if (flickrSet != null && flickrSet.getId().equals(media.getFlickrSetId())) {
						LOG.info(media.getFlickrId() + " photo is already in set " + flickrSet + ", no need to call API");
					} else if (flickrSet == null) {
						Photoset photoset = FlickrApi.get().getPhotosetsInterface().create(media.getFlickrSetTitle(), Utils.getUploadDescription(), media.getFlickrId());
						flickrSet = new FlickrSet(photoset.getId(), photoset.getTitle());
						flickrSet.save();
						flickrSet.setSize(1);
						if (cachedPhotoSets != null) {
							cachedPhotoSets.put(flickrSet.getName(), flickrSet);
						}
					} else {
						FlickrApi.get().getPhotosetsInterface().addPhoto(flickrSet.getId(), media.getFlickrId());
					}
				} catch (FlickrException fe) {
					if ("1".equals(fe.getErrorCode())) {
						LOG.warn("photosetId : " + flickrSet + " not found, photo will not be saved in a set");
						cachedPhotoSets = null;
					} else if ("3".equals(fe.getErrorCode())) {
						LOG.info(media.getFlickrId() + " photo is already in set " + flickrSet);
					} else {
						throw fe;
					}
				}
				if (flickrSet == null) {
					throw new UploadException("failed to add to photoset", true);
				} else {
					media.setFlickrSetId(flickrSet.getId());
					media.save();
				}
				try {
					if (System.currentTimeMillis() - media.getTimestampCreated() > 30 * 60 * 1000L) {
						Date date = new Date(media.getTimestampCreated());
						FlickrApi.get().getPhotosInterface().setDates(media.getFlickrId(), date, date, "0");
					}
				} catch (Throwable e) {
					LOG.error(ToolString.stack2string(e));
				}
			}

		} catch (Throwable e) {
			if (e instanceof FlickrException) {
				FlickrException fe = (FlickrException) e;
				LOG.warn(fe.getErrorCode() + " : " + fe.getErrorMessage());
				if ("1".equals(fe.getErrorCode())) {
					cachedPhotoSets = null;
				} else if ("98".equals(fe.getErrorCode())) {
					auth = null;
					authentified = false;
				} else if ("5".equals(fe.getErrorCode())) {
					addUnsupportedExtension(getExtension(media));
				}
				throw new UploadException(fe.getErrorCode() + " : " + fe.getErrorMessage(), e);
			} else {
				throw new UploadException(e.getMessage(), e);
			}
		}
	}

	static String getExtension(Media media) {
		try {
			File file = new File(media.getPath());
			int lastIndexOf = file.getName().lastIndexOf('.');
			if (lastIndexOf > 0) {
				return file.getName().substring(lastIndexOf + 1).trim().toLowerCase(Locale.US);
			}
		} catch (Exception e1) {
			LOG.error(e1.getClass().getSimpleName() + " : " + e1.getMessage());
		}
		return null;
	}

	static Set<String> unsupportedExtensions = Sets.newHashSet("skm", "mkv");
	static Set<String> whitelistedExtensions = Sets.newHashSet("jpg", "png", "jpeg", "gif", "tiff", "avi", "wmv", "mov", "mpeg", "3gp", "m2ts", "ogg", "ogv");

	static Map<String, Integer> nbUnsupportedExtensions;

	static void addUnsupportedExtension(String extension) {
		if (extension != null && !whitelistedExtensions.contains(extension) && !unsupportedExtensions.contains(extension)) {
			if (nbUnsupportedExtensions == null) {
				nbUnsupportedExtensions = new HashMap<String, Integer>();
			}
			Integer nb = nbUnsupportedExtensions.get(extension);
			if (nb == null) {
				nb = 0;
			}
			if (nb > 1) {
				unsupportedExtensions.add(extension);
			}
			nbUnsupportedExtensions.put(extension, nb + 1);
		}
	}

	public static boolean isNetworkOk() {
		try {
			JSONObject echo = flickr.getTestInterface().echo(Lists.newArrayList(new Parameter("api_key", API_KEY)));
			if (echo.has("stat")) {
				return true;
			}
		} catch (Throwable e) {
			LOG.error("No network : " + e.getClass().getSimpleName() + " : " + e.getMessage());
		}
		return false;
	}

	public static boolean isAuthentified() {
		if (auth == null) {
			updateOauth();
		}
		return authentified;
	}

	protected static String alphabetString = "123456789abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ";
	protected static char[] alphabet = alphabetString.toCharArray();
	protected static int base_count = alphabet.length;
	public static final String FLICKR_SHORT_URL_PREFIX = "http://flic.kr/p/";

	public static final int NB_RETRY = 3;

	public static String getShortUrl(String id) {
		String suffix = encode(Long.parseLong(id));
		return FLICKR_SHORT_URL_PREFIX + suffix;
	}

	public static String encode(long num) {
		String result = "";
		long div;
		int mod = 0;

		while (num >= base_count) {
			div = num / base_count;
			mod = (int) (num - (base_count * (long) div));
			result = alphabet[mod] + result;
			num = (long) div;
		}
		if (num > 0) {
			result = alphabet[(int) num] + result;
		}
		return result;
	}

	static Map<String, FlickrSet> cachedPhotoSets;

	public static Map<String, FlickrSet> getPhotoSets(boolean refresh) {
		Map<String, FlickrSet> photoSets = new HashMap<String, FlickrSet>();
		try {
			CursorList<FlickrSet> cursorList = null;
			if (cachedPhotoSets == null) {
				cachedPhotoSets = new HashMap<String, FlickrSet>();
				cursorList = Query.all(FlickrSet.class).get();
				for (FlickrSet flickrSet : cursorList) {
					cachedPhotoSets.put(flickrSet.getName(), flickrSet);
				}
			}
			if (FlickrApi.isAuthentified() && (refresh || cachedPhotoSets.isEmpty())) {
				Map<String, FlickrSet> freshPhotoSets = new HashMap<String, FlickrSet>();
				// Log.i(TAG, "persisted uploadedPhotos : " + uploadedPhotos);
				Collection<Photoset> photosets = null;
				int retry = 0;
				while (photosets == null && retry < 3) {
					try {
						photosets = FlickrApi.get().getPhotosetsInterface().getList(Utils.getStringProperty(STR.userId)).getPhotosets();
					} catch (Throwable e) {
						LOG.error(ToolString.stack2string(e));
						try {
							Thread.sleep((long) (Math.pow(4, retry) * 1000L));
						} catch (InterruptedException e1) {
						}
					} finally {
						retry++;
					}
				}
				if (photosets != null && !photosets.isEmpty()) {
					cachedPhotoSets.clear();
					Transaction t = new Transaction();
					try {
						for (final Photoset photoset : photosets) {
							FlickrSet flickrSet = new FlickrSet(photoset.getId(), photoset.getTitle());
							flickrSet.setSize(photoset.getPhotoCount());
							flickrSet.save(t);
							freshPhotoSets.put(photoset.getId(), flickrSet);
							cachedPhotoSets.put(photoset.getTitle(), flickrSet);
						}

						cursorList = Query.all(FlickrSet.class).get();
						for (FlickrSet flickrSetName : cursorList) {
							if (!freshPhotoSets.containsKey(flickrSetName.getId())) {
								flickrSetName.delete(t);
							}
						}

						t.setSuccessful(true);
					} catch (Throwable e) {
						LOG.error(ToolString.stack2string(e));
					} finally {
						t.finish();
					}
				}

			}
			photoSets.putAll(cachedPhotoSets);
		} catch (Throwable e) {
			LOG.error(ToolString.stack2string(e));
		}
		return photoSets;
	}

	public static boolean isStillOnFlickr(Media media) {
		if (isAuthentified()) {
			String md5tag = media.getMd5Tag();
			SearchParameters params = new SearchParameters();
			params.setUserId(Utils.getStringProperty(STR.userId));
			params.setMachineTags(new String[] { md5tag });
			PhotoList photoList = null;
			int retry = 0;
			while (photoList == null && retry < 3) {
				try {
					photoList = FlickrApi.get().getPhotosInterface().search(params, 1, 1);
					if (photoList != null && !photoList.isEmpty()) {
						Photo photo = photoList.get(0);
						LOG.warn(media + " is uploaded : " + photo.getId() + " = " + md5tag);
						return true;
					}
				} catch (Throwable e) {
					LOG.error(ToolString.stack2string(e));
					try {
						Thread.sleep((long) (Math.pow(4, retry) * 1000L));
					} catch (InterruptedException e1) {
					}
				} finally {
					retry++;
				}
			}
		}
		return false;
	}

}
