/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *	  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.moxie.less;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.JavaScriptException;
import org.mozilla.javascript.NativeArray;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.tools.shell.Global;

/**
 * @author Rostislav Hristov
 * @author Uriah Carpenter
 * @author Noah Sloan
 */
public class LessEngine {
	
	private Scriptable scope;
	private ClassLoader classLoader;
	private Function compileString;
	private Function compileFile;
	
	public LessEngine() {
		try {
			URL less = getClass().getResource("/less/less.js");
			URL env = getClass().getResource("/less/env.js");
			URL engine = getClass().getResource("/less/engine.js");
			URL cssmin = getClass().getResource("/less/cssmin.js");
			Context cx = Context.enter();
			cx.setOptimizationLevel(9);
			Global global = new Global();
			global.init(cx);
			scope = cx.initStandardObjects(global);
			cx.evaluateReader(scope, new InputStreamReader(env.openConnection().getInputStream()), env.getFile(), 1, null);
			cx.evaluateString(scope, "lessenv.charset = 'UTF-8';", "charset", 1, null);
			cx.evaluateString(scope, "lessenv.css = false;", "css", 1, null);
			cx.evaluateReader(scope, new InputStreamReader(less.openConnection().getInputStream()), less.getFile(), 1, null);
			cx.evaluateReader(scope, new InputStreamReader(cssmin.openConnection().getInputStream()), cssmin.getFile(), 1, null);
			cx.evaluateReader(scope, new InputStreamReader(engine.openConnection().getInputStream()), engine.getFile(), 1, null);
			compileString = (Function) scope.get("compileString", scope);
			compileFile = (Function) scope.get("compileFile", scope);
			Context.exit();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public String compile(String input) throws LessException {
		return compile(input, false);
	}
	
	public String compile(String input, boolean compress) throws LessException {
		try {
			String result = call(compileString, new Object[] {input, compress});
			return result;
		} catch (Exception e) {
			throw parseLessException(e);
		}
	}
	
	public String compile(URL input) throws LessException {
		return compile(input, false);
	}
	
	public String compile(URL input, boolean compress) throws LessException {
		try {
			String result = call(compileFile, new Object[] {input.getProtocol() + ":" + input.getFile(), classLoader, compress});
			return result;
		} catch (Exception e) {
			throw parseLessException(e);
		}
	}
	
	public String compile(File input) throws LessException {
		return compile(input, false);
	}
	
	public String compile(File input, boolean compress) throws LessException {
		try {
			String result = call(compileFile, new Object[] {"file:" + input.getAbsolutePath(), classLoader, compress});
			return result;
		} catch (Exception e) {
			throw parseLessException(e);
		}
	}
	
	public void compile(File input, File output) throws LessException, IOException {
		compile(input, output, false);
	}
	
	public void compile(File input, File output, boolean compress) throws LessException, IOException {
		try {
			String content = compile(input, compress);
			if (!output.exists()) {
				output.createNewFile();
			}
			BufferedWriter bw = new BufferedWriter(new FileWriter(output));
			bw.write(content);
			bw.close();
		} catch (Exception e) {
			throw parseLessException(e);
		}
	}

	private synchronized String call(Function fn, Object[] args) {
		return (String) Context.call(null, fn, scope, scope, args);
	}
	
	private LessException parseLessException(Exception root) throws LessException {
		if (root instanceof JavaScriptException) {
			Scriptable value = (Scriptable) ((JavaScriptException) root).getValue();
			String type = (String) ScriptableObject.getProperty(value, "type") + " Error";
			String message = (String) ScriptableObject.getProperty(value, "message");
			String filename = "";
			if (ScriptableObject.hasProperty(value, "filename")) {
				filename = (String) ScriptableObject.getProperty(value, "filename"); 
			}
			int line = -1;
			if (ScriptableObject.hasProperty(value, "line")) {
				line = ((Double) ScriptableObject.getProperty(value, "line")).intValue(); 
			}
			int column = -1;
			if (ScriptableObject.hasProperty(value, "column")) {
				column = ((Double) ScriptableObject.getProperty(value, "column")).intValue();
			}				
			List<String> extractList = new ArrayList<String>();
			if (ScriptableObject.hasProperty(value, "extract")) {
				NativeArray extract = (NativeArray) ScriptableObject.getProperty(value, "extract");
				for (int i = 0; i < extract.getLength(); i++) {
					if (extract.get(i, extract) instanceof String) {
						extractList.add(((String) extract.get(i, extract)).replace("\t", " "));
					}
				}
			}
			throw new LessException(message, type, filename, line, column, extractList);
		}
		throw new LessException(root);
	}
	
}