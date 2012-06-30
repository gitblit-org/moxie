package org.moxie.proxy.resources;

import java.util.HashMap;
import java.util.Map;

import org.moxie.proxy.Constants;
import org.restlet.representation.Representation;
import org.restlet.resource.Get;

public class RootResource extends BaseResource {

	@Override
	protected String getBasePath() {
		return "";
	}

	@Override
	protected String getBasePathName() {
		return "";
	}

	@Get
	public Representation toText() {
		Map<String, Object> map = new HashMap<String, Object>();
		map.put("title", Constants.getName());
		map.put("tagline", getTranslation().getString("mp.tagline"));
		map.put("content", "");
		return toHtml(map, "root.html");
	}
}
