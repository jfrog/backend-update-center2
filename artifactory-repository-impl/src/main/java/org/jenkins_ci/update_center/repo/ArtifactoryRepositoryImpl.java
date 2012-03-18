package org.jenkins_ci.update_center.repo;

import hudson.util.VersionNumber;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.jenkins_ci.update_center.model.GenericArtifactInfo;
import org.jenkins_ci.update_center.model.HudsonWar;
import org.jenkins_ci.update_center.model.PluginHistory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.util.Collection;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Noam Y. Tenne
 */
public class ArtifactoryRepositoryImpl extends MavenRepository {

    private DefaultHttpClient client;

    public ArtifactoryRepositoryImpl() {
        client = new DefaultHttpClient();
    }

    @Override
    public Collection<PluginHistory> listHudsonPlugins() throws IOException {
        return null;
    }

    @Override
    public File resolve(GenericArtifactInfo a, String type, String classifier) throws IOException {
        return null;
    }

    @Override
    protected void listWar(TreeMap<VersionNumber, HudsonWar> r, String groupId, VersionNumber cap) throws IOException {
        Pattern warPathPattern = Pattern.compile("(?:.+)/" +
                StringUtils.replace(StringUtils.replace(groupId, ".", "/"), "-", "\\-") +
                "/([^/]+)/([^/]+)/\\1\\-\\2\\.war");

        String searchUrl = new StringBuilder("http://localhost:8080/artifactory/api/search/gavc?g=")
                .append(URLEncoder.encode(groupId, "utf-8")).append("&a=*war*&repos=libs-release-local").toString();
        HttpResponse searchResponse = client.execute(new HttpGet(searchUrl));
        HttpEntity searchResultEntity = searchResponse.getEntity();
        InputStream searchResultContent = null;
        JSONObject searchResultJSONObject;
        try {
            searchResultContent = searchResultEntity.getContent();
            searchResultJSONObject = JSONObject.fromObject(IOUtils.toString(searchResultContent));
        } finally {
            EntityUtils.consume(searchResultEntity);
            IOUtils.closeQuietly(searchResultContent);
        }
        JSONArray warSearchResults = searchResultJSONObject.getJSONArray("results");
        for (Object warSearchResult : warSearchResults.toArray()) {
            String warInfoUri = ((JSONObject) warSearchResult).getString("uri");
            Matcher warMatcher = warPathPattern.matcher(warInfoUri);
            if (warMatcher.matches()) {
                String version = warMatcher.group(2);
                VersionNumber v = new VersionNumber(version);
                r.put(v, new HudsonWar(new GenericArtifactInfo("", groupId, warMatcher.group(1), version, null)));
            }
        }
    }
}
