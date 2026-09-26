package io.github.jobexplorer.service;

/** 模型调用失败或返回内容无法使用。此时保留之前的条件，不做任何删除。 */
public class ExtractionFailedException extends RuntimeException {

	public ExtractionFailedException(String message, Throwable cause) {
		super(message, cause);
	}
}
