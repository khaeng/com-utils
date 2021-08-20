package com.itcall.util.worker;

import java.io.Serializable;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;

import org.springframework.scheduling.TaskScheduler;
import org.springframework.util.StringUtils;

import com.itcall.util.ComUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class WorkerRunnable implements Runnable, Serializable {
	
	private static final long serialVersionUID = 1L;

	private final String resKey;
	private final WorkerHandler handler;
	private volatile ScheduledFuture<?> self;
	private final long finishedAtTime;
	public WorkerRunnable(String resKey, WorkerHandler handler, long waitTimeout) {
		this.resKey = resKey;
		this.handler = handler;
		this.finishedAtTime = System.currentTimeMillis() + waitTimeout;
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public void run() {
		boolean interrupted = false;
		try {
			Object result = ComUtils.getRedisConn().sync().get(resKey);
			if(!StringUtils.isEmpty(result)) {
				log.info("WorkerJob[{}] Finded Result : ", resKey, result);
				ComUtils.getRedisConn().sync().del(resKey);
				handler.workerServiceComplite((Map<String, Object>) result);
				interrupted = true; // 결과가 와서 종료함.
			} else if(System.currentTimeMillis() > finishedAtTime) {
				log.warn("WorkerJob[{}] Timeout and Terminating...", resKey);
				interrupted = true; // 시간초과
				return;
			} else {
				log.debug("WorkerJob[{}] Check and Waiting...", resKey);
				return; // 다음 Schedule에서 응답을 체크한다.
			}
//			while (self == null) {
//				try {
//					Thread.sleep(1);
//				} catch (InterruptedException e) {
//					interrupted = true;
//				}
//			}
		} catch (Exception e) {
			log.error("WorkerJob[{}] Error : {}{}", resKey, e.getMessage(), e);
			interrupted = true; // 에러발생 작업취소.
		} finally {
			if (interrupted) {
				self.cancel(false);
				Thread.currentThread().interrupt();
			}
		}
	}

	public void executeSchedule(TaskScheduler taskScheduler, long delayMilli) {
		self = taskScheduler.scheduleWithFixedDelay(this, delayMilli);
	}

}
