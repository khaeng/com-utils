package com.itcall.util;

import java.util.Arrays;
import java.util.Map;

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
		return context.getEnvironment().getProperty(key, System.getProperty(key));
	}
}
