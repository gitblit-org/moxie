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
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import org.moxie.utils.StringUtils;

/**
 * Read and manage the configuration.
 * 
 * <p>Unlike the standard config classes, this one allows to reload
 * the config at any convenient time.
 * 
 * @author digulla
 *
 */
public class Config
{
    public static final Log log = LogFactory.getLog(Config.class);
    
    private static Document config;
    private static long configLastModified;
    private static String BASE_DIR = null;
    private static int serverPort = 8080;
    private static String proxyHost = "proxy";
    private static int proxyPort = 80;
    private static String proxyUser;
    private static String proxyPassword;
    private static File cacheDirectory = new File ("cache");
    
    public static synchronized void reload ()
    {
        String fileName = System.getProperty("dsmp.conf", "dsmp.conf");
        File configFile = new File (fileName);
        if (!configFile.isAbsolute())
            configFile = new File (getBaseDirectory(), fileName);
        
        // TODO This means one access to the file system per Config method call
        long lastModified = configFile.lastModified();
        if (config == null || lastModified != configLastModified)
        {
            log.info((config == null ? "Loading" : "Reloading")+ " config from "+configFile.getAbsolutePath());
            configLastModified = lastModified;

            SAXBuilder builder = new SAXBuilder ();
            Throwable t = null;
            Document doc = config;
            List<MirrorEntry> tmpMirrors = mirrors;
            List<AllowDeny> tmpAllowDeny = allowDeny;
            String[] tmpNoProxy = noProxy;
            int tmpPort = serverPort;
            String tmpProxyHost = proxyHost;
            int tmpProxyPort = proxyPort;
            String tmpProxyUser = proxyUser;
            String tmpProxyPassword = proxyPassword;
            File tmpCacheDirectory = cacheDirectory;
            
            try
            {
                doc = builder.build(configFile);
                Element root = doc.getRootElement ();
                tmpCacheDirectory = getCacheDirectory (root);
                tmpMirrors = getMirrors (root);
                tmpAllowDeny = getAllowDeny (root);
                tmpNoProxy = getNoProxy (root);
                tmpPort = getPort (root);
                tmpProxyHost = getProxyHost (root);
                tmpProxyPort = getProxyPort (root);
                tmpProxyUser = getProxyUsername (root);
                tmpProxyPassword = getProxyPassword (root);
            }
            catch (JDOMException e)
            {
                t = e;
            }
            catch (IOException e)
            {
                t = e;
            }
            
            if (t != null)
            {
                String msg = "Error loading config from "+configFile.getAbsolutePath();
                log.error (msg, t);
                if (config == null)
                    throw new Error (msg, t);
            }

            // TODO All options should be checked for errors

            // After the error checking, save the new parameters
            config = doc;
            cacheDirectory = tmpCacheDirectory;
            mirrors = tmpMirrors;
            allowDeny = tmpAllowDeny;
            noProxy = tmpNoProxy;
            serverPort = tmpPort;
            proxyHost = tmpProxyHost;
            proxyPort = tmpProxyPort;
            proxyUser = tmpProxyUser;
            proxyPassword = tmpProxyPassword;
        }
    }
    
    public static void setBaseDir (String path)
    {
        Config.BASE_DIR = path;
    }
    
    private static int getPort (Element root)
    {
        int port = getIntProperty (root, "server", "port", 8080);
        int max = 0xffff;
        if (port < 1 || port > max)
            throw new RuntimeException ("Value for proxy.port must be between 1 and "+max);
        return port;
    }
    
    public static int getPort ()
    {
        return serverPort;
    }

    private static File getCacheDirectory (Element root)
    {
        String defaultValue = "cache";
        
        String s = getStringProperty(root, "directories", "cache", defaultValue);
        File f = new File (s);
        if (!f.isAbsolute())
            f = new File (getBaseDirectory (), s);
        
        IOUtils.mkdirs (f);
        
        return f;
    }
    
    public static File getCacheDirectory ()
    {
        return cacheDirectory;
    }

    public static File getBaseDirectory ()
    {
        String path = BASE_DIR;
        if (path == null)
            path = System.getProperty("user.home");
        return new File (path);
    }

    private static String getStringProperty (Element root, String element, String attribute, String defaultValue)
    {
        Element e = root.getChild(element);
        if (e == null)
            return defaultValue;
        
        String value = e.getAttributeValue(attribute);
        if (value == null)
            return defaultValue;
        
        return value;
    }
    
    private static boolean hasProperty (Element root, String element)
    {
        Element e = root.getChild(element);
        if (e == null)
            return false;
        
        return true;
    }
    
    private static String getStringProperty (Element root, String element, String attribute)
    {
        String value = getStringProperty(root, element, attribute, null);
        if (value == null)
            throw new RuntimeException ("Property "+element+"@"+attribute+" is not set.");
        
        return value;
    }
    
    private static int getIntProperty (Element root, String element, String attribute, int defaultValue)
    {
        String value = getStringProperty(root, element, attribute, null);
        if (value == null)
            return defaultValue;
        
        try
        {
            return Integer.parseInt(value);
        }
        catch (NumberFormatException e)
        {
            throw (NumberFormatException)(new NumberFormatException ("Error convertion value '"+value+"' of property "+element+"@"+attribute+": "+e.getMessage()).initCause(e));
        }
    }

    private static boolean hasProxy (Element root)
    {
        return hasProperty (root, "proxy");
    }
    
