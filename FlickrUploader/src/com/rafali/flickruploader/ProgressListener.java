package com.rafali.flickruploader;

import java.io.File;

public interface ProgressListener {
	void onProgress(File file, int progress);
}