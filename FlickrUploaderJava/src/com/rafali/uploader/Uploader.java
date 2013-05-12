package com.rafali.uploader;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.prefs.Preferences;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.Tag;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

public class Uploader {
	public static String MD5(String md5) {
		return MD5(md5.getBytes());
	}
	public static String MD5(byte[] bytearray) {
		try {
			java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
			byte[] array = md.digest(bytearray);
			StringBuffer sb = new StringBuffer();
			for (int i = 0; i < array.length; ++i) {
				sb.append(Integer.toHexString((array[i] & 0xFF) | 0x100).substring(1, 3));
			}
			return sb.toString();
		} catch (java.security.NoSuchAlgorithmException e) {
		}
		return null;
	}

	public static void main(String[] args) {
		try {
			ResourceBundle config = ResourceBundle.getBundle("config");
			
			System.out.println(config.getString("flickr_api_key"));
			
			
//			prefs.put("test", "hey dude");
//			System.out.println(prefs.get("test", "haha"));
//			long start = System.currentTimeMillis();
//			InputStream input = new URL("http://farm8.staticflickr.com/7217/7298872164_c754fc8cce_o.jpg").openStream();
//			Metadata metadata = ImageMetadataReader.readMetadata(new BufferedInputStream(input), true);
//			System.out.println("#### " + (System.currentTimeMillis() - start));
//			Iterable<Directory> directories = metadata.getDirectories();
//			for (Directory directory : directories) {
//				System.out.println("##### " + directory.getName());
//				Collection<Tag> tags = directory.getTags();
//				for (Tag tag : tags) {
//					System.out.println(tag.getTagType() + " : " + tag.toString());
//				}
//			}
//			System.out.println("#### " + (System.currentTimeMillis() - start));

		} catch (Throwable e) {
			e.printStackTrace();
		}
	}

