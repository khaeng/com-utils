package com.itcall.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.Charset;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.sql.DataSource;

import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.ProtocolException;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.protocol.HttpContext;
import org.apache.http.ssl.SSLContexts;
import org.apache.http.ssl.TrustStrategy;
import org.springframework.boot.autoconfigure.web.reactive.function.client.WebClientAutoConfiguration;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.ApplicationContext;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.ResourceHttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.converter.support.AllEncompassingFormHttpMessageConverter;
import org.springframework.http.converter.xml.Jaxb2RootElementHttpMessageConverter;
import org.springframework.http.converter.xml.SourceHttpMessageConverter;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.core.JsonParser.Feature;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.itcall.util.support.RestTemplateInterceptor;
import com.itcall.util.support.SerializedObjectRedisCodec;
import com.itcall.util.worker.WorkerHandler;
import com.itcall.util.worker.WorkerJobInvoke;
import com.itcall.util.worker.WorkerRunnable;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.SetArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import reactor.netty.http.client.HttpClient;

@Slf4j
public class ComUtils {

	private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule())
			.configure(Feature.ALLOW_UNQUOTED_CONTROL_CHARS, true);

	public static final long ONE_SECOND_FOR_MS = 1000L;
	public static final long ONE_MINITE_FOR_MS = 60 * 1000;
	public static final long ONE_HOUR_FOR_MS = 60 * 60 * 1000;
	public static final long HALF_DAY_FOR_MS = 12 * 60 * 60 * 1000;
	public static final long ONE_DAY_FOR_MS = 24 * 60 * 60 * 1000;
	public static final long DEF_REDIS_KEEP_MILLIS = ONE_HOUR_FOR_MS;
	private static final String WORKER_APP_NAME = "skep_sw";
	public static final String WORKER_KEYS_PATTERN = "req,*,*,*,workerJob";
	public static final String WORKER_SYNCRONOISED_CHECK_KEY = "req,res,worker.dyno.synchronized.key";

	private static final long MAX_RESTTEMPLATE_UNIT = 10;

	private static final int CONNECTION_TIMEOUT_RESTCLIENT = 10;

	private static final int READTIMEOUT_RESTCLIENT = 60;

	private static final int MAX_CONN_TOTAL_RESTCLIENT = 50;

	private static final int MAX_CONN_PER_ROUTE_RESTCLIENT = 20;

	@Getter
	private static String myAppName = "common.util.base";
	@Getter
	private static String myAppNo = "base.1";
	@Getter
	private static ApplicationContext context;
	@Getter
	private static DataSource dataSource;
