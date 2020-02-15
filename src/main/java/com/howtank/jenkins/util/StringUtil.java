package com.howtank.jenkins.util;

import lombok.experimental.UtilityClass;

import static org.apache.commons.lang3.StringUtils.isNotEmpty;

@UtilityClass
public class StringUtil {

    public static String escapeSpecialCharacter(String input) {
        String output = input;

        if(isNotEmpty(output)) {
            output = output.replace("{", "\\{");
            output = output.replace("}", "\\}");
            output = output.replace("'", "\\'");
        }

        return output;
    }
}
