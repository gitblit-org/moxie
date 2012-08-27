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
package org.moxie.ant;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.tools.ant.IntrospectionHelper;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.UnsupportedAttributeException;
import org.moxie.MoxieException;
import org.moxie.console.Console;
import org.moxie.maxml.MaxmlMap;

/**
 * This utility class sets and logs attributes specified in a .moxie file
 * using Ant's introspection helper.
 * 
 * @author James Moger
 *
 */
public class AttributeReflector {

	public static void setAttributes(Project project, Object object, MaxmlMap attributes) {
		IntrospectionHelper ih = IntrospectionHelper.getHelper(project, object.getClass());
		for (String key : attributes.keySet()) {
			Object value = attributes.get(key);
			String attrValue;
			if (value instanceof List) {
				// flatten lists back to a string for Ant
				List<?> list = (List<?>) value;
				StringBuilder sb = new StringBuilder();
				for (Object o : list) {
					sb.append(o.toString()).append(", ");
				}
				sb.setLength(sb.length() - 2);
				attrValue = sb.toString().trim();
			} else {
				// ultimately toString is called anyway
				attrValue = value.toString();
			}
			try {
				ih.setAttribute(project, object, key, attrValue);
			} catch (UnsupportedAttributeException be) {
				throw new MoxieException("{0} does not support the \"{1}\" attribute!", object instanceof Task ? ((Task) object).getTaskName() : object.getClass().getSimpleName(), key);
			}
		}
	}
	
	public static void logAttributes(Object object, MaxmlMap attributes, Console console) {
		if (attributes != null) {
			try {
				Map<String, Method> methods = new HashMap<String, Method>();
				for (Class<?> javacClass : new Class<?>[] { object.getClass().getSuperclass(), object.getClass() }) {
					for (Method method: javacClass.getDeclaredMethods()) {
						if (method.getName().startsWith("get")) {
							methods.put(method.getName().toLowerCase(), method);
						}
					}
				}
				for (String attrib : attributes.keySet()) {
					Method method = methods.get("get" + attrib.toLowerCase());
					if (method == null) {
						continue;
					}
					method.setAccessible(true);
					Object value = method.invoke(object, (Object[]) null);
					console.debug(1, "{0} = {1}", attrib, value);
				}			
			} catch (Exception e) {
				console.error(e);
				throw new MoxieException("failed to log {0} attributes!", object instanceof Task ? ((Task) object).getTaskName() : object.getClass().getSimpleName());
			}
		}
	}
}