//	private static RedisTemplate<String, Object> redisTemplate; // SSL??? ???????????? ????????? ????????????.
	private static StatefulRedisConnection<String, Object> redisConnection;
	private static String envRedisUrl;
	private static TaskScheduler taskScheduler;
	private static TaskExecutor taskExecutor;

	private static RestTemplate[] arrRestTemplateClient;

	public static void setConstruct(String myAppName, ApplicationContext context, DataSource dataSource,
			StatefulRedisConnection<String, Object> redisConnection, String envRedisUrl, TaskScheduler taskScheduler,
			TaskExecutor taskExecutor) {
		ComUtils.myAppName = myAppName;
		context.getEnvironment().getProperty(Const.ENV_SERVER_APP_NO_KEY, myAppNo);
		ComUtils.context = context;
		ComUtils.dataSource = dataSource;
		ComUtils.redisConnection = redisConnection;
		ComUtils.envRedisUrl = envRedisUrl;
		ComUtils.taskScheduler = taskScheduler;
		ComUtils.taskExecutor = taskExecutor;
		if (WORKER_APP_NAME.equals(myAppName)) {
			taskScheduler.scheduleWithFixedDelay(new WorkerJobInvoke(context), ONE_SECOND_FOR_MS / 10);
		}
	}

	/*********************************************************************
	 * Redis Connection??? ????????????. ??????????????? ?????? Open?????? ????????????.
	 *
	 * @return redisConnection
	 */
	public static StatefulRedisConnection<String, Object> getRedisConn() {
		if (!redisConnection.isOpen()) {
			log.error("Redis Server Connection is already closed... Try to reConnect...");
			redisConnection = redisConnection();
		}
		return redisConnection;
	}

	/****************
	 * Application ?????? Prefix??? ?????? Group????????? ?????? ??????.
	 **********************/
	private static String getKey(String key) {
		return new StringBuffer(myAppName).append(":").append(key).toString();
	}

	/**
	 * ????????? ?????? ?????? ????????? ??? ??????. ????????? "*"??? ?????? ???????????? like????????? ???????????????, null??? ????????? "*"??? ????????????. ???,
	 * ????????? ?????? Keys??? ?????????.
	 *
	 * @param pattern
	 * @return
	 */
	public static List<String> getKeys(String pattern) {
		return getRedisConn().sync().keys(pattern == null || pattern.trim().isEmpty() ? "*" : pattern);
	}

	/*********** Redis ?????? Reset ************/
	public static void resetRedis() {
		getRedisConn().sync().reset();
	}

	/*********** Redis ????????????(???) ?????? ************/
	public static long delRedis(String... key) {
		if (key == null)
			return 0;
		String[] keys = new String[key.length];
		for (int i = 0; i < keys.length; i++) {
			keys[i] = getKey(key[i]);
		}
		// Arrays.stream(key).forEach(k -> k = getKey(k)); // key = myAppName+":"+key;
		return getRedisConn().sync().del(keys);
	}

	/*********** Redis ?????? Object??? ???????????? ************/
	public static Object getRedis(String key) {
		key = getKey(key);
		return getRedisConn().sync().get(key);
	}

	/*********** Redis ?????? Object??? Class??? ???????????? ************/
	public static <T> T getRedis(String key, Class<T> c) {
		return c.cast(getRedis(key));
	}

	/*********** Redis Object??? ????????????. ????????????????????? ?????????. ????????? 1??????. ************/
	public static void setRedis(String key, Object value) {
		setRedis(key, value, DEF_REDIS_KEEP_MILLIS);
	}

	/*********** Redis Object??? ????????????(Milliseconds)??? ???????????? ???????????? ************/
	public static void setRedis(String key, Object value, long millis) {
		key = getKey(key);
		log.info("Redis to set key[{}], timeout[{}] is result to [{}]", key, millis,
				getRedisConn().sync().set(key, value, SetArgs.Builder.px(millis)));
	}

	/*********** Redis ???????????? - ??????????????? ????????? ??????. ************/
	public static StatefulRedisConnection<String, Object> redisConnection() {
		log.warn("StatefulRedisConnection to Retry...");
		RedisURI redisURI = RedisURI.create(envRedisUrl/* System.getenv("REDIS_URL") */);
		redisURI.setVerifyPeer(false);

		RedisClient redisClient = RedisClient.create(redisURI);
		SerializedObjectRedisCodec serializedObjectCodec = new SerializedObjectRedisCodec();
		return redisClient.connect(serializedObjectCodec);
	}

	public static void pushWorkerService(String beanName, Map<String, Object> param, final WorkerHandler handler) {
		pushWorkerService(beanName, param, handler, DEF_REDIS_KEEP_MILLIS);
	}

	public static void pushWorkerService(String beanName, Map<String, Object> param, final WorkerHandler handler,
			long millis) {
		// ???req,<appName>,<beanName>,<System.currentTimeMillis()>???
		// ???res,<appName>,<beanName>,<System.currentTimeMillis()>???
		String // reqKey = new
				// StringBuffer().append("req,").append(myAppName).append(",").append(beanName).append(",").append(System.currentTimeMillis()).append(",workerJob").toString();
		reqKey = WORKER_KEYS_PATTERN.replaceFirst("[*]", myAppName).replaceFirst("[*]", beanName).replaceFirst("[*]",
				String.valueOf(System.currentTimeMillis()));
		getRedisConn().sync().set(reqKey, param, SetArgs.Builder.px(millis));
//		taskExecutor.execute(()->{
//
//		});
//		final ScheduledFuture<?> future = taskScheduler.scheduleWithFixedDelay(()->{
//			// cancel(true);
//		}, 5 * 1000L);

		if (!StringUtils.isEmpty(handler)) {
			// Handler??? ?????? ?????? ????????? ????????????. ??????????????? ????????? ???????????? ?????????. bean????????? ????????? ?????? ????????? ??????.
			new WorkerRunnable(reqKey.replaceFirst("req,", "res."), handler, millis).executeSchedule(taskScheduler,
					5 * 1000L);
		}

	}

	public static String getProperty(String key) {
		return getProperty(key, null);
	}

	public static String getProperty(String key, String defaultValue) {
		String result = context.getEnvironment().getProperty(key, System.getProperty(key, System.getenv(key)));
		if (StringUtils.isEmpty(result))
			result = defaultValue;
		return result;
	}

	/**************************************************************************************
	 * Get All Exception Message from All Throwables...
	 *
	 * @param throwable
	 * @return
	 **************************************************************************************/
	public static String getErrMsg(Throwable t) {
		return getErrMsg(t, new StringBuilder(), 0).toString();
	}

	private static StringBuilder getErrMsg(Throwable t, StringBuilder sb, int depth) {
		if (t != null) {
			sb.append("[").append(depth).append("]:").append(t.getClass().getName()).append(": ")
					.append(t.getMessage());
			if (t.getCause() != null)
				getErrMsg(t.getCause(), sb.append("\n"), depth + 1);
		}
		return sb;
	}

	/******************************************************************************************
	 * verifyData??? ????????? ?????? [JAVA] String interfaceId = "prefix??? ???????????? ???"; String now =
	 * Long.toString(System.currentTimeMillis()); String merge = new
	 * StringBuffer().append(interfaceId).append(now).append("||").append("????????????
	 * Dummy ?????? ???????????? ?????? ????????????...").toString(); String verification =
	 * Base64.getEncoder().encodeToString(merge.getBytes(Charset.forName("UTF-8")));
	 * [Javascript] var interfaceId = "prefix??? ???????????? ???"; var now = new
	 * Date().getTime(); var merge = interfaceId + now + "||" + encodeURI("??????????????? ?????????
	 * ???????????? ???, ????????? ?????????..."); var verification = btoa(merge); // ??????????????????????????? ?????????????????? ?????????
	 * ?????? ???????????????URI-Encoding?????? ????????? ??????.
	 *
	 * Decoding ??? long??? ????????? ??????????????? ?????? UTC??? ZoneId.of("Asia/Seoul")??? ????????????.
	 *
	 * @param verifyData   - Encoding ?????????.
	 * @param limitMinutes - ????????? ??????(???) - 5?????? 5????????? ????????? ???????????? true??? ?????????.
	 * @param prefix       - ?????? InterfaceId??? ????????????.
	 * @return
	 */
	public static boolean isOkOnTimeApi(String verifyData, int limitMinutes, String prefix) {
		return isOkOnTimeApi(verifyData, limitMinutes, prefix, Const.getZoneId());
	}

	/******************************************************************************************
	 * verifyData??? ????????? ?????? [JAVA] String interfaceId = "prefix??? ???????????? ???"; String now =
	 * Long.toString(System.currentTimeMillis()); String merge = new
	 * StringBuffer().append(interfaceId).append(now).append("||").append("????????????
	 * Dummy ?????? ???????????? ?????? ????????????...").toString(); String verification =
	 * Base64.getEncoder().encodeToString(merge.getBytes(Charset.forName("UTF-8")));
	 * [Javascript] var interfaceId = "prefix??? ???????????? ???"; var now = new
	 * Date().getTime(); var merge = interfaceId + now + "||" + encodeURI("??????????????? ?????????
	 * ???????????? ???, ????????? ?????????..."); var verification = btoa(merge); // ??????????????????????????? ?????????????????? ?????????
	 * ?????? ???????????????URI-Encoding?????? ????????? ??????.
	 *
	 * @param verifyData   - Encoding ?????????.
	 * @param limitMinutes - ????????? ??????(???) - 5?????? 5????????? ????????? ???????????? true??? ?????????.
	 * @param prefix       - ?????? InterfaceId??? ????????????.
	 * @param zoneId       - ????????? ?????? ????????????. ZoneId.of("Asia/Seoul")
	 * @return
	 */
	public static boolean isOkOnTimeApi(String verifyData, int limitMinutes, String prefix, ZoneId zoneId) {
		String decodeVerification = new String(Base64.getDecoder().decode(verifyData), Charset.forName("UTF-8"));
		String reqKey = decodeVerification.split("[||]", 2)[0];
		long reqMilli = Long.parseLong(reqKey.substring(prefix.length()));
		return isOkOn(reqMilli, limitMinutes, zoneId);
	}

	public static boolean isOkOn(long reqMilli, int limitMinutes) {
		return isOkOn(reqMilli, limitMinutes, Const.getZoneId());
	}

	public static boolean isOkOn(long reqMilli, int limitMinutes, ZoneId zoneId) {
//		Date date = Date.from(zonedDateTime.toInstant());
//		date.getTime();
//		zonedDateTime.toInstant().toEpochMilli();
//		System.currentTimeMillis();
//
//		String interfaceId = "/Api/Test/Prefix/AddrTest";
//		String now = Long.toString(System.currentTimeMillis());
//		String merge = new StringBuffer().append(interfaceId).append(now).append("||").append("???????????? Dummy ?????? ???????????? ?????? ????????????...").toString();
//		String verification = Base64.getEncoder().encodeToString(merge.getBytes(Charset.forName("UTF-8")));
//
//		String decodeVerification = new String(Base64.getDecoder().decode(verification), Charset.forName("UTF-8"));
//		String reqKey = decodeVerification.split("||", 2)[0];
//		String reqTime = reqKey.substring(prefix.length());
//		long reqMilli = Long.parseLong(reqKey.substring(prefix.length()));
//
//		long reqMilli = zonedDateTime.toInstant().toEpochMilli();
		long nowSeconds = ZonedDateTime.now(zoneId).toInstant().toEpochMilli();
		long minMilli = nowSeconds - limitMinutes * 60 * 1000;
		long maxMilli = minMilli + limitMinutes * 60 * 1000 * 2;
		return minMilli <= reqMilli && reqMilli <= maxMilli;
	}

