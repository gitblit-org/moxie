package org.moxie.maxml;

import static java.text.MessageFormat.format;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/**
 * Static utility methods for the Maxml parser.
 * 
 * @author James Moger
 * 
 */
public class Maxml {

	/**
	 * Parse the Maxml content and reflectively build an object with the
	 * document's data.
	 * 
	 * @param content
	 * @param clazz
	 * @return an object
	 * @throws MaxmlException
	 */
	public static <X> X parse(String content, Class<X> clazz)
			throws MaxmlException {
		try {
			MaxmlParser parser = new MaxmlParser();
			Map<String, Object> map = parser.parse(new BufferedReader(
					new StringReader(content)));
			X x = clazz.newInstance();
			Map<String, Field> fields = new HashMap<String, Field>();
			for (Field field : clazz.getFields()) {
				fields.put(field.getName().toLowerCase(), field);
			}
			for (Map.Entry<String, Object> entry : map.entrySet()) {
				String key = entry.getKey();
				Object o = entry.getValue();
				if (fields.containsKey(key)) {
					Field field = fields.get(key);
					field.set(x, o);
				} else {
					throw new MaxmlException(format("Unbound property \"{0}\"",
							key));
				}
			}
			return x;
		} catch (Exception t) {
			throw new MaxmlException(t);
		}
	}

	/**
	 * Parse the content of the Maxml document and return a object map of the
	 * content.
	 * 
	 * @param content
	 * @return an object map
	 */
	public static MaxmlMap parse(String content)
			throws MaxmlException {
		try {
			MaxmlParser parser = new MaxmlParser();
			return parser.parse(new BufferedReader(new StringReader(content)));
		} catch (Exception e) {
			throw new MaxmlException(e);
		}
	}

	/**
	 * Parse the content of the Maxml document and return an object map of the
	 * content.
	 * 
	 * @param is
	 *            an input stream
	 * @return an object map
	 */
	public static MaxmlMap parse(InputStream is)
			throws MaxmlException {
		try {
			MaxmlParser parser = new MaxmlParser();
			return parser.parse(new BufferedReader(new InputStreamReader(is,
					"UTF-8")));
		} catch (Exception e) {
			throw new MaxmlException(e);
		}
	}
}
