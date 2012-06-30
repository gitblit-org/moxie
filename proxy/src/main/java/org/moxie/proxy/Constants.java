package org.moxie.proxy;


public class Constants {

	public static String getName() {
		return "Moxie Proxy";
	}
	
	public static String getUrl() {
		return "http://gitblit.github.com/moxie/";
	}
	
	public static String getVersion() {
		String v = Constants.class.getPackage().getImplementationVersion();
		if (v == null) {
			return "DEVELOPMENT";
		}
		return "v" + v;
	}
}
