package com.itcall.util;

import java.time.ZoneId;

public class Const {

	public static final String[] BASE_PACKAGES = new String[]{"com.itcall.herocu.api"};
	public static final String[] JPA_PACKAGES = {"com.itcall.herocu.api.jpa", "com.itcall.herocu.api.*.jpa", "com.itcall.herocu.api.*.*.jpa", "com.itcall.herocu.api.*.*.*.jpa"};
	public static final String[] REDIS_PACKAGES = {"com.itcall.herocu.api.redis", "com.itcall.herocu.api.*.redis", "com.itcall.herocu.api.*.*.redis", "com.itcall.herocu.api.*.*.*.redis"};

	public static final String DEF_DATE_TIME_FORMAT = "yyyy-MM-dd HH:mm:ss";

	public static final String DEF_FILE_TEMP_UPLOAD_KEY = "file.upload.temp.path";
	public static final String DEF_FILE_TEMP_EXT = "tmp";

	public static final String ENV_AWS_S3_BUCKET     = "HDRIVE_S3_BUCKET";
	public static final String ENV_AWS_S3_SECRET_KEY = "HDRIVE_S3_SECRET_KEY";
	public static final String ENV_AWS_S3_ACCESS_KEY = "HDRIVE_S3_ACCESS_KEY";

	public static final String ENV_SERVER_APP_NAME_KEY = "server.app.name";
	public static final String ENV_SERVER_APP_NO_KEY = "server.app.names.no";

	public static String ENV_PROFILE = "prod";
	public static boolean envIsDev() {
		return !ENV_PROFILE.equalsIgnoreCase("prod");
	}

	private static ZoneId zoneId; // = ZoneId.of(timeZoneId);

	public static ZoneId getZoneId() {
		if(zoneId==null) {
			zoneId = ZoneId.of(ComUtils.getContext().getEnvironment().getProperty("ocpp.server.timezone", "Asia/Seoul"));
		}
		return zoneId;
	}

}
