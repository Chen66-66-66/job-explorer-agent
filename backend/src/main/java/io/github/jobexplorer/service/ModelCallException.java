package io.github.jobexplorer.service;

/** 模型调用失败或返回内容无法使用。抽取时遇到它会保留之前的条件，不做任何删除。 */
public class ModelCallException extends RuntimeException {

	public ModelCallException(String message, Throwable cause) {
		super(message, cause);
	}
}
