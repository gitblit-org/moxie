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
package org.moxie.utils;

import static org.pegdown.Extensions.ALL;
import static org.pegdown.Extensions.SMARTYPANTS;

import org.pegdown.LinkRenderer;
import org.pegdown.PegDownProcessor;

/**
 * Utility methods for transforming raw markdown text to html.
 * 
 * @author James Moger
 * 
 */
public class MarkdownUtils {

	/**
	 * Returns the html version of the markdown source text.
	 *
	 * @param markdown
	 * @return html version of markdown text
	 * @throws java.text.ParseException
	 */
	public static String transformMarkdown(String markdown) {
		return transformMarkdown(markdown, null);
	}

	/**
	 * Returns the html version of the markdown source text.
	 *
	 * @param markdown
	 * @return html version of markdown text
	 * @throws java.text.ParseException
	 */
	public static String transformMarkdown(String markdown, LinkRenderer linkRenderer) {
		PegDownProcessor pd = new PegDownProcessor(ALL & ~SMARTYPANTS);
		String html = pd.markdownToHtml(markdown, linkRenderer == null ? new LinkRenderer() : linkRenderer);
		return html;
	}
}
