package com.itcall.util.date;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.node.TextNode;

/**
 * Model/Entity로 외부에서 입력해서 들어올때
 * @JsonDeserialize(using = JsonDateTimeDeserializer.class)
 * 이용하여 날자패턴을 동일하게 맞춘다.
 * @author khaeng@nate.com
 *
 */
public class JsonDateTimeDeserializer extends JsonDeserializer<LocalDateTime> {
	private static final String DATE_PATTERN_YMD_HMS_S = "yyyy-MM-dd HH:mm:ss.SSS";
	private static final String DATE_PATTERN_YMD_T_HMS_S = "yyyy-MM-dd'T'HH:mm:ss.SSS";
	private static final DateTimeFormatter isoFormatter = new DateTimeFormatterBuilder()
			.append(DateTimeFormatter.ISO_LOCAL_DATE_TIME).appendOffset("+HHMM", "+0000").toFormatter();
	private static final DateTimeFormatter localFormatter = new DateTimeFormatterBuilder()
			.append(DateTimeFormatter.ISO_LOCAL_DATE_TIME).toFormatter();
	private static final DateTimeFormatter isoSpcFormatter = new DateTimeFormatterBuilder()
			.append(DateTimeFormatter.ofPattern(DATE_PATTERN_YMD_HMS_S)).appendOffset("+HHMM", "+0000").toFormatter();
	private static final DateTimeFormatter localSpcFormatter = new DateTimeFormatterBuilder()
			.append(DateTimeFormatter.ofPattern(DATE_PATTERN_YMD_HMS_S)).toFormatter();

	@Override
	public LocalDateTime deserialize(JsonParser jp, DeserializationContext ctxt)
			throws IOException, JsonProcessingException {
		ObjectCodec oc = jp.getCodec();
		TextNode node = (TextNode) oc.readTree(jp);
		String dateString = node.textValue();
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern(DATE_PATTERN_YMD_T_HMS_S);
		// 입력되는 패턴에 맞게 결정한다.
		if(dateString.matches("\\d{4}-(0[1-9]|1[012])-(0[1-9]|[12][0-9]|3[01])T(0[0-9]|1[0-9]|2[0-3]):([0-5][0-9]):([0-5][0-9]).\\d{3}")) {
			formatter = localFormatter;
		} else if(dateString.matches("\\d{4}-(0[1-9]|1[012])-(0[1-9]|[12][0-9]|3[01])T(0[0-9]|1[0-9]|2[0-3]):([0-5][0-9]):([0-5][0-9]).\\d{3}[+]\\d{4}")) {
			formatter = isoFormatter;
		} else if(dateString.matches("\\d{4}-(0[1-9]|1[012])-(0[1-9]|[12][0-9]|3[01]) (0[0-9]|1[0-9]|2[0-3]):([0-5][0-9]):([0-5][0-9]).\\d{3}")) {
			formatter = localSpcFormatter;
		} else if(dateString.matches("\\d{4}-(0[1-9]|1[012])-(0[1-9]|[12][0-9]|3[01]) (0[0-9]|1[0-9]|2[0-3]):([0-5][0-9]):([0-5][0-9]).\\d{3}[+]\\d{4}")) {
			formatter = isoSpcFormatter;
		}
		return LocalDateTime.parse(dateString, formatter /*ISO_LOCAL_DATE_TIME*/ /*ISO_OFFSET_DATE_TIME*/);
	}
}