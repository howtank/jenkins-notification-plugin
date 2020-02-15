package com.howtank.jenkins.util;

import com.cloudbees.plugins.credentials.CredentialsMatcher;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import hudson.security.ACL;
import jenkins.model.Jenkins;
import lombok.experimental.UtilityClass;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;

import java.util.Collections;
import java.util.List;

@UtilityClass
public class CredentialUtil {

    public static StringCredentials lookupCredentials(String credentialId) {
        List<StringCredentials> credentials = CredentialsProvider.lookupCredentials(
                StringCredentials.class,
                Jenkins.getInstanceOrNull(),
                ACL.SYSTEM,
                Collections.<DomainRequirement>emptyList());

        CredentialsMatcher matcher = CredentialsMatchers.withId(credentialId);
        return CredentialsMatchers.firstOrNull(credentials, matcher);
    }
}