package id.selzcore.ofbiz.plugins;

import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.service.LocalDispatcher;
import org.keycloak.TokenVerifier;
import org.keycloak.adapters.KeycloakDeployment;
import org.keycloak.adapters.KeycloakDeploymentBuilder;
import java.io.InputStream;
import org.keycloak.adapters.rotation.AdapterTokenVerifier;
import org.keycloak.representations.AccessToken;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;

public class KeycloakAuthFilter implements Filter {

    private String authServerUrl;
    private String realm;
    private String clientId;
    private String clientSecret;
    private boolean sslRequired;

    @Override
    public void init(FilterConfig cfg) {
        String configPath = cfg.getInitParameter("keycloak.config");
        try {
            String ofbizHome = System.getProperty("ofbiz.home");
            if (ofbizHome == null) {
                ofbizHome = System.getenv("OFBIZ_HOME");
            }
            if (ofbizHome == null) {
                throw new ServletException("OFBIZ_HOME not set. Cannot locate Keycloak config.");
            }
            java.io.File configFile = new java.io.File(ofbizHome, configPath);
            if (!configFile.exists()) {
                throw new ServletException("Keycloak config not found: " + configFile.getAbsolutePath());
            }
            javax.xml.parsers.DocumentBuilderFactory dbFactory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
            javax.xml.parsers.DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            org.w3c.dom.Document doc = dBuilder.parse(configFile);
            doc.getDocumentElement().normalize();
            authServerUrl = doc.getElementsByTagName("auth-server-url").item(0).getTextContent();
            realm = doc.getElementsByTagName("realm").item(0).getTextContent();
            clientId = doc.getElementsByTagName("client-id").item(0).getTextContent();
            clientSecret = doc.getElementsByTagName("secret").item(0).getTextContent();
            String sslReq = doc.getElementsByTagName("ssl-required").item(0).getTextContent();
            sslRequired = "true".equalsIgnoreCase(sslReq);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load Keycloak config", e);
        }
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest  request  = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;
        if (sslRequired && !request.isSecure()) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "SSL is required.");
            return;
        }
        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            response.setHeader("WWW-Authenticate", "Bearer realm='keycloak'");
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing Bearer token");
            return;
        }
        String token = auth.substring(7);
        try {
            // Validate token using Keycloak REST API with client secret
            java.net.URL url = new java.net.URL(authServerUrl + "/" + realm + "/protocol/openid-connect/token/introspect");
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            String params = "client_id=" + clientId + "&client_secret=" + clientSecret + "&token=" + token;
            conn.getOutputStream().write(params.getBytes());
            int code = conn.getResponseCode();
            if (code != 200) {
                response.setHeader("WWW-Authenticate", "Bearer error='invalid_token', error_description='Token introspection failed'");
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid token");
                return;
            }
            java.io.InputStream respStream = conn.getInputStream();
            java.util.Scanner s = new java.util.Scanner(respStream).useDelimiter("\\A");
            String result = s.hasNext() ? s.next() : "";
            respStream.close();
            if (!result.contains("\"active\":true")) {
                response.setHeader("WWW-Authenticate", "Bearer error='invalid_token', error_description='Inactive token'");
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Inactive token");
                return;
            }
            // Extract user info (subject)
            String userId = null;
            int subIdx = result.indexOf("\"sub\":");
            if (subIdx > 0) {
                int start = result.indexOf('"', subIdx + 6) + 1;
                int end = result.indexOf('"', start);
                userId = result.substring(start, end);
            }
            if (userId == null) {
                response.setHeader("WWW-Authenticate", "Bearer error='invalid_token', error_description='No subject in token'");
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "No subject in token");
                return;
            }
            LocalDispatcher dispatcher = (LocalDispatcher) request.getServletContext().getAttribute("dispatcher");
            GenericValue userLogin = dispatcher.getDelegator().findOne("UserLogin", java.util.Collections.singletonMap("userLoginId", userId), true);
            if (userLogin == null) {
                userLogin = dispatcher.getDelegator().makeValue("UserLogin");
                userLogin.set("userLoginId", userId);
                userLogin.set("enabled", "Y");
                userLogin.set("hasLoggedOut", "N");
                userLogin.create();
            } else {
                userLogin.set("enabled", "Y");
                userLogin.set("hasLoggedOut", "N");
                userLogin.store();
            }
            request.getSession().setAttribute("userLogin", userLogin);
            chain.doFilter(req, res);
        } catch (Exception e) {
            response.setHeader("WWW-Authenticate", "Bearer error='invalid_token', error_description='" + e.getMessage() + "'");
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid token: " + e.getMessage());
        }
    }

    @Override public void destroy() {}
}