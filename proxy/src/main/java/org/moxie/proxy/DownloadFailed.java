/*
 * Copyright 2002-2005 The Apache Software Foundation.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.moxie.proxy;

import org.apache.commons.httpclient.methods.GetMethod;

/**
 * This exception extracts information from a failed GET request and
 * presents it in a user-friendly way.
 * 
 * @author digulla
 *
 */
public class DownloadFailed extends Exception
{
    private String statusLine;
    
    public DownloadFailed (GetMethod get)
    {
        super ("Download failed: "+get.getStatusLine().toString());
        statusLine = get.getStatusLine().toString();
    }

    public DownloadFailed (String message)
    {
        super ("Download failed: "+message);
        statusLine = message;
    }

    public String getStatusLine ()
    {
        return statusLine;
    }
}
