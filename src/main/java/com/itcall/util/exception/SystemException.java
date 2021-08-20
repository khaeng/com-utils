package com.itcall.util.exception;

public class SystemException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	public SystemException(Exception cause) {
		super(cause);
	}

	public SystemException(String message) {
		super(message);
	}

	public SystemException(String message, Throwable cause) {
		super(message, cause);
	}
}
