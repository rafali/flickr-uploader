package com.rafali.flickruploader.model;

import java.io.File;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import se.emilsjolander.sprinkles.annotations.Cacheable;

import se.emilsjolander.sprinkles.Model;
import se.emilsjolander.sprinkles.Transaction;
import se.emilsjolander.sprinkles.annotations.Column;
import se.emilsjolander.sprinkles.annotations.PrimaryKey;
import se.emilsjolander.sprinkles.annotations.Table;

import com.rafali.common.ToolString;
import com.rafali.flickruploader.enums.MEDIA_TYPE;
import com.rafali.flickruploader.enums.PRIVACY;
import com.rafali.flickruploader.enums.STATUS;
import com.rafali.flickruploader.tool.Utils;
import com.rafali.flickruploader.ui.activity.FlickrUploaderActivity;

@Table("Media")
@Cacheable
public class Media extends Model {

	@PrimaryKey
	@Column("id")
	private int id;

	@Column("path")
	private String path;

	@Column("mediaType")
	private int mediaType;

	@Column("size")
	private int size;

	@Column("md5Sum")
	private String md5Sum;

	@Column("timestampCreated")
	private long timestampCreated;

	@Column("timestampImported")
	private long timestampImported;

	@Column("timestampUploaded")
	private long timestampUploaded;

	@Column("timestampQueued")
	private long timestampQueued;

	@Column("flickrId")
	private String flickrId;

	@Column("flickrSetTitle")
	private String flickrSetTitle;

	@Column("flickrSetId")
	private String flickrSetId;

	@Column("privacy")
	private int privacy;

	@Column("status")
	private int status;

	@Column("retries")
	private int retries;

	@Column("timestampRetry")
	private long timestampRetry;

	@Column("errorMessage")
	private String errorMessage;
	
	private long timestampUploadStarted;

	public Media() {
	}

	public Media(int status) {
		this.status = status;
	}

	@Override
	public String toString() {
		return this.id + " - " + path + " - retries:" + retries;
	}

	@Override
	public int hashCode() {
		if (this.path != null)
			return this.path.hashCode();
		return super.hashCode();
	}

	@Override
	public boolean equals(Object o) {
		if (o == this) {
			return true;
		}
		if (o instanceof Media) {
			return ((Media) o).path.equals(this.path);
		}
		return super.equals(o);
	}

	public String getFolderPath() {
		return ToolString.getParentPath(this.path);
	}

	public String getFolderName() {
		return ToolString.getFileName(getFolderPath());
	}

	public int getMediaType() {
		return this.mediaType;
	}

	public void setMediaType(int mediaType) {
		this.mediaType = mediaType;
	}

	public boolean isPhoto() {
		return this.mediaType == MEDIA_TYPE.PHOTO;
	}

	public boolean isVideo() {
		return this.mediaType == MEDIA_TYPE.VIDEO;
	}

