package org.jenkins_ci.update_center.repo;

import hudson.util.VersionNumber;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.ClientContext;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.util.EntityUtils;
import org.jenkins_ci.update_center.model.GenericArtifactInfo;
import org.jenkins_ci.update_center.model.HPI;
import org.jenkins_ci.update_center.model.HudsonWar;
import org.jenkins_ci.update_center.model.PluginHistory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Noam Y. Tenne
 */
public class ArtifactoryRepositoryImpl extends MavenRepository {

    private static String REPO_URL = "http://repo.jenkins-ci.org";
    private static List<String> QUERY_REPO_KEYS = new ArrayList<String>();
    private static String RESOLVE_REPO_KEY = "public";

    private DefaultHttpClient client;
    private HttpHost targetHost = new HttpHost("repo.jenkins-ci.org", 80, "http");
    private BasicHttpContext localcontext;

    public ArtifactoryRepositoryImpl() {
        client = new DefaultHttpClient();
        QUERY_REPO_KEYS.add("releases");
        QUERY_REPO_KEYS.add("javanet2-cache");
        QUERY_REPO_KEYS.add("maven.jenkins-ci.org-cache");
    }

    @Override
    public void setCredentials(String username, String password) {
        super.setCredentials(username, password);
        if (StringUtils.isNotBlank(username)) {

            client.getCredentialsProvider().setCredentials(
                    new AuthScope(targetHost.getHostName(), targetHost.getPort()),
                    new UsernamePasswordCredentials(username, password));

            // Create AuthCache instance
            AuthCache authCache = new BasicAuthCache();
            // Generate BASIC scheme object and add it to the local auth cache
            BasicScheme basicAuth = new BasicScheme();
            authCache.put(targetHost, basicAuth);

            // Add AuthCache to the execution context
            localcontext = new BasicHttpContext();
            localcontext.setAttribute(ClientContext.AUTH_CACHE, authCache);
        }
    }

    @Override
    public File resolve(GenericArtifactInfo a, String type, String classifier) throws IOException {
        StringBuilder filePathBuilder = new StringBuilder(StringUtils.replace(a.groupId, ".", "/")).append("/")
                .append(a.artifactId).append("/").append(a.version).append("/").append(a.artifactId).append("-")
                .append(a.version);
        if (StringUtils.isNotBlank(classifier)) {
            filePathBuilder.append("-").append(classifier);
        }
        String filePath = filePathBuilder.append(".").append(type).toString();
        File resolvedFilePath = new File(new File(System.getProperty("user.home"), ".m2/repository"), filePath);
        if (resolvedFilePath.exists()) {
            return resolvedFilePath;
        }
        HttpResponse fileGetResponse = client.execute(targetHost,
                new HttpGet(REPO_URL + "/" + RESOLVE_REPO_KEY + "/" + filePath), localcontext);
        HttpEntity fileEntity = fileGetResponse.getEntity();
        StatusLine fileGetStatus = fileGetResponse.getStatusLine();
        if (HttpStatus.SC_OK != fileGetStatus.getStatusCode()) {
            System.out.println("Unable to download file: " + filePath);
            EntityUtils.consume(fileEntity);
            return null;
        }
        InputStream fileContent = null;
        try {
            fileContent = fileEntity.getContent();
            if (!resolvedFilePath.getParentFile().exists()) {
                resolvedFilePath.getParentFile().mkdirs();
            }
            if (!resolvedFilePath.exists()) {
                resolvedFilePath.createNewFile();
            }
            IOUtils.copy(fileContent, new FileOutputStream(resolvedFilePath));
        } finally {
            EntityUtils.consume(fileEntity);
            IOUtils.closeQuietly(fileContent);
        }

        return resolvedFilePath;
    }

