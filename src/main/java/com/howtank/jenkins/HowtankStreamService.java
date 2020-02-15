package com.howtank.jenkins;

import hudson.FilePath;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.apache.http.client.utils.URIBuilder;
import org.jenkinsci.plugins.tokenmacro.TokenMacro;

import java.net.URISyntaxException;
import java.util.logging.Level;

import static io.restassured.RestAssured.with;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;

@Log
@RequiredArgsConstructor
public class HowtankStreamService {
    private static final String HOWTANK_BASE_API_URL = "https://www.howtank.com/api/v4";

    private final HowtankStreamsNotification howtankStreamsNotification;
    private final TaskListener taskListener;
    private final FilePath ws;
    private final String accessToken;

    public Boolean sendNotification(Run run) {

        String streamId = howtankStreamsNotification.getStreamId();
        String message = this.extractMessageFromJobExecution(run);

        try {
            Response response = this.publishHowtankStreamMessage(streamId, message);

            log.info("Howtank Notification Response: " + response.print());

            if(taskListener != null) {
                taskListener.getLogger().println("Howtank Notification Response: " + response.print());
            }

            return response.statusCode() == HttpStatus.SC_OK;

        } catch (URISyntaxException e) {
            log.log(Level.SEVERE, e.getMessage(), e);
            return false;
        }
    }


    String buildHowtankApiCommandQuery(String streamId, String message) throws URISyntaxException {
        URIBuilder uriBuilder = new URIBuilder();
        uriBuilder.addParameter("command", "add_stream_message");
        uriBuilder.addParameter("stream_id", streamId);
        uriBuilder.addParameter("local_id", "0");
        uriBuilder.addParameter("type", "group_chat");
        uriBuilder.addParameter("content", message);

        return uriBuilder.build().getRawQuery();
    }

    Response publishHowtankStreamMessage(String streamId, String json) throws URISyntaxException {
        final String queryParam = buildHowtankApiCommandQuery(streamId, json);

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

    String extractMessageFromJobExecution(Run build) {
        return escapeSpecialCharacter(replaceJenkinsKeywords(howtankStreamsNotification.getMessage(), build));
    }

    public String replaceJenkinsKeywords(String inputString, Run build) {

        if(StringUtils.isEmpty(inputString)) {
            return inputString;
        }

        try {

            if(taskListener != null) {
                taskListener.getLogger().println("ws: " + ws + " , build: " + build);
            }

            return TokenMacro.expandAll(build, ws, taskListener, inputString, false, null);
        } catch (Exception e) {
            if(taskListener != null) {
                taskListener.getLogger().println("Exception in Token Macro expansion: " + e);
            }
        }
        return inputString;
    }

    public boolean checkWhetherToSend(Run build) {
        boolean result = false;

        if(build != null) {
            Result currentBuildResult = build.getResult();
            Run previousBuild = build.getPreviousBuild();
            Result previousBuildResult = null;

            if (previousBuild != null) {
                previousBuildResult = previousBuild.getResult();
            }

            if(currentBuildResult != null) {
                if (howtankStreamsNotification.isNotifyAborted()
                        && Result.ABORTED.toString().equalsIgnoreCase(currentBuildResult.toString())) {

                    result = true;

                } else if (previousBuildResult != null && howtankStreamsNotification.isNotifyBackToNormal()
                        && ((Result.ABORTED.toString().equalsIgnoreCase(previousBuildResult.toString())
                        || Result.FAILURE.toString().equalsIgnoreCase(previousBuildResult.toString())
                        || Result.UNSTABLE.toString().equalsIgnoreCase(previousBuildResult.toString())
                        || Result.NOT_BUILT.toString().equalsIgnoreCase(previousBuildResult.toString())
                ) && Result.SUCCESS.toString().equalsIgnoreCase(currentBuildResult.toString())
                )
                ) {

                    result = true;

                } else if (howtankStreamsNotification.isNotifyFailure()
                        && Result.FAILURE.toString().equalsIgnoreCase(currentBuildResult.toString())) {

                    result = true;

                } else if (howtankStreamsNotification.isNotifyNotBuilt()
                        && Result.NOT_BUILT.toString().equalsIgnoreCase(currentBuildResult.toString())) {

                    result = true;
                } else if (howtankStreamsNotification.isNotifySuccess()
                        && Result.SUCCESS.toString().equalsIgnoreCase(currentBuildResult.toString())) {

                    result = true;

                } else if (howtankStreamsNotification.isNotifyUnstable()
                        && Result.UNSTABLE.toString().equalsIgnoreCase(currentBuildResult.toString())) {

                    result = true;
                }
            }
        }
        return result;
    }

    public boolean checkPipelineFlag(Run build) {
        if(!howtankStreamsNotification.isNotifyAborted() &&
                !howtankStreamsNotification.isNotifyBackToNormal() &&
                !howtankStreamsNotification.isNotifyFailure() &&
                !howtankStreamsNotification.isNotifyNotBuilt() &&
                !howtankStreamsNotification.isNotifySuccess() &&
                !howtankStreamsNotification.isNotifyUnstable()) {
            return true;
        }
        return checkWhetherToSend(build);
    }

    public String escapeSpecialCharacter(String input) {

        String output = input;

        if(isNotEmpty(output)) {
            output = output.replace("{", "\\{");
            output = output.replace("}", "\\}");
            output = output.replace("'", "\\'");
        }

        return output;
    }
}
