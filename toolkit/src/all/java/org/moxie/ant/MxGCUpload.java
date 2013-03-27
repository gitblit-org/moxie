/*
 * Created on May 24, 2007
 *
 * This code belongs to Jonathan Fuerth
 */
package org.moxie.ant;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSession;

import org.apache.tools.ant.BuildException;
import org.moxie.utils.Base64;


/**
 * GoogleCodeUploadTask is an Ant task for uploading files to a Google Code
 * project's downloads area.
 * <p>
 * The following parameters must be set before this task is executed:
 * <ul>
 *  <li>userName
 *  <li>password
 *  <li>projectName
 *  <li>fileName
 *  <li>targetFileName
 *  <li>summary
 * </ul>
 * <p>
 * The following parameters are optional, and default to nothing/excluded:
 * <ul>
 *  <li>labels (a comma-separated list of labels to apply to the uploaded item)
 * </ul>
 * <p>
 * The following parameters have reasonable defaults, and do not normally need to be set:
 * <ul>
 *  <li>uploadUrl
 *  <li>ignoreSslCertificateHostname
 * </ul>
 *
 * @version $Id$
 */
public class MxGCUpload extends MxTask {
    
    /**
     * Google user name to authenticate as (this is just the username part; 
     * don't include the @gmail.com part).
     */
    private String userName;
    
    /**
     * Coogle Code password (not the same as the gmail password).
     */
    private String password;
    
    /**
     * Google Code project name to upload to.
     */
    private String projectName;

    /**
     * The local path of the file to upload. 
     */
    private String fileName;
    
    /**
     * The file name that this file will be given on Google Code.
     */
    private String targetFileName;
    
    /**
     * Summary of the upload.
     */
    private String summary;
    
    /**
     * Overrides the default upload URL. This parameter is only useful for
     * testing this Ant task without uploading to the live server.
     */
    private String uploadUrl;
    
    /**
     * If set to true, this task will print debugging information to System.out
     * as it progresses through its job.
     */
    private boolean verbose;
    
    /**
     * The labels that the download should have, separated by commas. Extra
     * whitespace before and after each label name will not be considered part
     * of the label name.
     */
    private String labels;
    
	/**
	 * In case of using https connection, some certificate could not be
	 * validated by ssl security layer. To disable the ssl security layer, set
	 * this parameter to true. Use this option only if you understand the risks.
	 */
	private boolean ignoreSslCertificateHostname = false;
	
	public MxGCUpload() {
		super();
		setTaskName("mx:GCUpload");
	}
	
    /**
     * Just calls {@link #upload()}, wrapping any exceptions it might throw in a
     * BuildException.
     */
    @Override
    public void execute() throws BuildException {
        try {
            upload();
        } catch (Exception ex) {
            augmentAndRethrow(ex);
        }
    }

    /**
     * Many developers getting started with this task run into simple problems
     * that they can't solve on their own. These get reported as bugs.
     * <p>
     * This method provides a kind of "context-sensitive FAQ" which can be
     * augmented over time as more easy problems with simple solutions get
     * reported as bugs.
     * 
     * @param ex
     *            The exception that caused this task to fail.
     * @throws BuildException
     *             always; the new exception may have additional hints attached
     *             to it, and will be initialized with <code>ex</code> as its
     *             cause.
     */
    private void augmentAndRethrow(Exception ex) throws BuildException {
        Throwable rootCause = ex;
        while (rootCause.getCause() != null) {
            rootCause = rootCause.getCause();
        }
        String message = rootCause.getMessage();
        
        List<String> hints = new ArrayList<String>();
        if (rootCause instanceof IOException) {
            if (message.matches(".*40[13].*")) {
                hints.add("This is often caused by use of the wrong password. Find your password at http://code.google.com/hosting/settings (you have to be signed in)");
            }
        }
        hints.add("Invoke ant with the \"-v\" argument to see the full stack trace");
        
        StringBuilder hintedMessage = new StringBuilder();
        hintedMessage.append("Original Message: ").append(message);
        for (String hint: hints) {
            hintedMessage.append("\r\nHint: ").append(hint);
        }
        throw new BuildException(hintedMessage.toString(), ex);
    }