    @Override
    protected void listWar(TreeMap<VersionNumber, HudsonWar> r, String groupId, VersionNumber cap) throws IOException {
        Pattern warPathPattern = Pattern.compile("(?:.+)/([^/]+)/" +
                StringUtils.replace(StringUtils.replace(groupId, ".", "/"), "-", "\\-") +
                "/([^/]+)/([^/]+)/\\2\\-\\3\\.war");

        StringBuilder searchUrlBuilder = new StringBuilder(REPO_URL).append("/api/search/gavc?g=")
                .append(URLEncoder.encode(groupId, "utf-8")).append("&a=*war*&repos=");
        Iterator<String> repoKeyIterator = QUERY_REPO_KEYS.iterator();
        while (repoKeyIterator.hasNext()) {
            searchUrlBuilder.append(repoKeyIterator.next());
            if (repoKeyIterator.hasNext()) {
                searchUrlBuilder.append(",");
            }
        }
        HttpResponse searchResponse = client.execute(targetHost, new HttpGet(searchUrlBuilder.toString()),
                localcontext);
        HttpEntity searchResultEntity = searchResponse.getEntity();
        StatusLine statusLine = searchResponse.getStatusLine();
        if (HttpStatus.SC_OK != statusLine.getStatusCode()) {
            System.out.println("Unable to find WAR files with the group ID '" + groupId + "': " + statusLine);
            EntityUtils.consume(searchResultEntity);
            return;
        }
        JSONArray warSearchResults = getSearchResultArray(searchResultEntity);
        for (Object warSearchResult : warSearchResults.toArray()) {
            String warInfoUri = ((JSONObject) warSearchResult).getString("uri");
            Matcher warMatcher = warPathPattern.matcher(warInfoUri);
            if (warMatcher.matches()) {
                HudsonWar warInfo = new HudsonWar(
                        new GenericArtifactInfo(warMatcher.group(1), groupId, warMatcher.group(2), warMatcher.group(3),
                                null, "war"));
                if (isWarValid(warInfo, cap)) {
                    VersionNumber v = new VersionNumber(warInfo.version);
                    r.put(v, warInfo);
                }
            }
        }
    }

    @Override
    protected void listHudsonPlugins(Map<String, PluginHistory> plugins) throws IOException {
        Pattern hpiPathPattern = Pattern.compile("(?:.+)/api/storage/([^/]+)/(.+?)/([^/]+)/([^/]+)/\\3\\-\\4\\.hpi");

        StringBuilder searchUrlBuilder = new StringBuilder(REPO_URL).append("/api/search/artifact?name=*.hpi")
                .append("&repos=");
        Iterator<String> repoKeyIterator = QUERY_REPO_KEYS.iterator();
        while (repoKeyIterator.hasNext()) {
            searchUrlBuilder.append(repoKeyIterator.next());
            if (repoKeyIterator.hasNext()) {
                searchUrlBuilder.append(",");
            }
        }
        HttpResponse searchResponse = client.execute(targetHost, new HttpGet(searchUrlBuilder.toString()),
                localcontext);
        HttpEntity searchResultEntity = searchResponse.getEntity();
        StatusLine statusLine = searchResponse.getStatusLine();
        if (HttpStatus.SC_OK != statusLine.getStatusCode()) {
            System.out.println("Unable to find HPI files: " + statusLine);
            EntityUtils.consume(searchResultEntity);
            return;
        }
        JSONArray hpiSearchResults = getSearchResultArray(searchResultEntity);
        for (Object hpiSearchResult : hpiSearchResults.toArray()) {
            String hpiInfoUri = ((JSONObject) hpiSearchResult).getString("uri");
            Matcher hpiMatcher = hpiPathPattern.matcher(hpiInfoUri);
            if (hpiMatcher.matches()) {
                HPI hpiInfo = new HPI(
                        new GenericArtifactInfo(hpiMatcher.group(1), StringUtils.replace(hpiMatcher.group(2), "/", "."),
                                hpiMatcher.group(3), hpiMatcher.group(4), null, "hpi"));
                if (isHpiValid(hpiInfo)) {
                    PluginHistory p = plugins.get(hpiInfo.artifact.artifactId);
                    if (p == null) {
                        plugins.put(hpiInfo.artifact.artifactId, p = new PluginHistory(hpiInfo.artifact.artifactId));
                    }
                    p.addArtifact(hpiInfo);
                    p.groupId.add(hpiInfo.artifact.groupId);
                }
            }
        }
    }

    private JSONArray getSearchResultArray(HttpEntity searchResultEntity) throws IOException {
        InputStream searchResultContent = null;
        JSONObject searchResultJSONObject;
        try {
            searchResultContent = searchResultEntity.getContent();
            searchResultJSONObject = JSONObject.fromObject(IOUtils.toString(searchResultContent));
        } finally {
            EntityUtils.consume(searchResultEntity);
            IOUtils.closeQuietly(searchResultContent);

        }
        return searchResultJSONObject.getJSONArray("results");
    }
}
