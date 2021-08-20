package com.itcall.util.worker;

import java.util.Map;

public interface WorkerService {

	public Map<String, Object> run(Map<String, Object> param);

}
