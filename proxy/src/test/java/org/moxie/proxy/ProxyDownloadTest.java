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

import java.io.File;
import java.net.URL;

import junit.framework.TestCase;

import org.moxie.utils.FileUtils;

public class ProxyDownloadTest extends TestCase
{
    
    public void testMkdirs () throws Exception
    {
        URL url = new URL ("http://repo1.maven.org/maven2/org/apache/commons/commons-parent/1/commons-parent-1.pom");
        File f = RequestHandler.getCacheFile(url);
        File dir = Config.getCacheDirectory();
        String expected = dir.getAbsolutePath().replace(File.separatorChar, '/')+"/repo1.maven.org/maven2/org/apache/commons/commons-parent/1/commons-parent-1.pom";
        String s = f.getAbsolutePath().replace(File.separatorChar, '/');
        assertEquals(expected, s);
    }
    
    public void testDownload () throws Exception
    {
        URL url = new URL ("http://repo1.maven.org/maven2/org/apache/commons/commons-parent/1/commons-parent-1.pom");
        File f = new File ("tmp/commons-parent-1.pom");
        ProxyDownload d = new ProxyDownload (url, f);
        d.download();
        
        assertEquals (7616, f.length());
    }
    
    @Override
    protected void setUp () throws Exception
    {
        super.setUp();

        Config.setBaseDir("tmp");
        File from = new File ("dsmp-test.conf");
        if (!from.exists())
        {
            File orig = new File ("src/test/resources/dsmp-test.conf");
            throw new RuntimeException ("Please copy "+orig.getAbsolutePath()+" to "+from.getAbsolutePath()+" and adjust the proxy settings for your site to run this test.");
        }
        FileUtils.copyFile(from, new File ("tmp/dsmp.conf"));
        Config.reload ();
    }

    @Override
    protected void tearDown () throws Exception
    {
        super.tearDown();
    }
}
