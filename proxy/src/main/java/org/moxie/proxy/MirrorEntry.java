package org.moxie.proxy;

import java.net.MalformedURLException;
import java.net.URL;

public class MirrorEntry {
	private String from;
	private String to;

	public MirrorEntry(String from, String to) {
		this.from = fix(from);
		this.to = fix(to);
	}

	private String fix(String s) {
		s = s.trim();
		if (!s.endsWith("/"))
			s += "/";
		return s;
	}

	public URL getMirrorURL(String s) {
		if (s.startsWith(from)) {
			s = s.substring(from.length());
			s = to + s;
			try {
				return new URL(s);
			} catch (MalformedURLException e) {
				throw new RuntimeException("Couldn't create URL from " + s, e);
			}
		}

		return null;
	}
}