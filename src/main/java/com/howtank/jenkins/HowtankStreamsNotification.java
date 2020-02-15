package com.howtank.jenkins;

import com.howtank.jenkins.service.HowtankStreamService;
import com.howtank.jenkins.util.CredentialUtil;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import hudson.util.FormValidation;
import jenkins.tasks.SimpleBuildStep;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.java.Log;
import net.sf.json.JSONObject;
import org.apache.commons.lang3.StringUtils;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.jenkinsci.plugins.tokenmacro.TokenMacro;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import javax.annotation.Nonnull;

import static com.howtank.jenkins.util.StringUtil.escapeSpecialCharacter;

@Getter
@Setter
@Log
public class HowtankStreamsNotification extends Notifier implements SimpleBuildStep {
    private final String streamId;
    private final String message;
    private final String accessToken;

    private TaskListener taskListener;
    private FilePath ws;
    private Run build;

    @DataBoundSetter
    private boolean notifyAborted;

    @DataBoundSetter
    private boolean notifyFailure;

    @DataBoundSetter
    private boolean notifyNotBuilt;

    @DataBoundSetter
    private boolean notifySuccess;

    @DataBoundSetter
    private boolean notifyUnstable;

    @DataBoundSetter
    private boolean notifyBackToNormal;


    @DataBoundConstructor
    public HowtankStreamsNotification(String streamId, String message, String accessToken) {
        this.streamId = streamId;
        this.message = message;
        this.accessToken = accessToken;
    }

    @Override
    public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) {
        this.setBuild(build);
        this.setWs(null);
        this.setTaskListener(listener);

        performAction(build);
        return true;
    }

    @Override
    public void perform(@Nonnull Run<?, ?> run, @Nonnull FilePath workspace, @Nonnull Launcher launcher, @Nonnull TaskListener listener) {
        this.setBuild(run);
        this.setWs(workspace);
        this.setTaskListener(listener);

        performAction(run);
    }

    @Override
    public Descriptor getDescriptor() {
        return (Descriptor)super.getDescriptor();
    }

    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.BUILD;
    }

    public String getStreamId() {
        if(streamId == null || streamId.equals("")) {
            return getDescriptor().getStreamId();
        } else {
            return streamId;
        }
    }

    public String getMessage() {
        if(message == null || message.equals("")) {
            return getDescriptor().getMessage();
        } else {
            return message;
        }
    }

    public String getAccessToken() {
        if(accessToken == null || accessToken.equals("")) {
            return getDescriptor().getAccessToken();
        } else {
            return accessToken;
        }
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
                if (this.isNotifyAborted()
                        && Result.ABORTED.toString().equalsIgnoreCase(currentBuildResult.toString())) {

                    result = true;

                } else if (previousBuildResult != null && this.isNotifyBackToNormal()
                        && ((Result.ABORTED.toString().equalsIgnoreCase(previousBuildResult.toString())
                        || Result.FAILURE.toString().equalsIgnoreCase(previousBuildResult.toString())
                        || Result.UNSTABLE.toString().equalsIgnoreCase(previousBuildResult.toString())
                        || Result.NOT_BUILT.toString().equalsIgnoreCase(previousBuildResult.toString())
                ) && Result.SUCCESS.toString().equalsIgnoreCase(currentBuildResult.toString())
                )
                ) {

                    result = true;

                } else if (this.isNotifyFailure()
                        && Result.FAILURE.toString().equalsIgnoreCase(currentBuildResult.toString())) {

                    result = true;

                } else if (this.isNotifyNotBuilt()
                        && Result.NOT_BUILT.toString().equalsIgnoreCase(currentBuildResult.toString())) {

                    result = true;
                } else if (this.isNotifySuccess()
                        && Result.SUCCESS.toString().equalsIgnoreCase(currentBuildResult.toString())) {

                    result = true;

                } else if (this.isNotifyUnstable()
                        && Result.UNSTABLE.toString().equalsIgnoreCase(currentBuildResult.toString())) {

                    result = true;
                }
            }
        }
        return result;
    }

    private boolean checkPipelineFlag(Run build) {
        if(!this.isNotifyAborted() &&
                !this.isNotifyBackToNormal() &&
                !this.isNotifyFailure() &&
                !this.isNotifyNotBuilt() &&
                !this.isNotifySuccess() &&
                !this.isNotifyUnstable()) {
            return true;
        }
        return checkWhetherToSend(build);
    }

    private void performAction(Run run) {
        String plainAccessToken = null;

        if (this.accessToken.startsWith("id:")) {
            StringCredentials stringCredentials = CredentialUtil.lookupCredentials(this.accessToken);

            if (stringCredentials != null) {
                plainAccessToken = stringCredentials.getSecret().getPlainText();
            }
        } else {
            plainAccessToken = this.accessToken;
        }

        if (plainAccessToken == null) {
            log.severe("Invalid or missing access token provided.");
        } else {
            HowtankStreamService howtankStreamService = new HowtankStreamService(
                    this,
                    this.taskListener,
                    this.ws,
                    plainAccessToken);

            boolean sendNotificationFlag = checkPipelineFlag(run);

            if (sendNotificationFlag) {
                howtankStreamService.sendNotification(this.extractMessageFromJobExecution(run), this.streamId);
            } else {
                log.info("Notification will not be sent to Howtank for this build");
            }
        }
    }

    private String extractMessageFromJobExecution(Run build) {
        return escapeSpecialCharacter(replaceJenkinsKeywords(this.getMessage(), build));
    }

    private String replaceJenkinsKeywords(String inputString, Run build) {
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

    @Symbol("howtankNotification")
    @Getter
    @Extension
    public static class Descriptor extends BuildStepDescriptor<Publisher> {
        private String streamId;
        private String message;
        private String accessToken;
        private boolean notifyAborted;
        private boolean notifyFailure;
        private boolean notifyNotBuilt;
        private boolean notifySuccess;
        private boolean notifyUnstable;
        private boolean notifyBackToNormal;

        public Descriptor() {
            load();
        }

        public FormValidation doCheckStreamId(@QueryParameter String value) {
            if(value.length() == 0) {
                return FormValidation.error("Please add at least one Howtank Stream ID");
            }

            return FormValidation.ok();
        }

        public FormValidation doCheckMessage(@QueryParameter String value) {
            if(value.length() == 0) {
                return FormValidation.error("Please add message");
            }

            return FormValidation.ok();
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "Howtank Stream Notification";
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            streamId = formData.getString("streamId");
            message = formData.getString("message");
            accessToken = formData.getString("accessToken");
            notifyAborted = formData.getBoolean("notifyAborted");
            notifyFailure = formData.getBoolean("notifyFailure");
            notifyNotBuilt = formData.getBoolean("notifyNotBuilt");
            notifySuccess = formData.getBoolean("notifySuccess");
            notifyUnstable = formData.getBoolean("notifyUnstable");
            notifyBackToNormal = formData.getBoolean("notifyBackToNormal");

            save();
            return super.configure(req,formData);
        }
    }
}

