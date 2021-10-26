/**
 * 
 */
package com.itcall.util.date.support;

import java.text.SimpleDateFormat;
import java.time.format.DateTimeFormatter;

/**
 * @author khaeng@nate.com
 *
 */
public enum DatetimePattern {

	YYYY_MM_DD(new SimpleDateFormat("yyyy-MM-dd"), "yyyy-MM-dd", "(19|20)\\d{2}-(0[1-9]|1[012])-(0[1-9]|[12][0-9]|3[01])"),
	YYYYMMDD(new SimpleDateFormat("yyyyMMdd"), "yyyyMMdd", "(19|20)\\d{2}(0[1-9]|1[012])(0[1-9]|[12][0-9]|3[01])"),
	HH_MM_SS(new SimpleDateFormat("HH:mm:ss"), "HH:mm:ss", "(0[0-9]|1[0-9]|2[0-3]):([0-5][0-9]):([0-5][0-9])"),
	HH_MM_SS_SSS(new SimpleDateFormat("HH:mm:ss.SSS"), "HH:mm:ss.SSS", "(0[0-9]|1[0-9]|2[0-3]):([0-5][0-9]):([0-5][0-9]).\\d{3}"),
	HHMMSS(new SimpleDateFormat("HHmmss"), "HHmmss", "(0[0-9]|1[0-9]|2[0-3])([0-5][0-9])([0-5][0-9])"),
	HHMMSSSSS(new SimpleDateFormat("HHmmssSSS"), "HHmmssSSS", "(0[0-9]|1[0-9]|2[0-3])([0-5][0-9])([0-5][0-9])\\d{3}"),
	YYYY_MM_DD_HH_MM_SS(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"), "yyyy-MM-dd HH:mm:ss", "(19|20)\\d{2}-(0[1-9]|1[012])-(0[1-9]|[12][0-9]|3[01]) (0[0-9]|1[0-9]|2[0-3]):([0-5][0-9]):([0-5][0-9])"),
	YYYY_MM_DDTHH_MM_SS(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss"), "yyyy-MM-dd'T'HH:mm:ss", "(19|20)\\d{2}-(0[1-9]|1[012])-(0[1-9]|[12][0-9]|3[01])T(0[0-9]|1[0-9]|2[0-3]):([0-5][0-9]):([0-5][0-9])"),
	YYYY_MM_DDTHH_MM_SSZ(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ"), "yyyy-MM-dd'T'HH:mm:ssZ", "(19|20)\\d{2}-(0[1-9]|1[012])-(0[1-9]|[12][0-9]|3[01])T(0[0-9]|1[0-9]|2[0-3]):([0-5][0-9]):([0-5][0-9])Z"),
	YYYY_MM_DD_HH_MM_SS_SSS(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS"), "yyyy-MM-dd HH:mm:ss.SSS", "(19|20)\\d{2}-(0[1-9]|1[012])-(0[1-9]|[12][0-9]|3[01]) (0[0-9]|1[0-9]|2[0-3]):([0-5][0-9]):([0-5][0-9]).\\d{3}"),
	YYYY_MM_DDTHH_MM_SS_SSS(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS"), "yyyy-MM-dd'T'HH:mm:ss.SSS", "(19|20)\\d{2}-(0[1-9]|1[012])-(0[1-9]|[12][0-9]|3[01])T(0[0-9]|1[0-9]|2[0-3]):([0-5][0-9]):([0-5][0-9]).\\d{3}"),
	YYYY_MM_DDTHH_MM_SS_SSSZ(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ"), "yyyy-MM-dd'T'HH:mm:ss.SSSZ", "(19|20)\\d{2}-(0[1-9]|1[012])-(0[1-9]|[12][0-9]|3[01])T(0[0-9]|1[0-9]|2[0-3]):([0-5][0-9]):([0-5][0-9]).\\d{3}Z"),
	YYYY_MM_DDTHH_MM_SS_SSS_GMTS(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX"), "yyyy-MM-dd'T'HH:mm:ss.SSS+SSSS", "(19|20)\\d{2}-(0[1-9]|1[012])-(0[1-9]|[12][0-9]|3[01])T(0[0-9]|1[0-9]|2[0-3]):([0-5][0-9]):([0-5][0-9])[+]\\d{4}"),
	YYYY_MM_DDTHH_MM_SS_SSSZ_GMTS(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZXXX"), "yyyy-MM-dd'T'HH:mm:ss.SSSZ+SSSS", "(19|20)\\d{2}-(0[1-9]|1[012])-(0[1-9]|[12][0-9]|3[01])T(0[0-9]|1[0-9]|2[0-3]):([0-5][0-9]):([0-5][0-9])Z[+]\\d{4}"),
	;
	private SimpleDateFormat parser;
	private String displayName;
	private String datetimePattern;

	DatetimePattern(SimpleDateFormat parser, String displayName, String datePattern) {
		this.parser = parser;
		this.displayName = displayName;
		this.datetimePattern = datePattern;
	}

	@Override
	public String toString() {
		return displayName;
	}

	public SimpleDateFormat getParser() {
		return this.parser;
	}

	public String getDisplayName() {
		return this.displayName;
	}

	public String getDatetimePattern() {
		return datetimePattern;
	}

	public DateTimeFormatter getFormatter() {
		// String pattern = "(?:19|20)[0-9]{2}-(?:(?:0[1-9]|1[0-2])-(?:0[1-9]|1[0-9]|2[0-8])|(?:(?!02)(?:0[1-9]|1[0-2])-(?:29|30))|(?:(?:0[13578]|1[02])-31))";
		// 입력 날짜의 포멧을 읽어서 가져가자.
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern(displayName);
		return formatter;
	}

	public static DatetimePattern getPattern(String datetimeString) {
		// GMT+8; GMT+08:00; UTC-08:00
		DateTimeFormatter.ISO_OFFSET_DATE_TIME.toString();
		for (DatetimePattern pattern : DatetimePattern.values()) {
			if(datetimeString.matches(pattern.getDatetimePattern())) {
				return pattern;
			}
		}
		return null;
	}
}