	static void testLocal() {
		String path = "/Volumes/NO NAME/DCIM/102_2512";
		// String path = "/Users/emerix/Dropbox/IMG_20120819_181921.jpg";
		System.out.println(path);
		try {
			Multimap<String, Object> objects = HashMultimap.create();
			File folder = new File(path);
			for (File file : folder.listFiles()) {
				Metadata metadata = ImageMetadataReader.readMetadata(file);
				Iterable<Directory> directories = metadata.getDirectories();
				for (Directory directory : directories) {
					System.out.println("##### " + directory.getName());
					Collection<Tag> tags = directory.getTags();
					for (Tag tag : tags) {
						byte[] byteArray = directory.getByteArray(tag.getTagType());
						String str = byteArray != null ? MD5(byteArray) : "null";
						objects.put(map.get(tag.getTagType()) + " : " + tag.getTagType() + " - " + tag.getTagName(), str);
						System.out.println(tag.getTagType() + " : " + tag.toString());
					}
				}
			}
			for (String tagId : objects.keySet()) {
				System.out.println(objects.get(tagId).size() + " : " + tagId + " : " + objects.get(tagId));
			}

			System.out.println("nb files : " + folder.listFiles().length);

		} catch (ImageProcessingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	static Map<Integer, String> map = new HashMap<Integer, String>();
	static {
		map.put(11, "Exif.Image.ProcessingSoftware");
		map.put(254, "Exif.Image.NewSubfileType");
		map.put(255, "Exif.Image.SubfileType");
		map.put(256, "Exif.Image.ImageWidth");
		map.put(257, "Exif.Image.ImageLength");
		map.put(258, "Exif.Image.BitsPerSample");
		map.put(259, "Exif.Image.Compression");
		map.put(262, "Exif.Image.PhotometricInterpretation");
		map.put(263, "Exif.Image.Threshholding");
		map.put(264, "Exif.Image.CellWidth");
		map.put(265, "Exif.Image.CellLength");
		map.put(266, "Exif.Image.FillOrder");
		map.put(269, "Exif.Image.DocumentName");
		map.put(270, "Exif.Image.ImageDescription");
		map.put(271, "Exif.Image.Make");
		map.put(272, "Exif.Image.Model");
		map.put(273, "Exif.Image.StripOffsets");
		map.put(274, "Exif.Image.Orientation");
		map.put(277, "Exif.Image.SamplesPerPixel");
		map.put(278, "Exif.Image.RowsPerStrip");
		map.put(279, "Exif.Image.StripByteCounts");
		map.put(282, "Exif.Image.XResolution");
		map.put(283, "Exif.Image.YResolution");
		map.put(284, "Exif.Image.PlanarConfiguration");
		map.put(290, "Exif.Image.GrayResponseUnit");
		map.put(291, "Exif.Image.GrayResponseCurve");
		map.put(292, "Exif.Image.T4Options");
		map.put(293, "Exif.Image.T6Options");
		map.put(296, "Exif.Image.ResolutionUnit");
		map.put(301, "Exif.Image.TransferFunction");
		map.put(305, "Exif.Image.Software");
		map.put(306, "Exif.Image.DateTime");
		map.put(315, "Exif.Image.Artist");
		map.put(316, "Exif.Image.HostComputer");
		map.put(317, "Exif.Image.Predictor");
		map.put(318, "Exif.Image.WhitePoint");
		map.put(319, "Exif.Image.PrimaryChromaticities");
		map.put(320, "Exif.Image.ColorMap");
		map.put(321, "Exif.Image.HalftoneHints");
		map.put(322, "Exif.Image.TileWidth");
		map.put(323, "Exif.Image.TileLength");
		map.put(324, "Exif.Image.TileOffsets");
		map.put(325, "Exif.Image.TileByteCounts");
		map.put(330, "Exif.Image.SubIFDs");
		map.put(332, "Exif.Image.InkSet");
		map.put(333, "Exif.Image.InkNames");
		map.put(334, "Exif.Image.NumberOfInks");
		map.put(336, "Exif.Image.DotRange");
		map.put(337, "Exif.Image.TargetPrinter");
		map.put(338, "Exif.Image.ExtraSamples");
		map.put(339, "Exif.Image.SampleFormat");
		map.put(340, "Exif.Image.SMinSampleValue");
		map.put(341, "Exif.Image.SMaxSampleValue");
		map.put(342, "Exif.Image.TransferRange");
		map.put(343, "Exif.Image.ClipPath");
		map.put(344, "Exif.Image.XClipPathUnits");
		map.put(345, "Exif.Image.YClipPathUnits");
		map.put(346, "Exif.Image.Indexed");
		map.put(347, "Exif.Image.JPEGTables");
		map.put(351, "Exif.Image.OPIProxy");
		map.put(512, "Exif.Image.JPEGProc");
		map.put(513, "Exif.Image.JPEGInterchangeFormat");
		map.put(514, "Exif.Image.JPEGInterchangeFormatLength");
		map.put(515, "Exif.Image.JPEGRestartInterval");
		map.put(517, "Exif.Image.JPEGLosslessPredictors");
		map.put(518, "Exif.Image.JPEGPointTransforms");
		map.put(519, "Exif.Image.JPEGQTables");
		map.put(520, "Exif.Image.JPEGDCTables");
		map.put(521, "Exif.Image.JPEGACTables");
		map.put(529, "Exif.Image.YCbCrCoefficients");
		map.put(530, "Exif.Image.YCbCrSubSampling");
		map.put(531, "Exif.Image.YCbCrPositioning");
		map.put(532, "Exif.Image.ReferenceBlackWhite");
		map.put(700, "Exif.Image.XMLPacket");
		map.put(18246, "Exif.Image.Rating");
		map.put(18249, "Exif.Image.RatingPercent");
		map.put(32781, "Exif.Image.ImageID");
		map.put(33421, "Exif.Image.CFARepeatPatternDim");
		map.put(33422, "Exif.Image.CFAPattern");
		map.put(33423, "Exif.Image.BatteryLevel");
		map.put(33432, "Exif.Image.Copyright");
		map.put(33434, "Exif.Image.ExposureTime");
		map.put(33437, "Exif.Image.FNumber");
		map.put(33723, "Exif.Image.IPTCNAA");
		map.put(34377, "Exif.Image.ImageResources");
		map.put(34665, "Exif.Image.ExifTag");
		map.put(34675, "Exif.Image.InterColorProfile");
		map.put(34850, "Exif.Image.ExposureProgram");
		map.put(34852, "Exif.Image.SpectralSensitivity");
		map.put(34853, "Exif.Image.GPSTag");
		map.put(34855, "Exif.Image.ISOSpeedRatings");
		map.put(34856, "Exif.Image.OECF");
		map.put(34857, "Exif.Image.Interlace");
		map.put(34858, "Exif.Image.TimeZoneOffset");
		map.put(34859, "Exif.Image.SelfTimerMode");
		map.put(36867, "Exif.Image.DateTimeOriginal");
		map.put(37122, "Exif.Image.CompressedBitsPerPixel");
		map.put(37377, "Exif.Image.ShutterSpeedValue");
		map.put(37378, "Exif.Image.ApertureValue");
		map.put(37379, "Exif.Image.BrightnessValue");
		map.put(37380, "Exif.Image.ExposureBiasValue");
		map.put(37381, "Exif.Image.MaxApertureValue");
		map.put(37382, "Exif.Image.SubjectDistance");
		map.put(37383, "Exif.Image.MeteringMode");
		map.put(37384, "Exif.Image.LightSource");
		map.put(37385, "Exif.Image.Flash");
		map.put(37386, "Exif.Image.FocalLength");
		map.put(37387, "Exif.Image.FlashEnergy");
		map.put(37388, "Exif.Image.SpatialFrequencyResponse");
		map.put(37389, "Exif.Image.Noise");
		map.put(37390, "Exif.Image.FocalPlaneXResolution");
		map.put(37391, "Exif.Image.FocalPlaneYResolution");
		map.put(37392, "Exif.Image.FocalPlaneResolutionUnit");
		map.put(37393, "Exif.Image.ImageNumber");
		map.put(37394, "Exif.Image.SecurityClassification");
		map.put(37395, "Exif.Image.ImageHistory");
		map.put(37396, "Exif.Image.SubjectLocation");
		map.put(37397, "Exif.Image.ExposureIndex");
		map.put(37398, "Exif.Image.TIFFEPStandardID");
		map.put(37399, "Exif.Image.SensingMethod");
		map.put(40091, "Exif.Image.XPTitle");
		map.put(40092, "Exif.Image.XPComment");
		map.put(40093, "Exif.Image.XPAuthor");
		map.put(40094, "Exif.Image.XPKeywords");
		map.put(40095, "Exif.Image.XPSubject");
		map.put(50341, "Exif.Image.PrintImageMatching");
		map.put(50706, "Exif.Image.DNGVersion");
		map.put(50707, "Exif.Image.DNGBackwardVersion");
		map.put(50708, "Exif.Image.UniqueCameraModel");
		map.put(50709, "Exif.Image.LocalizedCameraModel");
		map.put(50710, "Exif.Image.CFAPlaneColor");
		map.put(50711, "Exif.Image.CFALayout");
		map.put(50712, "Exif.Image.LinearizationTable");
		map.put(50713, "Exif.Image.BlackLevelRepeatDim");
		map.put(50714, "Exif.Image.BlackLevel");
		map.put(50715, "Exif.Image.BlackLevelDeltaH");
		map.put(50716, "Exif.Image.BlackLevelDeltaV");
		map.put(50717, "Exif.Image.WhiteLevel");
		map.put(50718, "Exif.Image.DefaultScale");
		map.put(50719, "Exif.Image.DefaultCropOrigin");
		map.put(50720, "Exif.Image.DefaultCropSize");
		map.put(50721, "Exif.Image.ColorMatrix1");
		map.put(50722, "Exif.Image.ColorMatrix2");
		map.put(50723, "Exif.Image.CameraCalibration1");
		map.put(50724, "Exif.Image.CameraCalibration2");
		map.put(50725, "Exif.Image.ReductionMatrix1");
		map.put(50726, "Exif.Image.ReductionMatrix2");
		map.put(50727, "Exif.Image.AnalogBalance");
		map.put(50728, "Exif.Image.AsShotNeutral");
		map.put(50729, "Exif.Image.AsShotWhiteXY");
		map.put(50730, "Exif.Image.BaselineExposure");
		map.put(50731, "Exif.Image.BaselineNoise");
		map.put(50732, "Exif.Image.BaselineSharpness");
		map.put(50733, "Exif.Image.BayerGreenSplit");
		map.put(50734, "Exif.Image.LinearResponseLimit");
		map.put(50735, "Exif.Image.CameraSerialNumber");
		map.put(50736, "Exif.Image.LensInfo");
		map.put(50737, "Exif.Image.ChromaBlurRadius");
		map.put(50738, "Exif.Image.AntiAliasStrength");
		map.put(50739, "Exif.Image.ShadowScale");
		map.put(50740, "Exif.Image.DNGPrivateData");
		map.put(50741, "Exif.Image.MakerNoteSafety");
		map.put(50778, "Exif.Image.CalibrationIlluminant1");
		map.put(50779, "Exif.Image.CalibrationIlluminant2");
		map.put(50780, "Exif.Image.BestQualityScale");
		map.put(50781, "Exif.Image.RawDataUniqueID");
		map.put(50827, "Exif.Image.OriginalRawFileName");
		map.put(50828, "Exif.Image.OriginalRawFileData");
		map.put(50829, "Exif.Image.ActiveArea");
		map.put(50830, "Exif.Image.MaskedAreas");
		map.put(50831, "Exif.Image.AsShotICCProfile");
		map.put(50832, "Exif.Image.AsShotPreProfileMatrix");
		map.put(50833, "Exif.Image.CurrentICCProfile");
		map.put(50834, "Exif.Image.CurrentPreProfileMatrix");
		map.put(50879, "Exif.Image.ColorimetricReference");
		map.put(50931, "Exif.Image.CameraCalibrationSignature");
		map.put(50932, "Exif.Image.ProfileCalibrationSignature");
		map.put(50934, "Exif.Image.AsShotProfileName");
		map.put(50935, "Exif.Image.NoiseReductionApplied");
		map.put(50936, "Exif.Image.ProfileName");
		map.put(50937, "Exif.Image.ProfileHueSatMapDims");
		map.put(50938, "Exif.Image.ProfileHueSatMapData1");
		map.put(50939, "Exif.Image.ProfileHueSatMapData2");
		map.put(50940, "Exif.Image.ProfileToneCurve");
		map.put(50941, "Exif.Image.ProfileEmbedPolicy");
		map.put(50942, "Exif.Image.ProfileCopyright");
		map.put(50964, "Exif.Image.ForwardMatrix1");
		map.put(50965, "Exif.Image.ForwardMatrix2");
		map.put(50966, "Exif.Image.PreviewApplicationName");
		map.put(50967, "Exif.Image.PreviewApplicationVersion");
		map.put(50968, "Exif.Image.PreviewSettingsName");
		map.put(50969, "Exif.Image.PreviewSettingsDigest");
		map.put(50970, "Exif.Image.PreviewColorSpace");
		map.put(50971, "Exif.Image.PreviewDateTime");
		map.put(50972, "Exif.Image.RawImageDigest");
		map.put(50973, "Exif.Image.OriginalRawFileDigest");
		map.put(50974, "Exif.Image.SubTileBlockSize");
		map.put(50975, "Exif.Image.RowInterleaveFactor");
		map.put(50981, "Exif.Image.ProfileLookTableDims");
		map.put(50982, "Exif.Image.ProfileLookTableData");
		map.put(51008, "Exif.Image.OpcodeList1");
		map.put(51009, "Exif.Image.OpcodeList2");
		map.put(51022, "Exif.Image.OpcodeList3");
		map.put(51041, "Exif.Image.NoiseProfile");
		map.put(33434, "Exif.Photo.ExposureTime");
		map.put(33437, "Exif.Photo.FNumber");
		map.put(34850, "Exif.Photo.ExposureProgram");
		map.put(34852, "Exif.Photo.SpectralSensitivity");
		map.put(34855, "Exif.Photo.ISOSpeedRatings");
		map.put(34856, "Exif.Photo.OECF");
		map.put(34864, "Exif.Photo.SensitivityType");
		map.put(34865, "Exif.Photo.StandardOutputSensitivity");
		map.put(34866, "Exif.Photo.RecommendedExposureIndex");
		map.put(34867, "Exif.Photo.ISOSpeed");
		map.put(34868, "Exif.Photo.ISOSpeedLatitudeyyy");
		map.put(34869, "Exif.Photo.ISOSpeedLatitudezzz");
		map.put(36864, "Exif.Photo.ExifVersion");
		map.put(36867, "Exif.Photo.DateTimeOriginal");
		map.put(36868, "Exif.Photo.DateTimeDigitized");
		map.put(37121, "Exif.Photo.ComponentsConfiguration");
		map.put(37122, "Exif.Photo.CompressedBitsPerPixel");
		map.put(37377, "Exif.Photo.ShutterSpeedValue");
		map.put(37378, "Exif.Photo.ApertureValue");
		map.put(37379, "Exif.Photo.BrightnessValue");
		map.put(37380, "Exif.Photo.ExposureBiasValue");
		map.put(37381, "Exif.Photo.MaxApertureValue");
		map.put(37382, "Exif.Photo.SubjectDistance");
		map.put(37383, "Exif.Photo.MeteringMode");
		map.put(37384, "Exif.Photo.LightSource");
		map.put(37385, "Exif.Photo.Flash");
		map.put(37386, "Exif.Photo.FocalLength");
		map.put(37396, "Exif.Photo.SubjectArea");
		map.put(37500, "Exif.Photo.MakerNote");
		map.put(37510, "Exif.Photo.UserComment");
		map.put(37520, "Exif.Photo.SubSecTime");
		map.put(37521, "Exif.Photo.SubSecTimeOriginal");
		map.put(37522, "Exif.Photo.SubSecTimeDigitized");
		map.put(40960, "Exif.Photo.FlashpixVersion");
		map.put(40961, "Exif.Photo.ColorSpace");
		map.put(40962, "Exif.Photo.PixelXDimension");
		map.put(40963, "Exif.Photo.PixelYDimension");
		map.put(40964, "Exif.Photo.RelatedSoundFile");
		map.put(40965, "Exif.Photo.InteroperabilityTag");
		map.put(41483, "Exif.Photo.FlashEnergy");
		map.put(41484, "Exif.Photo.SpatialFrequencyResponse");
		map.put(41486, "Exif.Photo.FocalPlaneXResolution");
		map.put(41487, "Exif.Photo.FocalPlaneYResolution");
		map.put(41488, "Exif.Photo.FocalPlaneResolutionUnit");
		map.put(41492, "Exif.Photo.SubjectLocation");
		map.put(41493, "Exif.Photo.ExposureIndex");
		map.put(41495, "Exif.Photo.SensingMethod");
		map.put(41728, "Exif.Photo.FileSource");
		map.put(41729, "Exif.Photo.SceneType");
		map.put(41730, "Exif.Photo.CFAPattern");
		map.put(41985, "Exif.Photo.CustomRendered");
		map.put(41986, "Exif.Photo.ExposureMode");
		map.put(41987, "Exif.Photo.WhiteBalance");
		map.put(41988, "Exif.Photo.DigitalZoomRatio");
		map.put(41989, "Exif.Photo.FocalLengthIn35mmFilm");
		map.put(41990, "Exif.Photo.SceneCaptureType");
		map.put(41991, "Exif.Photo.GainControl");
		map.put(41992, "Exif.Photo.Contrast");
		map.put(41993, "Exif.Photo.Saturation");
		map.put(41994, "Exif.Photo.Sharpness");
		map.put(41995, "Exif.Photo.DeviceSettingDescription");
		map.put(41996, "Exif.Photo.SubjectDistanceRange");
		map.put(42016, "Exif.Photo.ImageUniqueID");
		map.put(42032, "Exif.Photo.CameraOwnerName");
		map.put(42033, "Exif.Photo.BodySerialNumber");
		map.put(42034, "Exif.Photo.LensSpecification");
		map.put(42035, "Exif.Photo.LensMake");
		map.put(42036, "Exif.Photo.LensModel");
		map.put(42037, "Exif.Photo.LensSerialNumber");
		map.put(1, "Exif.Iop.InteroperabilityIndex");
		map.put(2, "Exif.Iop.InteroperabilityVersion");
		map.put(4096, "Exif.Iop.RelatedImageFileFormat");
		map.put(4097, "Exif.Iop.RelatedImageWidth");
		map.put(4098, "Exif.Iop.RelatedImageLength");
		map.put(0, "Exif.GPSInfo.GPSVersionID");
		map.put(1, "Exif.GPSInfo.GPSLatitudeRef");
		map.put(2, "Exif.GPSInfo.GPSLatitude");
		map.put(3, "Exif.GPSInfo.GPSLongitudeRef");
		map.put(4, "Exif.GPSInfo.GPSLongitude");
		map.put(5, "Exif.GPSInfo.GPSAltitudeRef");
		map.put(6, "Exif.GPSInfo.GPSAltitude");
		map.put(7, "Exif.GPSInfo.GPSTimeStamp");
		map.put(8, "Exif.GPSInfo.GPSSatellites");
		map.put(9, "Exif.GPSInfo.GPSStatus");
		map.put(10, "Exif.GPSInfo.GPSMeasureMode");
		map.put(11, "Exif.GPSInfo.GPSDOP");
		map.put(12, "Exif.GPSInfo.GPSSpeedRef");
		map.put(13, "Exif.GPSInfo.GPSSpeed");
		map.put(14, "Exif.GPSInfo.GPSTrackRef");
		map.put(15, "Exif.GPSInfo.GPSTrack");
		map.put(16, "Exif.GPSInfo.GPSImgDirectionRef");
		map.put(17, "Exif.GPSInfo.GPSImgDirection");
		map.put(18, "Exif.GPSInfo.GPSMapDatum");
		map.put(19, "Exif.GPSInfo.GPSDestLatitudeRef");
		map.put(20, "Exif.GPSInfo.GPSDestLatitude");
		map.put(21, "Exif.GPSInfo.GPSDestLongitudeRef");
		map.put(22, "Exif.GPSInfo.GPSDestLongitude");
		map.put(23, "Exif.GPSInfo.GPSDestBearingRef");
		map.put(24, "Exif.GPSInfo.GPSDestBearing");
		map.put(25, "Exif.GPSInfo.GPSDestDistanceRef");
		map.put(26, "Exif.GPSInfo.GPSDestDistance");
		map.put(27, "Exif.GPSInfo.GPSProcessingMethod");
		map.put(28, "Exif.GPSInfo.GPSAreaInformation");
		map.put(29, "Exif.GPSInfo.GPSDateStamp");
		map.put(30, "Exif.GPSInfo.GPSDifferential");

	}
}
