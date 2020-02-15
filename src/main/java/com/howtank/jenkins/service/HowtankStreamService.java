package com.howtank.jenkins.service;

import com.howtank.jenkins.HowtankStreamsNotification;
import hudson.FilePath;
import hudson.model.TaskListener;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import org.apache.http.HttpStatus;
import org.apache.http.client.utils.URIBuilder;

import java.net.URISyntaxException;
import java.util.logging.Level;

import static io.restassured.RestAssured.with;

@Log
@RequiredArgsConstructor
public class HowtankStreamService {
    private static final String HOWTANK_BASE_API_URL = "https://www.howtank.com/api/v4";

    private final HowtankStreamsNotification howtankStreamsNotification;
    private final TaskListener taskListener;
    private final FilePath ws;
    private final String accessToken;

    public Boolean sendNotification(String message, String streamId) {
        try {
            Response response = this.publishHowtankStreamMessage(streamId, message);

            log.info("Howtank Notification Response: " + response.print());

            if (taskListener != null) {
                taskListener.getLogger().println("Howtank Notification Response: " + response.print());
            }

            return response.statusCode() == HttpStatus.SC_OK;
        } catch (URISyntaxException e) {
            log.log(Level.SEVERE, e.getMessage(), e);
        }

        return false;
    }

    String buildHowtankApiCommandQuery(String streamId, String message) throws URISyntaxException {
        URIBuilder uriBuilder = new URIBuilder();
        uriBuilder.addParameter("command", "add_stream_message");
        uriBuilder.addParameter("stream_id", streamId);
        uriBuilder.addParameter("local_id", "0");
        uriBuilder.addParameter("mode", "expert");
        uriBuilder.addParameter("type", "group_chat");
        uriBuilder.addParameter("content", message);

        return uriBuilder.build().getRawQuery();
    }

    Response publishHowtankStreamMessage(String streamId, String json) throws URISyntaxException {
        final String queryParam = buildHowtankApiCommandQuery(streamId, json);

        log.info("Pushing stream message to: " + HOWTANK_BASE_API_URL + "?" + queryParam);

        return with()
                .baseUri(HOWTANK_BASE_API_URL)
                .contentType(ContentType.JSON)
                .queryParam(queryParam)
                .urlEncodingEnabled(false)
                .header("Authorization", "Bearer " + accessToken)
                .body(json)
            .log().all()
            .post();
    }


}
