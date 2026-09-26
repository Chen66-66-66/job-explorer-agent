package io.github.jobexplorer.service;

/** 未配置大模型时调用抽取功能抛出。系统其余功能不受影响，条件可以手动录入。 */
public class ModelNotConfiguredException extends RuntimeException {

	public ModelNotConfiguredException() {
		super("未配置大模型：请设置环境变量 LLM_PROVIDER=deepseek 和 LLM_API_KEY，或改为手动录入条件");
	}
}
