package com.maxtk;

import java.util.HashSet;
import java.util.Set;

public class Prop {
	String token;
	String file;

	Set<String> keywords = new HashSet<String>();

	public void setToken(String token) {
		this.token = token;
	}

	public void setFile(String file) {
		this.file = file;
	}
	
	public void addKeyword(Keyword keyword) {
		keywords.add(keyword.value);
	}
	
	public boolean containsKeyword(String comment) {
		for (String keyword:keywords) {
			if (comment.contains(keyword)) {
				return true;
			}
		}
		return false;
	}
}