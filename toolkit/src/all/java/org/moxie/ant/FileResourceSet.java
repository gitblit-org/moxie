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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import org.apache.tools.ant.types.AbstractFileSet;
import org.apache.tools.ant.types.Resource;
import org.apache.tools.ant.types.ResourceCollection;
import org.apache.tools.ant.types.resources.FileResource;

public class FileResourceSet extends AbstractFileSet implements ResourceCollection {

	List<FileResource> resources;
	
	FileResourceSet(Collection<FileResource> resources) {
		this.resources = new ArrayList<FileResource>(resources);
	}
	
	public Iterator<Resource> iterator() {
		return new ResolvedFileIterator();
	}
	
	public int size() {
		return resources.size();
	}
	
	class ResolvedFileIterator implements Iterator<Resource> {
	    private int pos = 0;

	    public ResolvedFileIterator() {
	    }

	    /**
	     * Find out whether this FileResourceIterator has more elements.
	     * @return whether there are more Resources to iterate over.
	     */
	    public boolean hasNext() {
	        return pos < resources.size();
	    }

	    /**
	     * Get the next element from this FileResourceIterator.
	     * @return the next Object.
	     */
	    public FileResource next() {
	        return nextResource();
	    }

	    /**
	     * Not implemented.
	     */
	    public void remove() {
	        throw new UnsupportedOperationException();
	    }

	    /**
	     * Convenience method to return the next resource.
	     * @return the next File.
	     */
	    public FileResource nextResource() {
	        if (!hasNext()) {
	            throw new NoSuchElementException();
	        }
	        return resources.get(pos++);
	    }
	}

	@Override
	public boolean isFilesystemOnly() {
		return true;
	}
}