//	public static void main(String[] args) {
//		String interfaceId = "/Api/Test/Prefix/AddrTest";
//		String now = Long.toString(System.currentTimeMillis());
//		String merge = new StringBuffer().append(interfaceId).append(now).append("||").append("???????????? Dummy ?????? ???????????? ?????? ????????????...").toString();
//		String verification = Base64.getEncoder().encodeToString(merge.getBytes(Charset.forName("UTF-8")));
//		System.out.println(verification);
//
//		// btoa('/Api/Soap/CSMS/SKVIEW/EventReserve/One' + new Date().getTime() + '||')
//		String test_2021_10_25_14_10 = "L0FwaS9Tb2FwL0NTTVMvU0tWSUVXL0V2ZW50UmVzZXJ2ZS9PbmUxNjM1Mzk3Nzk3MDAxfHw=";
//
//		String test_2021_10_28_14_14 = "L0FwaS9UZXN0L1ByZWZpeC9BZGRyVGVzdDE2MzUzOTgwNTU0ODF8fOu2iO2VhOyalO2VnCBEdW1teSDrmJDripQg7KCE64us67Cc6rOgIOyLtuydgCDrjbDsnbTthLDrk6QuLi4=";
//		String test_2021_10_28_14_15 = "L0FwaS9UZXN0L1ByZWZpeC9BZGRyVGVzdDE2MzUzOTgxMjI5Nzl8fOu2iO2VhOyalO2VnCBEdW1teSDrmJDripQg7KCE64us67Cc6rOgIOyLtuydgCDrjbDsnbTthLDrk6QuLi4=";
//		String test_2021_10_28_14_16 = "L0FwaS9UZXN0L1ByZWZpeC9BZGRyVGVzdDE2MzUzOTgxNjcwMzd8fOu2iO2VhOyalO2VnCBEdW1teSDrmJDripQg7KCE64us67Cc6rOgIOyLtuydgCDrjbDsnbTthLDrk6QuLi4=";
//		String test_2021_10_28_14_17 = "L0FwaS9UZXN0L1ByZWZpeC9BZGRyVGVzdDE2MzUzOTgyNTQ1Mjh8fOu2iO2VhOyalO2VnCBEdW1teSDrmJDripQg7KCE64us67Cc6rOgIOyLtuydgCDrjbDsnbTthLDrk6QuLi4=";
//		String test_2021_10_28_14_18 = "L0FwaS9UZXN0L1ByZWZpeC9BZGRyVGVzdDE2MzUzOTgzMjAyMTh8fOu2iO2VhOyalO2VnCBEdW1teSDrmJDripQg7KCE64us67Cc6rOgIOyLtuydgCDrjbDsnbTthLDrk6QuLi4=";
//		String test_2021_10_28_14_19 = "L0FwaS9UZXN0L1ByZWZpeC9BZGRyVGVzdDE2MzUzOTg3MDg2Njl8fOu2iO2VhOyalO2VnCBEdW1teSDrmJDripQg7KCE64us67Cc6rOgIOyLtuydgCDrjbDsnbTthLDrk6QuLi4=";
//
//		System.out.println(isOkOnTimeApi(test_2021_10_25_14_10, 5, "/Api/Soap/CSMS/SKVIEW/EventReserve/One", Const.getZoneId()));
//		System.out.println(isOkOnTimeApi(verification, 5, interfaceId, Const.getZoneId()));
//		System.out.println();
//		System.out.println(isOkOnTimeApi(test_2021_10_28_14_14, 5, interfaceId, Const.getZoneId()));
//		System.out.println(isOkOnTimeApi(test_2021_10_28_14_15, 5, interfaceId, Const.getZoneId()));
//		System.out.println(isOkOnTimeApi(test_2021_10_28_14_16, 5, interfaceId, Const.getZoneId()));
//		System.out.println(isOkOnTimeApi(test_2021_10_28_14_17, 5, interfaceId, Const.getZoneId()));
//		System.out.println(isOkOnTimeApi(test_2021_10_28_14_18, 5, interfaceId, Const.getZoneId()));
//		System.out.println(isOkOnTimeApi(test_2021_10_28_14_19, 5, interfaceId, Const.getZoneId()));
//		System.out.println();
//
//		System.out.println(isOkOnTimeApi(test_2021_10_28_14_14, 5, interfaceId, ZoneId.systemDefault()));
//		System.out.println(isOkOnTimeApi(test_2021_10_28_14_15, 5, interfaceId, ZoneId.systemDefault()));
//		System.out.println(isOkOnTimeApi(test_2021_10_28_14_16, 5, interfaceId, ZoneId.systemDefault()));
//		System.out.println(isOkOnTimeApi(test_2021_10_28_14_17, 5, interfaceId, ZoneId.systemDefault()));
//		System.out.println(isOkOnTimeApi(test_2021_10_28_14_18, 5, interfaceId, ZoneId.systemDefault()));
//		System.out.println(isOkOnTimeApi(test_2021_10_28_14_19, 5, interfaceId, ZoneId.systemDefault()));
//		System.out.println();
//		System.out.println(isOkOnTimeApi(test_2021_10_28_14_14, 5, interfaceId, ZoneId.of("America/Chicago")));
//		System.out.println(isOkOnTimeApi(test_2021_10_28_14_15, 5, interfaceId, ZoneId.of("America/Chicago")));
//		System.out.println(isOkOnTimeApi(test_2021_10_28_14_16, 5, interfaceId, ZoneId.of("America/Chicago")));
//		System.out.println(isOkOnTimeApi(test_2021_10_28_14_17, 5, interfaceId, ZoneId.of("America/Chicago")));
//		System.out.println(isOkOnTimeApi(test_2021_10_28_14_18, 5, interfaceId, ZoneId.of("America/Chicago")));
//		System.out.println(isOkOnTimeApi(test_2021_10_28_14_19, 5, interfaceId, ZoneId.of("America/Chicago")));
//
//		System.out.println(System.currentTimeMillis());
//		long nowSeconds = ZonedDateTime.now(ZoneId.of("Europe/Paris")).toInstant().toEpochMilli();
//		long nowSeconds1 = ZonedDateTime.now(ZoneId.of("America/Chicago")).toInstant().toEpochMilli();
//		long nowSeconds2 = ZonedDateTime.now(Const.getZoneId()).toInstant().toEpochMilli();
//		long nowSeconds3 = ZonedDateTime.now().toInstant().toEpochMilli();
//		System.out.println(nowSeconds);
//		System.out.println(nowSeconds1);
//		System.out.println(nowSeconds2);
//		System.out.println(nowSeconds3);
//		System.out.println(ZonedDateTime.now(ZoneId.of("Europe/Paris")));
//		System.out.println(ZonedDateTime.now(ZoneId.of("America/Chicago")));
//		System.out.println(ZonedDateTime.now());
//		System.out.println(OffsetDateTime.now(ZoneId.of("Europe/Paris")));
//		System.out.println(OffsetDateTime.now(ZoneId.of("America/Chicago")));
//		System.out.println(OffsetDateTime.now());
//
//		nowSeconds = OffsetDateTime.now(ZoneId.of("Europe/Paris")).toInstant().toEpochMilli();
//		nowSeconds1 = OffsetDateTime.now(ZoneId.of("America/Chicago")).toInstant().toEpochMilli();
//		nowSeconds2 = OffsetDateTime.now(Const.getZoneId()).toInstant().toEpochMilli();
//		nowSeconds3 = OffsetDateTime.now().toInstant().toEpochMilli();
//		System.out.println(nowSeconds);
//		System.out.println(nowSeconds1);
//		System.out.println(nowSeconds2);
//		System.out.println(nowSeconds3);
//
//
//		String columnDefinition = "character varying(20) NOT NULL CHECK (status in ('Accepted', 'Blocked', 'Expired', 'Invalid', 'ConcurrentTx'))";
//		System.out.println(columnDefinition);
//		System.out.println(STATUS_CHECK_STRING);
//		columnDefinition = "character varying(20) NOT NULL CHECK (status in ('"
//					+ Arrays.stream(AuthorizationStatus.values())
//							.map(AuthorizationStatus::toString).collect(Collectors.joining("', '"))
//					+ "'))";
//		System.out.println(columnDefinition);
//	}
//	private static final String STATUS_CHECK_STRING = Arrays.asList(AuthorizationStatus.values()).toString();
//
//	public enum AuthorizationStatus {
//		Accepted, Blocked, Expired, Invalid, ConcurrentTx;
//	}

	/**
	 * ?????? ????????? ?????? ??????????????? ???????????? ?????? ????????? ????????? PID??? ????????????.
	 *
	 * @param port
	 * @return
	 */
	public static int getPIDforRunningPort(int port) {
		Process process = null;
		boolean isWin = !System.getProperty("file.separator", "/").equals("/");
		try {
			process = Runtime.getRuntime().exec(String.format(isWin ? "netstat -ano | findstr :%d"
					: "netstat -nap | grep \":%d \" | grep -vE \"LISTENING|_WAIT|ESTABLISHED\"", port));
			BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()));
			String readLine = null;
			while ((readLine = br.readLine()) != null) {
				return Integer.parseInt(readLine.split(isWin ? "LISTENING" : "LISTEN", 2)[1].trim());
			}
		} catch (Exception e) {
			log.debug("Find failure allready Running Port[{}]...", port, e.getMessage());
		} finally {
			if (process != null)
				process.destroy();
		}
		return 0;
	}

	/**
	 * ?????? PID??? ??????????????? ???????????????. ???????????? ???????????? ????????????. ???????????? ?????? ?????? kill -9 <PID> ?????? ??? Shell??? ?????????
	 * ????????????.
	 *
	 * @param pid
	 * @return
	 * @throws IOException
	 */
	public static String killProcess(int pid) throws IOException {
		boolean isWin = !System.getProperty("file.separator", "/").equals("/");
		return new BufferedReader(new InputStreamReader(Runtime.getRuntime()
				.exec(String.format(isWin ? "taskkill /PID %d /F" : "kill -9 %d", pid)).getInputStream(),
				Charset.defaultCharset())).lines()
						.collect(Collectors.joining(System.getProperty("line.separator", "\n")));
		// Runtime.getRuntime().exec(String.format(isWin ? "taskkill /PID %d /F" : "kill
		// -9 %d", pid));
	}

	/**
	 * ObjectMapper??? ????????????, ??????Object?????? ??????Object??? ???????????? ????????????. ???????????? ???????????????, ??????????????? ????????????...
	 * readValue/writeValue??? ???????????? ??????.
	 *
	 * @param           <T>
	 * @param fromValue
	 * @param t
	 * @return
	 */
	public static <T> T convertValue(Object fromValue) {
		T target = MAPPER.convertValue(fromValue, new TypeReference<T>() {
		});
		return target;
	}

	public static <T> T convertValue(Object fromValue, Class<T> t) {
		T target = MAPPER.convertValue(fromValue, t);
		return target;
	}

