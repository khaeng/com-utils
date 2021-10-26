package com.itcall.util.date;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

/**
 * Model/Entity에서 외부로 전송(문자열 형식으로)할때
 * @JsonSerialize(using = JsonDateTimeSerializer.class)
 * 이용하여 날자패턴을 동일하게 맞춘다.
 * @author khaeng@nate.com
 *
 */
public class JsonDateTimeSerializer extends JsonSerializer<LocalDateTime> {

	private static final String DATE_PATTERN_YMD_T_HMS_S = "yyyy-MM-dd'T'HH:mm:ss.SSS";
	private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern(DATE_PATTERN_YMD_T_HMS_S);

	@Override
	public void serialize(LocalDateTime date, JsonGenerator generator, SerializerProvider arg)
			throws IOException, JsonProcessingException {
		// DateTimeFormatter formatter =
		// DateTimeFormatter.ofPattern(DATE_PATTERN_YMD_T_HMS_S);
		String dateString = date.format(formatter/* ISO_OFFSET_DATE_TIME */);
//		dateString = date.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
//		dateString = date.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
		generator.writeString(dateString);
	}
}