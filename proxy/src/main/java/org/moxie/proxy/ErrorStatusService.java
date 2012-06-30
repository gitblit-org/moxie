/*
 * Copyright 2012 James Moger
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

public class ErrorStatusService extends StatusService {
	
	final Configuration configuration;
	
	public ErrorStatusService(Context context) {
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
