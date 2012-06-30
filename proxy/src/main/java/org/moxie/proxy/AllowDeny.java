package org.moxie.proxy;

public class AllowDeny {
	private final String url;
	private boolean allow;

	public AllowDeny(String url, boolean allow) {
		this.url = url;
		this.allow = allow;
	}

	public boolean matches(String url) {
		return url.startsWith(this.url);
	}

	public boolean isAllowed() {
		return allow;
	}

	public String getURL() {
		return url;
	}
}
