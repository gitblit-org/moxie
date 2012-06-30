package org.moxie.proxy;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import org.moxie.utils.StringUtils;
import org.restlet.Request;
import org.restlet.Response;
import org.restlet.data.ChallengeScheme;
import org.restlet.security.ChallengeAuthenticator;
import org.restlet.security.SecretVerifier;

public class MxAuthenticator extends ChallengeAuthenticator {

	final Logger logger = Logger.getLogger(getClass().getSimpleName());
	final Main app;
	
	public MxAuthenticator(Main app) {
		super(null, ChallengeScheme.HTTP_BASIC, Constants.getName());
		this.app = app;
		setVerifier(new CredentialsVerifier());
	}
	
    @Override
    protected boolean authenticate(Request request, Response response) {
    	boolean authenticateRequest = false;
    	if (authenticateRequest) {
    		return super.authenticate(request, response);	
    	}
    	return true;
    }
    
    private class CredentialsVerifier extends SecretVerifier {
    	
		int timeout = 60*1000;
		Map<String, Date> authenticated = new HashMap<String, Date>();
		
		@Override
		public int verify(String username, char[] secret) {
			String password = new String(secret);
			String hash = StringUtils.getSHA1(username + password);
			Date date = new Date(0);
			if (authenticated.containsKey(hash)) {
				date = authenticated.get(hash);
			}
			if (timeout >= (System.currentTimeMillis() - date.getTime())) {
				// user has authenticated recently
				return RESULT_VALID;
			}
			// Authenticate user
			if (app.authenticate(username, password)) {
				authenticated.put(hash, new Date());
				return RESULT_VALID;
			}
			return RESULT_INVALID;
		}
	};
}
