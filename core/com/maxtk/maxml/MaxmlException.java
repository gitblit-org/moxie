package com.maxtk.maxml;
/**
 * MaxmlException is thrown by the MaxmlParser.
 * 
 * @author James Moger
 * 
 */
public class MaxmlException extends Exception {

	private static final long serialVersionUID = 1L;

	public MaxmlException(String msg) {
		super(msg);
	}

	public MaxmlException(Throwable t) {
		super(t);
	}
}