package org.moxie.proxy;

import java.util.HashMap;
import java.util.Map;

import org.restlet.Context;
import org.restlet.Request;
import org.restlet.Response;
import org.restlet.data.CharacterSet;
import org.restlet.data.MediaType;
import org.restlet.data.Status;
import org.restlet.ext.freemarker.ContextTemplateLoader;
import org.restlet.ext.freemarker.TemplateRepresentation;
import org.restlet.representation.Representation;
import org.restlet.service.StatusService;

import freemarker.template.Configuration;

public class MxStatusService extends StatusService {
	
	final Configuration configuration;
	
	public MxStatusService(Context context) {
		this.configuration = new Configuration();
		this.configuration.setTemplateLoader(new ContextTemplateLoader(context, "clap://class/templates"));
	}

	@Override
	public Representation getRepresentation(Status status, Request request, Response response) {
		Map<String, Object> map = new HashMap<String, Object>();
		map.put("title", Constants.getName());
		map.put("reason", status.getReasonPhrase());
		map.put("description", status.getDescription());		

		TemplateRepresentation template = new TemplateRepresentation("error.html", configuration, map,
				MediaType.TEXT_HTML);
		template.setCharacterSet(CharacterSet.UTF_8);
		return template;
	}
}
