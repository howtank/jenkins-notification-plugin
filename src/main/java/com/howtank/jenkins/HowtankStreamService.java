package com.howtank.jenkins;

import hudson.FilePath;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import lombok.extern.java.Log;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.apache.http.client.utils.URIBuilder;
import org.jenkinsci.plugins.tokenmacro.TokenMacro;

import static io.restassured.RestAssured.with;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;

@Log
public class HowtankStreamService {
    private static final String HOWTANK_BASE_API_URL = "https://www.howtank.com/api/v4";

    private HowtankStreamsNotification howtankStreamsNotification;
    private TaskListener taskListener;
    private FilePath ws;

    public HowtankStreamService(HowtankStreamsNotification howtankStreamsNotification, TaskListener taskListener, FilePath ws) {
        this.howtankStreamsNotification = howtankStreamsNotification;
        this.taskListener = taskListener;
        this.ws = ws;
    }

    public Boolean sendNotification(Run run) {

        String streamId = howtankStreamsNotification.getStreamId();
        String message = this.extractMessageFromJobExecution(run);
        Response response = this.publishHowtankStreamMessage(streamId, message);

        log.info("Howtank Notification Response: " + response.print());

        if(taskListener != null) {
            taskListener.getLogger().println("Howtank Notification Response: " + response.print());
        }

        return response.statusCode() == HttpStatus.SC_OK;
    }


    String buildHowtankApiCommandQuery(String streamId, String message) {
        URIBuilder uriBuilder = new URIBuilder();
        uriBuilder.addParameter("command", "add_stream_message");
        uriBuilder.addParameter("stream_id", streamId);
        uriBuilder.addParameter("local_id", "0");
        uriBuilder.addParameter("type", "group_chat");
        uriBuilder.addParameter("content", message);

        return uriBuilder.getQueryParams().toString();
    }

    Response publishHowtankStreamMessage(String streamId, String json) {
        final String queryParam = buildHowtankApiCommandQuery(streamId, json);

        return with()
                .baseUri(HOWTANK_BASE_API_URL)
                .contentType(ContentType.JSON)
                .queryParam(queryParam)
                .urlEncodingEnabled(false)
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

        if(build != null && build.getResult() != null) {
            if(howtankStreamsNotification.isNotifyAborted()
                    && Result.ABORTED.toString().equalsIgnoreCase(build.getResult().toString())) {

                result = true;

            } else if(howtankStreamsNotification.isNotifyBackToNormal()
                    && ( ( Result.ABORTED.toString().equalsIgnoreCase(build.getPreviousBuild().getResult().toString())
                    || Result.FAILURE.toString().equalsIgnoreCase(build.getPreviousBuild().getResult().toString())
                    || Result.UNSTABLE.toString().equalsIgnoreCase(build.getPreviousBuild().getResult().toString())
                    || Result.NOT_BUILT.toString().equalsIgnoreCase(build.getPreviousBuild().getResult().toString())
            ) && Result.SUCCESS.toString().equalsIgnoreCase(build.getResult().toString())
            )
            ) {

                result = true;

            } else if(howtankStreamsNotification.isNotifyFailure()
                    && Result.FAILURE.toString().equalsIgnoreCase(build.getResult().toString())) {

                result = true;

            } else if(howtankStreamsNotification.isNotifyNotBuilt()
                    && Result.NOT_BUILT.toString().equalsIgnoreCase(build.getResult().toString())) {

                result = true;
            } else if(howtankStreamsNotification.isNotifySuccess()
                    && Result.SUCCESS.toString().equalsIgnoreCase(build.getResult().toString())) {

                result = true;

            } else if(howtankStreamsNotification.isNotifyUnstable()
                    && Result.UNSTABLE.toString().equalsIgnoreCase(build.getResult().toString())) {

                result = true;
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
