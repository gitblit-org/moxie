package com.maxtk;


public class NoMarkdown {
	String startToken;
	
	String endToken;
	
	boolean escape;
	
	boolean prettify;
	
	boolean linenums;
	
	String lang;

	public void setStarttoken(String token) {
		this.startToken = token;
	}

	public void setEndtoken(String token) {
		this.endToken = token;
	}
	
	public void setEscape(boolean value) {
		this.escape = value;
	}
	
	public void setPrettify(boolean value) {
		this.prettify = true;
	}

	public void setLinenums(boolean value) {
		this.linenums = true;
	}

	public void setLang(String value) {
		this.lang = value;
	}

}