	public int getId() {
		return this.id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public String getPath() {
		return this.path;
	}

	public void setPath(String path) {
		this.path = path;
	}

	public String getName() {
		return ToolString.getFileName(this.path);
	}

	public int getSize() {
		return this.size;
	}

	public void setSize(int size) {
		this.size = size;
	}

	public long getTimestampCreated() {
		return this.timestampCreated;
	}

	public void setTimestampCreated(long timestampCreated) {
		this.timestampCreated = timestampCreated;
	}

	public long getTimestampImported() {
		return this.timestampImported;
	}

	public String getMd5Sum() {
		if (this.md5Sum == null) {
			// this call is expensive, around 7 hash/sec on a Moto X
			this.md5Sum = Utils.getMD5Checksum(path);
			saveAsync();
		}
		return this.md5Sum;
	}

	public String getMd5Tag() {
		return "file:md5sum=" + getMd5Sum();
	}

	public void setMd5Sum(String md5Sum) {
		this.md5Sum = md5Sum;
	}

	public boolean isUploaded() {
		return ToolString.isNotBlank(flickrId);
	}

	public String getFlickrId() {
		return this.flickrId;
	}

	public void setFlickrId(String flickrId) {
		if (ToolString.isNotBlank(flickrId)) {
			this.flickrId = flickrId;
			setStatus(STATUS.UPLOADED);
		} else if (status == STATUS.UPLOADED) {
			this.flickrId = null;
			setStatus(STATUS.PAUSED);
		}
	}

	public PRIVACY getPrivacy() {
		switch (this.privacy) {
		case 1:
			return PRIVACY.PRIVATE;
		case 2:
			return PRIVACY.FAMILY;
		case 3:
			return PRIVACY.FRIENDS;
		case 4:
			return PRIVACY.FRIENDS_FAMILY;
		case 5:
			return PRIVACY.PUBLIC;
		default:
			break;
		}
		return null;
	}

	public void setPrivacy(PRIVACY privacy) {
		if (privacy == null) {
			this.privacy = 0;
		} else {
			switch (privacy) {
			case PRIVATE:
				this.privacy = 1;
				break;
			case FAMILY:
				this.privacy = 2;
				break;
			case FRIENDS:
				this.privacy = 3;
				break;
			case FRIENDS_FAMILY:
				this.privacy = 4;
				break;
			case PUBLIC:
				this.privacy = 5;
				break;
			default:
				this.privacy = 0;
				break;
			}
		}
	}

	public String getSha1Tag() {
		return ("file:sha1sig=" + Utils.SHA1(this.path + "_" + new File(this.path).length())).toLowerCase(Locale.US);
	}

	@Override
	protected void beforeCreate() {
		this.timestampImported = System.currentTimeMillis();
	}

	public void save2(Transaction t) {
		FlickrUploaderActivity.updateStatic(this);
		if (t == null) {
			save();
		} else {
			save(t);
		}
	}

	public int getStatus() {
		return status;
	}

	public void setStatus(int status) {
		setStatus(status, null);
	}

	public void setStatus(int status, Transaction t) {
		if (this.status != status) {
			this.status = status;
			if (status == STATUS.QUEUED) {
				setRetries(0);
				setTimestampQueued(System.currentTimeMillis());
			} else if (status == STATUS.UPLOADED) {
				setTimestampUploaded(System.currentTimeMillis());
			}
			if (t != null) {
				save2(t);
			}
		}
	}

	static ExecutorService executor = Executors.newSingleThreadExecutor();

	public boolean isQueued() {
		return this.status == STATUS.QUEUED;
	}

	public boolean isFailed() {
		return this.status == STATUS.FAILED;
	}

	public boolean isImported() {
		return this.status == STATUS.IMPORTED;
	}

	public int getRetries() {
		return retries;
	}

	public void setRetries(int retries) {
		if (this.retries != retries) {
			this.retries = Math.max(0, retries);
			if (this.retries == 0) {
				setTimestampRetry(0);
			} else {
				setTimestampRetry((long) (System.currentTimeMillis() + Math.min(3600 * 1000L, Math.pow(3, this.retries) * 1000L)));
			}
		}
	}

	public long getTimestampUploaded() {
		if (isUploaded()) {
			if (timestampUploaded > 0)
				return timestampUploaded;
			else
				return timestampCreated;
		}
		return -1;
	}

	public void setTimestampUploaded(long timestampUploaded) {
		this.timestampUploaded = timestampUploaded;
	}

	public void setTimestampUploaded(Date dateUploaded) {
		if (dateUploaded != null) {
			setTimestampUploaded(dateUploaded.getTime());
		}
	}

	public long getTimestampQueued() {
		if (timestampQueued > 0) {
			return timestampQueued;
		} else {
			return getTimestampUploaded();
		}
	}

	public void setTimestampQueued(long timestampQueued) {
		this.timestampQueued = timestampQueued;
	}

	public long getTimestampRetry() {
		return timestampRetry;
	}

	public void setTimestampRetry(long timestampRetry) {
		this.timestampRetry = timestampRetry;
	}

	public String getFlickrSetId() {
		return flickrSetId;
	}

	public void setFlickrSetId(String flickrSetId) {
		this.flickrSetId = flickrSetId;
	}

	public String getFlickrSetTitle() {
		return flickrSetTitle;
	}

	public void setFlickrSetTitle(String flickrSetTitle) {
		this.flickrSetTitle = flickrSetTitle;
	}

	public String getErrorMessage() {
		return errorMessage;
	}

	public void setErrorMessage(String errorMessage) {
		this.errorMessage = errorMessage;
	}

	public int getProgress() {
		return progress;
	}

	public void setProgress(int progress) {
		this.progress = progress;
	}

	public long getTimestampUploadStarted() {
		return timestampUploadStarted;
	}

	public void setTimestampUploadStarted(long timestampUploadStarted) {
		this.timestampUploadStarted = timestampUploadStarted;
	}

	private int progress = 0;

}