    private static String getProxyUsername (Element root)
    {
        if (!hasProxy (root))
            return null;
        
        return getStringProperty (root, "proxy", "user");
    }

    public static String getProxyUsername ()
    {
        return proxyUser;
    }
    
    private static String getProxyPassword (Element root)
    {
        if (!hasProxy (root))
            return null;

        return getStringProperty (root, "proxy", "password");
    }

    public static String getProxyPassword ()
    {
        return proxyPassword;
    }
    
    private static String getProxyHost (Element root)
    {
        if (!hasProxy (root))
            return null;

        return getStringProperty (root, "proxy", "host");
    }
    
    public static String getProxyHost ()
    {
        return proxyHost;
    }
    
    private static int getProxyPort (Element root)
    {
        int port = getIntProperty (root, "proxy", "port", 80);
        int max = 0xffff;
        if (port < 1 || port > max)
            throw new RuntimeException ("Value for proxy.port must be between 1 and "+max);
        return port;
    }
    
    public static int getProxyPort ()
    {
        return proxyPort;
    }
    
    private static class MirrorEntry
    {
        private String from;
        private String to;
        
        public MirrorEntry (String from, String to)
        {
            this.from = fix (from);
            this.to = fix (to);
        }

        private String fix (String s)
        {
            s = s.trim ();
            if (!s.endsWith("/"))
                s += "/";
            return s;
        }
        
        public URL getMirrorURL (String s)
        {
            //log.debug(s);
            //log.debug(from);
            
            if (s.startsWith(from))
            {
                s = s.substring(from.length());
                s = to + s;
                try
                {
                    return new URL (s);
                }
                catch (MalformedURLException e)
                {
                    throw new RuntimeException ("Couldn't create URL from "+s, e);
                }
            }
            
            return null;
        }
    }
    
    private static List<MirrorEntry> mirrors = Collections.emptyList ();
    
    public static List<MirrorEntry> getMirrors (Element root)
    {
        List<MirrorEntry> l = new ArrayList<MirrorEntry> ();
        for (Iterator iter = root.getChildren("redirect").iterator(); iter.hasNext();)
        {
            Element element = (Element)iter.next();
            String from = element.getAttributeValue("from");
            String to = element.getAttributeValue("to");
            
            if (StringUtils.isEmpty(from))
                throw new RuntimeException ("from attribute is missing or empty in redirect element");
            if (StringUtils.isEmpty(to))
                throw new RuntimeException ("to attribute is missing or empty in redirect element");
            
            l.add (new MirrorEntry (from, to));
        }

        return l;
    }
    
    public static List<MirrorEntry> getMirrors ()
    {
        return mirrors;
    }
    
    public static URL getMirror (URL url) throws MalformedURLException
    {
        String s = url.toString();
        
        for (MirrorEntry entry: getMirrors())
        {
            URL mirror = entry.getMirrorURL(s);
            if (mirror != null)
            {
                log.info ("Redirecting request to mirror "+mirror.toString());
                return mirror;
            }
        }
        
        return url;
    }
    
    private static String[] noProxy = new String[0];
    
    private static String[] getNoProxy (Element root)
    {
        String s = getStringProperty(root, "proxy", "no-proxy", null);
        if (s == null)
            return new String[0];
        
        String[] result = s.split(",");
        for (int i=0; i<result.length; i++)
        {
            result[i] = result[i].trim ();
        }
        
        return result;
    }
    
    public static String[] getNoProxy ()
    {
        return noProxy;
    }
    
    public static boolean useProxy (URL url)
    {
        if (!hasProxy (config.getRootElement ()))
            return false;
        
        String host = url.getHost();
        for (String postfix: getNoProxy())
        {
            if (host.endsWith(postfix))
                return false;
        }
        return true;
    }

    private static class AllowDeny
    {
        private final String url;
        private boolean allow;
        
        public AllowDeny (String url, boolean allow)
        {
            this.url = url;
            this.allow = allow;
        }
        
        public boolean matches (String url)
        {
            return url.startsWith(this.url);
        }
        
        public boolean isAllowed ()
        {
            return allow;
        }
        
        public String getURL ()
        {
            return url;
        }
    }
    
    private static List<AllowDeny> allowDeny = Collections.emptyList ();
    
    public static List<AllowDeny> getAllowDeny (Element root)
    {
        ArrayList<AllowDeny> l = new ArrayList<AllowDeny> ();
        
        for (Iterator iter = root.getChildren().iterator(); iter.hasNext();)
        {
            Element element = (Element)iter.next();
            if ("allow".equals (element.getName()) || "deny".equals(element.getName()))
            {
                boolean allow = "allow".equals (element.getName());
                String url = element.getAttributeValue("url");
                if (url == null)
                    throw new RuntimeException ("Missing or empty url attribute in "+element.getName()+" element");
                l.add (new AllowDeny (url, allow));
            }
        }

        return l;
    }
    
    public static List<AllowDeny> getAllowDeny ()
    {
        return allowDeny;
    }
    
    public static boolean isAllowed (URL url)
    {
        String s = url.toString();
        for (AllowDeny rule: getAllowDeny())
        {
            if (rule.matches(s))
            {
                log.info((rule.isAllowed() ? "Allowing" : "Denying")+" access to "+url+" because of config rule");
                return rule.isAllowed();
            }
        }
        
        return true;
    }
}