//	public static <T> List<T> convertValue(Object fromValue) {
//		// T target = (T) fromValue;
//		List<T> target = MAPPER.convertValue(fromValue, new TypeReference<List<T>>() {});
//		return target;
//	}
	public static <T> T readValue(String fromValue) throws JsonMappingException, JsonProcessingException {
		T target = MAPPER.readValue(fromValue, new TypeReference<T>() {
		});
		return target;
	}

	public static <T> T readValue(String fromValue, Class<T> t) throws JsonMappingException, JsonProcessingException {
		T target = MAPPER.readValue(fromValue, t);
		return target;
	}

	public static String writeValue(Object fromValue) throws JsonProcessingException {
		return toString(fromValue);
	}

	public static String toString(Object fromValue) throws JsonProcessingException {
		try {
			return MAPPER.writeValueAsString(fromValue);
		} catch (JsonProcessingException e) {
			log.error("Cannot parsing Object using ObjectMapper.writeValueAsString[{}], error[{}]{}", fromValue,
					getErrMsg(e), e);
			throw e;
		}
	}

	/**
	 * SHELL laucher Command
	 *
	 * @param command
	 * @param consoleResultCallBack - call-by-reference result body
	 * @return - false is error.
	 */
	public static boolean laucherCommandConsole(StringBuilder consoleResultCallBack, String... command) {
		boolean result = false;
		Process process = null;
		try {
			process = Runtime.getRuntime().exec(command);
			BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
			String cmdResultBody = reader.lines().collect(Collectors.joining(System.getProperty("line.separator")));
			Map<String, Object> cmdResultMap = ComUtils.readValue(cmdResultBody);
			if (cmdResultMap.getOrDefault("exeResult", "done").toString().equals("error") == false) {
				consoleResultCallBack.append(cmdResultBody);
				result = true;
				log.info("Result[{}], Command[{}], ResultBody[{}]", result, String.join(" ", command), cmdResultBody);
			} else {
				log.warn("Result[{}], Command[{}], ResultBody[{}]", result, String.join(" ", command), cmdResultBody);
			}
		} catch (IOException e) {
			log.error("Error[{}], Command[{}]{}", ComUtils.getErrMsg(e), String.join(" ", command), e);
		}
		return result;
	}

	/**
	 * Download file from url
	 *
	 * @param downloadLocation
	 * @return - save to file-full-path
	 * @throws IOException
	 */
	public static String downloadFile(String downloadLocation) throws IOException {
		return downloadFile(downloadLocation, File.createTempFile("ocpp-", "tmp").getAbsolutePath());
	}

	/**
	 *
	 * @param downloadLocation
	 * @param saveFilePath
	 * @return
	 * @throws IOException
	 */
	public static String downloadFile(String downloadLocation, String saveFilePath) throws IOException {
		URL website = new URL(downloadLocation);
		try (ReadableByteChannel rbc = Channels.newChannel(website.openStream());
				FileOutputStream fos = new FileOutputStream(saveFilePath);) {
			fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
		}
		return saveFilePath;
	}

	/**
	 * Bytes to HexString
	 * @param buf
	 * @return
	 */
	public static String getHexString(byte[] buf) {
		return new java.math.BigInteger(buf).toString(16);
	}

	/**
	 * HexString to Bytes
	 * @param hexString
	 * @return
	 */
	public static byte[] getBytesFromHexString(String hexString) {
		return new java.math.BigInteger(hexString, 16).toByteArray();
	}

	public static RestTemplate restTemplate(final int maxTotal, final int defaultMaxPerRoute, final int connectTimeout,
			final int readTimeout, final String userAgent) {
		final Registry<ConnectionSocketFactory> schemeRegistry = RegistryBuilder.<ConnectionSocketFactory>create()
				.register("http", PlainConnectionSocketFactory.getSocketFactory())
				.register("https", SSLConnectionSocketFactory.getSocketFactory()).build();

		final PoolingHttpClientConnectionManager connManager = new PoolingHttpClientConnectionManager(schemeRegistry);
		connManager.setMaxTotal(maxTotal);
		connManager.setMaxPerRoute(new HttpRoute(HttpHost.create("routHost")), 20); // Route??? ???????????? ?????????.
		connManager.setDefaultMaxPerRoute(defaultMaxPerRoute);

		final CloseableHttpClient httpClient = HttpClients.custom().setConnectionManager(connManager)
				.setUserAgent(userAgent)
				.setDefaultRequestConfig(
						RequestConfig.custom()
						.setConnectTimeout(connectTimeout)
						.setSocketTimeout(readTimeout)
						.setExpectContinueEnabled(false).build())
				.build();

		return new RestTemplateBuilder().requestFactory(() -> new HttpComponentsClientHttpRequestFactory(httpClient))
				.build();
	}

	public static RestTemplate restTemplate(boolean isLogging) {
		int indexRestTemplate = (int) (System.currentTimeMillis() % MAX_RESTTEMPLATE_UNIT);
		if (arrRestTemplateClient[indexRestTemplate] != null) return arrRestTemplateClient[indexRestTemplate];
		HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();
		factory.setConnectTimeout(CONNECTION_TIMEOUT_RESTCLIENT * 1000);
		factory.setReadTimeout(READTIMEOUT_RESTCLIENT * 1000);
		factory.setHttpClient(getHttpClientWithSSL(null, new String[]{"SSLv3","TLSv1","TLSv1.1","TLSv1.2"}, isLogging)); // support SSL
		BufferingClientHttpRequestFactory bufferingClientHttpRequestFactory = new BufferingClientHttpRequestFactory(
				factory);
		RestTemplate restTemplate = new RestTemplate(bufferingClientHttpRequestFactory);
		if (isLogging) {
			List<ClientHttpRequestInterceptor> interceptors = new ArrayList<ClientHttpRequestInterceptor>();
			interceptors.add(new RestTemplateInterceptor(null, isLogging));
			restTemplate.setInterceptors(interceptors);
		}
		return arrRestTemplateClient[indexRestTemplate] = withMessageConverters(restTemplate, Charset.forName("UTF-8"));
	}

	private static CloseableHttpClient getHttpClientWithSSL(RestTemplateInterceptor restTemplateInterceptor, String[] arrProtocal, boolean isLogging) {
		CloseableHttpClient httpClient = null;
		try {
			TrustStrategy trustStrategy = (X509Certificate[] chain, String authType) -> true;

			final SSLContext sslContext = SSLContexts.custom() // .useProtocol("TLSv1.2") // TLSv1, TLSv1.1, TLSv1.2, SSL, TLS
					.loadTrustMaterial(null, trustStrategy).build();

			RequestConfig config = RequestConfig.custom()
					.setConnectTimeout(CONNECTION_TIMEOUT_RESTCLIENT * 1000)
					.setConnectionRequestTimeout(CONNECTION_TIMEOUT_RESTCLIENT * 1000)
					.setSocketTimeout(READTIMEOUT_RESTCLIENT * 1000).build();
/*************************************************************
// Dummy protocol version value for invalid SSLSession
final static ProtocolVersion NONE = new ProtocolVersion(-1, "NONE");
// If enabled, send/ accept SSLv2 hello messages
final static ProtocolVersion SSL20Hello = new ProtocolVersion(0x0002, "SSLv2Hello");
// SSL 3.0
final static ProtocolVersion SSL30 = new ProtocolVersion(0x0300, "SSLv3");
// TLS 1.0
final static ProtocolVersion TLS10 = new ProtocolVersion(0x0301, "TLSv1");
// TLS 1.1
final static ProtocolVersion TLS11 = new ProtocolVersion(0x0302, "TLSv1.1");
// TLS 1.2
final static ProtocolVersion TLS12 = new ProtocolVersion(0x0303, "TLSv1.2");
*************************************************************/
			final SSLConnectionSocketFactory sslConnectionSocketFactory = new SSLConnectionSocketFactory(sslContext,
					arrProtocal, null, NoopHostnameVerifier.INSTANCE); // "SSLv3,TLSv1,TLSv1.1,TLSv1.2".split(","); // new String[]{"SSLv3","TLSv1","TLSv1.1","TLSv1.2"};

			LaxRedirectStrategy redirectStrategy = new LaxRedirectStrategy() {
				@Override
				public boolean isRedirected(HttpRequest request, HttpResponse response, HttpContext context) throws ProtocolException {
					// Redirected??? ????????? ??? ?????? ??????????????? ????????? ???????????? Redirect.URL??? ???????????? ?????????????????? ????????? ????????????.
					if(response.getStatusLine().getStatusCode()==HttpStatus.SC_MOVED_PERMANENTLY  // 301
							|| response.getStatusLine().getStatusCode()==HttpStatus.SC_MOVED_TEMPORARILY) { // 302 - ????????? 302??? ??????.
						if(isLogging) log.info("Call Status ::: " + response.getStatusLine());
						Header[] headers = response.getAllHeaders();
						for (int i = 0; i < headers.length; i++) {
							if(isLogging) log.info("HeaderInfo[" + headers[i].getName() + "] : " + headers[i].getValue());
						}
					}
					String setCookie = response.getFirstHeader(HttpHeaders.SET_COOKIE)!=null?response.getFirstHeader(HttpHeaders.SET_COOKIE).getValue():null;
					if(!StringUtils.isEmpty(setCookie)) {
						restTemplateInterceptor.setCookie(setCookie.substring(0, setCookie.indexOf(";")));
					}
					return super.isRedirected(request, response, context);
				}
			};

			httpClient = HttpClients.custom() // HttpClientBuilder.create()
					.setSSLSocketFactory(sslConnectionSocketFactory)
					.setDefaultRequestConfig(config)
					// .setHostnameVerifier(new AllowAllHostnameVerifier())
					.setRedirectStrategy(redirectStrategy)
					.setMaxConnTotal(MAX_CONN_TOTAL_RESTCLIENT)
					.setMaxConnPerRoute(MAX_CONN_PER_ROUTE_RESTCLIENT).build();

		} catch (NoSuchAlgorithmException | KeyManagementException | KeyStoreException e) {
			log.error("[{}]{}",ComUtils.getErrMsg(e));
		}
		return httpClient;
	}

	private static RestTemplate withMessageConverters(RestTemplate restTemplate, Charset charset) {
		if(charset==null) charset = Charset.forName("UTF-8");
		if(!StringUtils.isEmpty(restTemplate)) {
			List<HttpMessageConverter<?>> messageConverterList = new ArrayList<HttpMessageConverter<?>>();
			for (HttpMessageConverter<?> httpMessageConverter : restTemplate.getMessageConverters()) {
				if(!(httpMessageConverter instanceof AllEncompassingFormHttpMessageConverter))
					continue;

				List<HttpMessageConverter<?>> partConverterList = new ArrayList<HttpMessageConverter<?>>();
				partConverterList.add(new ByteArrayHttpMessageConverter());
				StringHttpMessageConverter stringHttpMessageConverter = new StringHttpMessageConverter(charset);
				stringHttpMessageConverter.setWriteAcceptCharset(false);
				partConverterList.add(stringHttpMessageConverter);
				partConverterList.add(new ResourceHttpMessageConverter());
				partConverterList.add(new SourceHttpMessageConverter<>());
				if(ClassUtils.isPresent("javax.xml.bind.Binder", AllEncompassingFormHttpMessageConverter.class.getClassLoader())) {
					partConverterList.add(new Jaxb2RootElementHttpMessageConverter());
				}
				if(ClassUtils.isPresent("com.fasterxml.jackson.databind.ObjectMapper", AllEncompassingFormHttpMessageConverter.class.getClassLoader())) {
					partConverterList.add(new MappingJackson2HttpMessageConverter());
				} else if(ClassUtils.isPresent("org.codehaus.jackson.map.ObjectMapper",  AllEncompassingFormHttpMessageConverter.class.getClassLoader())
						&& ClassUtils.isPresent("org.codehaus.jackson.JsonGenerator",  AllEncompassingFormHttpMessageConverter.class.getClassLoader())) {
					// partConverterList.add(new MappingJacksonHttpMessageConverter());
					partConverterList.add(new MappingJackson2HttpMessageConverter());
				}

				((AllEncompassingFormHttpMessageConverter) httpMessageConverter).setPartConverters(partConverterList);
				((AllEncompassingFormHttpMessageConverter) httpMessageConverter).setCharset(charset);
				((AllEncompassingFormHttpMessageConverter) httpMessageConverter).setMultipartCharset(charset);
				messageConverterList.add(httpMessageConverter);
			}
			restTemplate.setMessageConverters(messageConverterList); // ?????? .setPartConverters(partConverterList);?????? ??????????????????, ?????? ????????? set????????????.
		}
		return restTemplate;
	}

	public static WebClient webClient(String apiBaseUrl) {
		HttpClient httpClient = HttpClient.create().secure(t -> {
			try {
				t.sslContext(SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).build());
			} catch (SSLException e) {
				log.error("SSLException\n", e);
			}
		});
		return WebClient.builder().baseUrl(apiBaseUrl)
				.clientConnector(new ReactorClientHttpConnector(httpClient)).build();
	}


}