    /**
     * Uploads the contents of the file {@link #fileName} to the project's
     * Google Code upload url. Performs the basic http authentication required
     * by Google Code.
     */
    private void upload() throws IOException {
        System.clearProperty("javax.net.ssl.trustStoreProvider"); // fixes open-jdk-issue
        System.clearProperty("javax.net.ssl.trustStoreType");
        
        final String BOUNDARY = "CowMooCowMooCowCowCow";
        URL url = createUploadURL();
        
        log("The upload URL is " + url);
        
        InputStream in = new BufferedInputStream(new FileInputStream(fileName));
        
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        
		if (this.isIgnoreSslCertificateHostname()) {
			if (conn instanceof HttpsURLConnection) {
				HttpsURLConnection secure = (HttpsURLConnection) conn;
				secure.setHostnameVerifier(new HostnameVerifier() {

					@Override
					public boolean verify(String hostname, SSLSession session) {
						boolean result = true;
						log("SSL verification ignored for current session and hostname: "
								+ hostname);
						return result;
					}
				});
			}
		}
        
        conn.setDoOutput(true);
        conn.setRequestProperty("Authorization", "Basic " + createAuthToken(userName, password));
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + BOUNDARY);
        conn.setRequestProperty("User-Agent", "Google Code Upload Ant Task 0.1");
        
		log("Attempting to connect (username is " + userName + ")...");
		conn.connect();
        
        log("Sending request parameters...");
        OutputStream out = conn.getOutputStream();
        sendLine(out, "--" + BOUNDARY);
        sendLine(out, "content-disposition: form-data; name=\"summary\"");
        sendLine(out, "");
        sendLine(out, summary);
        
        if (labels != null) {
            String[] labelArray = labels.split("\\,");
            
            if (labelArray != null && labelArray.length > 0) {
                log("Setting "+labelArray.length+" label(s)");
                
                for (int n = 0, i = labelArray.length; n < i; n++) {
                    sendLine(out, "--" + BOUNDARY);
                    sendLine(out, "content-disposition: form-data; name=\"label\"");
                    sendLine(out, "");
                    sendLine(out, labelArray[n].trim());
                }
            }
        }
        
        log("Sending file... "+targetFileName);
        sendLine(out, "--" + BOUNDARY);
        sendLine(out, "content-disposition: form-data; name=\"filename\"; filename=\"" + targetFileName + "\"");
        sendLine(out, "Content-Type: application/octet-stream");
        sendLine(out, "");
        int count;
        byte[] buf = new byte[8192];
        while ( (count = in.read(buf)) >= 0 ) {
            out.write(buf, 0, count);
        }
        in.close();
        sendLine(out, "");
        sendLine(out, "--" + BOUNDARY + "--");
        
        out.flush();
        out.close();
        
        // For whatever reason, you have to read from the input stream before
        // the url connection will start sending
        in = conn.getInputStream();
        
        log("Upload finished. Reading response.");
        
        log("HTTP Response Headers: " + conn.getHeaderFields());
        StringBuilder responseBody = new StringBuilder();
        while ( (count = in.read(buf)) >= 0 ) {
            responseBody.append(new String(buf, 0, count, "ascii"));
        }
        log(responseBody.toString());
        in.close();
        
        conn.disconnect();
    }

    /**
     * Just sends an ASCII version of the given string, followed by a CRLF line terminator,
     * to the given output stream.
     */
    private void sendLine(OutputStream out, String string) throws IOException {
        out.write(string.getBytes("ascii"));
        out.write("\r\n".getBytes("ascii"));
    }

    /**
     * Creates a (base64-encoded) HTTP basic authentication token for the
     * given user name and password.
     */
    private static String createAuthToken(String userName, String password) {
        String string = (userName + ":" + password);
        try {
            return Base64.encodeBytes(string.getBytes("UTF-8"));
        }
        catch (java.io.UnsupportedEncodingException notreached){
            throw new InternalError(notreached.toString());
        }
    }

    /**
     * Creates the correct URL for uploading to the named google code project.
     * If uploadUrl is not set (this is the standard case), the correct URL will
     * be generated based on the {@link #projectName}.  Otherwise, if uploadUrl
     * is set, it will be used and the project name setting will be ignored.
     */
    private URL createUploadURL() throws MalformedURLException {
        if (uploadUrl != null) {
            return new URL(uploadUrl);
        } else {
            if (projectName == null) {
                throw new NullPointerException("projectName must be set");
            }
            return new URL("https", projectName + ".googlecode.com", "/files");
        }
    }

    
    // ============ Getters and Setters ==============

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getTargetFileName() {
        return targetFileName;
    }

    public void setTargetFileName(String targetFileName) {
        this.targetFileName = targetFileName;
    }

    public String getUploadUrl() {
        return uploadUrl;
    }

    public void setUploadUrl(String uploadUrl) {
        this.uploadUrl = uploadUrl;
    }

    public boolean isVerbose() {
        return verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    public String getLabels() {
        return labels;
    }

    public void setLabels(String labels) {
        this.labels = labels;
    }

	public boolean isIgnoreSslCertificateHostname() {
		return this.ignoreSslCertificateHostname;
	}

	public void setIgnoreSslCertificateHostname(boolean ignoreSslCertHostname) {
		this.ignoreSslCertificateHostname = ignoreSslCertHostname;
	}
}