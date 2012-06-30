package org.moxie.proxy;

import java.io.Serializable;

public class RemoteRepository implements Serializable {

	private static final long serialVersionUID = 1L;
	
	public final String id;
	public final String url;
	
	public RemoteRepository(String id, String url) {
		this.id = id;
		this.url = url;
	}
	
}
