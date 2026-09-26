package io.github.jobexplorer.service;

public class NotFoundException extends RuntimeException {

	public NotFoundException(String what, Long id) {
		super(what + " 不存在：" + id);
	}
}
