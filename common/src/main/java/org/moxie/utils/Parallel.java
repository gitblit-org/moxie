/*
 * Copyright 2011 Matt Crinklaw-Vogt
 * Copyright 2012 Weimin Xiao
 * Copyright 2013 James Moger
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.moxie.utils;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * http://stackoverflow.com/questions/4010185/parallel-for-for-java
 * https://github.com/tantaman/commons/blob/master/src/main/java/com/tantaman/commons/concurrent/Parallel.java
 */
public class Parallel {

	private static final int NUM_CORES = Runtime.getRuntime().availableProcessors();
	private static final ExecutorService executor = Executors.newFixedThreadPool(NUM_CORES, Executors.defaultThreadFactory());

	/**
	 * Executes operations in parallel and waits for all operations to complete
	 * before returning.
	 * 
	 * @param elements
	 * @param operation
	 */
	public static <T> void WaitFor(final Iterable<T> elements, final Operation<T> operation) {
		List<Future<?>> futures = new LinkedList<Future<?>>();
		for (final T element : elements) {
			Future<?> future = executor.submit(new Callable<Void>() {
				@Override
				public Void call() {
					operation.perform(element);
					return null;
				}
			});
			futures.add(future);
		}

		for (Future<?> f : futures) {
			try {
				f.get();
			} catch (InterruptedException e) {
			} catch (ExecutionException e) {
				Throwable t = e.getCause();
				if (t instanceof RuntimeException) {
					throw (RuntimeException) t;
				}
				throw new RuntimeException(t);
			}
		}
	}

	public static interface Operation<T> {
		public void perform(T pParameter);
	}
}