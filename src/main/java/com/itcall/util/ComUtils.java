package com.itcall.util;

import java.nio.charset.Charset;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import org.springframework.context.ApplicationContext;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.util.StringUtils;

import com.itcall.util.support.SerializedObjectRedisCodec;
import com.itcall.util.worker.WorkerHandler;
import com.itcall.util.worker.WorkerJobInvoke;
import com.itcall.util.worker.WorkerRunnable;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.SetArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ComUtils {

	public static final long ONE_SECOND_FOR_MS = 1000L;
	public static final long ONE_MINITE_FOR_MS = 60 * 1000;
	public static final long ONE_HOUR_FOR_MS = 60 * 60 * 1000;
	public static final long HALF_DAY_FOR_MS = 12 * 60 * 60 * 1000;
	public static final long ONE_DAY_FOR_MS = 24 * 60 * 60 * 1000;
	public static final long DEF_REDIS_KEEP_MILLIS = ONE_HOUR_FOR_MS;
	private static final String WORKER_APP_NAME = "skep_sw";
	public static final String WORKER_KEYS_PATTERN = "req,*,*,*,workerJob";
	public static final String WORKER_SYNCRONOISED_CHECK_KEY = "req,res,worker.dyno.synchronized.key";
	
	@Getter
	private static String myAppName = "heroku.base";
	@Getter
	private static ApplicationContext context;
	@Getter
	private static DataSource dataSource;
//	private static RedisTemplate<String, Object> redisTemplate; // SSL를 제공하지 않아서 사용불가.
	private static StatefulRedisConnection<String,Object> redisConnection;
	private static String envRedisUrl;
	private static TaskScheduler taskScheduler;
	private static TaskExecutor taskExecutor;

	public static void setConstruct(String myAppName, ApplicationContext context, DataSource dataSource, StatefulRedisConnection<String,Object> redisConnection, String envRedisUrl, TaskScheduler taskScheduler, TaskExecutor taskExecutor) {
		ComUtils.myAppName = myAppName;
		ComUtils.context = context;
		ComUtils.dataSource = dataSource;
		ComUtils.redisConnection = redisConnection;
		ComUtils.envRedisUrl = envRedisUrl;
		ComUtils.taskScheduler = taskScheduler;
		ComUtils.taskExecutor = taskExecutor;
		if(WORKER_APP_NAME.equals(myAppName)) {
			taskScheduler.scheduleWithFixedDelay(new WorkerJobInvoke(context), ONE_SECOND_FOR_MS / 10);
		}
	}

	/*********************************************************************
	 * Redis Connection을 가져온다. 닫혀있으면 새로 Open해서 가져온다.
	 * @return redisConnection
	 */
	public static StatefulRedisConnection<String,Object> getRedisConn() {
		if(!redisConnection.isOpen()) {
			log.error("Redis Server Connection is already closed... Try to reConnect...");
			redisConnection = redisConnection();
		}
		return redisConnection;
	}

	/****************   Application 마다 Prefix를 두어 Group관리를 하게 한다. **********************/
	private static String getKey(String key) {
		return new StringBuffer(myAppName).append(":").append(key).toString();
	}

	/*********** Redis 작업 Reset ************/
	public static void resetRedis() {
		getRedisConn().sync().reset();
	}
	/*********** Redis 저장항목(들) 삭제 ************/
	public static long delRedis(String... key) {
		Arrays.stream(key).forEach(k -> k = getKey(k)); // key = myAppName+":"+key;
		return getRedisConn().sync().del(key);
	}
	/*********** Redis 저장 Object를 가져오기 ************/
	public static Object getRedis(String key) {
		key = getKey(key);
		return getRedisConn().sync().get(key);
	}
	/*********** Redis 저장 Object를 Class로 가져오기 ************/
	public static <T> Object getRedis(String key, Class<T> c) {
		return c.cast(getRedis(key));
	}
	/*********** Redis Object를 저장하기. 기본유지시각이 존재함. 기본값 1시간. ************/
	public static void setRedis(String key, Object value) {
		setRedis(key, value, DEF_REDIS_KEEP_MILLIS);
	}
	/*********** Redis Object를 유지시각(Milliseconds)를 지정하여 저장하기 ************/
	public static void setRedis(String key, Object value, long millis) {
		key = getKey(key);
		log.info("Redis to set key[{}], timeout[{}] is result to [{}]", key, millis, getRedisConn().sync().set(key, value, SetArgs.Builder.px(millis)));
	}
	/*********** Redis 연결하기 - 끈어졌을때 재연결 한다. ************/
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
	public static void pushWorkerService(String beanName, Map<String, Object> param, final WorkerHandler handler, long millis) {
		// “req,<appName>,<beanName>,<System.currentTimeMillis()>”
		// “res,<appName>,<beanName>,<System.currentTimeMillis()>”
		String // reqKey = new StringBuffer().append("req,").append(myAppName).append(",").append(beanName).append(",").append(System.currentTimeMillis()).append(",workerJob").toString();
		reqKey = WORKER_KEYS_PATTERN.replaceFirst("[*]", myAppName).replaceFirst("[*]", beanName).replaceFirst("[*]", String.valueOf(System.currentTimeMillis()));
		getRedisConn().sync().set(reqKey, param, SetArgs.Builder.px(millis));
//		taskExecutor.execute(()->{
//			
//		});
//		final ScheduledFuture<?> future = taskScheduler.scheduleWithFixedDelay(()->{
//			// cancel(true);
//		}, 5 * 1000L);
		
		if(!StringUtils.isEmpty(handler)) {
			// Handler가 있는 경우 응답을 기다린다. 없는경우는 응답을 기다리지 않는다. bean에서도 응답을 주지 말아야 한다.
			new WorkerRunnable(reqKey.replaceFirst("req,", "res."), handler, millis).executeSchedule(taskScheduler, 5 * 1000L);
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
	 * @param throwable
	 * @return
	 **************************************************************************************/
	public static String getErrMsg(Throwable t) {return getErrMsg(t,new StringBuilder(), 0).toString();}
	private static StringBuilder getErrMsg(Throwable t, StringBuilder sb, int depth) {
		if(t!=null) {
			sb.append("[").append(depth).append("]:").append(t.getClass().getName()).append(": ").append(t.getMessage());
			if(t.getCause()!=null)
				getErrMsg(t.getCause(), sb.append("\n"), depth+1);
		}
		return sb;
	}

	/******************************************************************************************
	 * verifyData를 만드는 과정
	 * [JAVA]
	 * 		String interfaceId = "prefix로 사용하는 값";
	 * 		String now = Long.toString(System.currentTimeMillis());
	 * 		String merge = new StringBuffer().append(interfaceId).append(now).append("||").append("불필요한 Dummy 또는 전달발고 싶은 데이터들...").toString();
	 * 		String verification = Base64.getEncoder().encodeToString(merge.getBytes(Charset.forName("UTF-8")));
	 * [Javascript]
	 * 		var interfaceId = "prefix로 사용하는 값";
	 * 		var now = new Date().getTime();
	 * 		var merge = interfaceId + now + "||" + encodeURI("호출지에서 필요한 불필요한 값, 전달시 사용됨...");
	 * 		var verification = btoa(merge);
	 * 		// 자바스크립트에서는 한글데이터가 존재할 경우 해당부분을URI-Encoding해서 보내야 한다.
	 * 
	 * Decoding 후 long형 시간을 체크하는데 기본 UTC는 ZoneId.of("Asia/Seoul")를 사용한다.
	 * 
	 * @param verifyData - Encoding 데이터.
	 * @param limitMinutes - 체크할 범위(분) - 5이면 5분내의 요청에 대해서만 true가 반환됨.
	 * @param prefix - 통산 InterfaceId를 사용한다.
	 * @return
	 */
	public static boolean isOkOnTimeApi(String verifyData, int limitMinutes, String prefix) {
		return isOkOnTimeApi(verifyData, limitMinutes, prefix, Const.getZoneId());
	}
	/******************************************************************************************
	 * verifyData를 만드는 과정
	 * [JAVA]
	 * 		String interfaceId = "prefix로 사용하는 값";
	 * 		String now = Long.toString(System.currentTimeMillis());
	 * 		String merge = new StringBuffer().append(interfaceId).append(now).append("||").append("불필요한 Dummy 또는 전달발고 싶은 데이터들...").toString();
	 * 		String verification = Base64.getEncoder().encodeToString(merge.getBytes(Charset.forName("UTF-8")));
	 * [Javascript]
	 * 		var interfaceId = "prefix로 사용하는 값";
	 * 		var now = new Date().getTime();
	 * 		var merge = interfaceId + now + "||" + encodeURI("호출지에서 필요한 불필요한 값, 전달시 사용됨...");
	 * 		var verification = btoa(merge);
	 * 		// 자바스크립트에서는 한글데이터가 존재할 경우 해당부분을URI-Encoding해서 보내야 한다.
	 * 
	 * @param verifyData - Encoding 데이터.
	 * @param limitMinutes - 체크할 범위(분) - 5이면 5분내의 요청에 대해서만 true가 반환됨.
	 * @param prefix - 통산 InterfaceId를 사용한다.
	 * @param zoneId - 위치를 직접 지정한다. ZoneId.of("Asia/Seoul")
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
//		String merge = new StringBuffer().append(interfaceId).append(now).append("||").append("불필요한 Dummy 또는 전달발고 싶은 데이터들...").toString();
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
//		String merge = new StringBuffer().append(interfaceId).append(now).append("||").append("불필요한 Dummy 또는 전달발고 싶은 데이터들...").toString();
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

}
