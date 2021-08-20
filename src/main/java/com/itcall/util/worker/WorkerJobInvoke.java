package com.itcall.util.worker;

import java.util.List;
import java.util.Map;

import org.springframework.context.ApplicationContext;
import org.springframework.util.StringUtils;

import com.itcall.util.ComUtils;

import io.lettuce.core.SetArgs;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class WorkerJobInvoke implements Runnable {

	private ApplicationContext context;

	public WorkerJobInvoke(ApplicationContext context) {
		this.context = context;
	}

	@Override
	public void run() {
		// “req,<appName>,<beanName>,<System.currentTimeMillis()>”
		// “res,<appName>,<beanName>,<System.currentTimeMillis()>”
		// 한번만 읽어갈 수 있게 처리해야 한다.
		// List<String> keyList = getRedisConn().sync().keys("req,[-_.0-9a-zA-Z]*,[-_.0-9a-zA-Z]*,[0-9]*$"/*pattern*/); // 패턴이 Matches 패턴이 아니다.
		/*** 병렬 APP들이 동일 작업을 가져가지 않기 위한 최대한의 노력 ***/
		synchronized (context) {
			List<String> keyList = ComUtils.getRedisConn().sync().keys(ComUtils.WORKER_KEYS_PATTERN);
			if(!StringUtils.isEmpty(keyList) && keyList.size()>0) {
				if(StringUtils.isEmpty(ComUtils.getRedisConn().sync().get(ComUtils.WORKER_SYNCRONOISED_CHECK_KEY))) {
					ComUtils.getRedisConn().sync().set(ComUtils.WORKER_SYNCRONOISED_CHECK_KEY, "someone used!!! wait just seconds...", SetArgs.Builder.px(ComUtils.ONE_MINITE_FOR_MS));
					try {
						String reqKey = keyList.get(0);
						try {
							String resKey = reqKey.replaceFirst("req,", "res.");
							String beanName = reqKey.split(",", 4)[2];
							@SuppressWarnings("unchecked")
							Map<String, Object> param = (Map<String, Object>) ComUtils.getRedisConn().sync().get(reqKey);
							log.info("WorkerJob[{}] Take param : ", reqKey, param);
							ComUtils.getRedisConn().sync().del(reqKey);
							Map<String, Object> result = context.getBean(beanName, WorkerService.class).run(param);
							ComUtils.getRedisConn().sync().set(resKey, result, SetArgs.Builder.px(ComUtils.DEF_REDIS_KEEP_MILLIS));
							log.info("WorkerJob[{}] Result Saved : ", resKey, result);
						} catch (Exception e) {
							log.error("WorkerJob[{}] Redis take error : {}{}", reqKey, e.getMessage(), e);
							ComUtils.getRedisConn().sync().del(reqKey);
						}
					} finally {
						ComUtils.getRedisConn().sync().del(ComUtils.WORKER_SYNCRONOISED_CHECK_KEY);
					}
				}
			}
		}
	}

}
