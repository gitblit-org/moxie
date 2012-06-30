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
package org.moxie;

public enum UpdatePolicy {

	always(0), never(Integer.MAX_VALUE), daily(0), interval(60);
	
	public static final UpdatePolicy defaultPolicy = daily;
	
	int mins;
	
	UpdatePolicy(int mins) {
		this.mins = mins;
	}
	
	public void setMins(int mins) {
		this.mins = mins;
	}
	
	@Override
	public String toString() {
		if (interval.equals(this)) {
			return name() + ":" + mins;
		}
		return name();
	}
	
	public static UpdatePolicy fromString(String name) {
		for (UpdatePolicy policy : values()) {
			if (policy.name().equalsIgnoreCase(name)) {
				return policy;
			}
		}
		return daily;
	}
}
