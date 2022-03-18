package org.joget.cardano.service;

import java.io.IOException;
import java.util.Properties;
import org.joget.commons.util.LogUtil;
import org.joget.commons.util.SecurityUtil;

public class PluginUtil {
    public static String getProjectVersion(Class classObj) {
        final Properties projectProp = new Properties();
        try {
            projectProp.load(classObj.getClassLoader().getResourceAsStream("project.properties"));
        } catch (IOException ex) {
            LogUtil.error(classObj.getName(), ex, "Unable to get project version from project properties...");
        }
        return projectProp.getProperty("version");
    }

    //Feel free to implement more secure encryption algo, and decrypt accordingly
    public static String decrypt(String content) {
        return SecurityUtil.decrypt(content);
    }

    //Feel free to implement more secure encryption algo
    public static String encrypt(String content) {
        return SecurityUtil.encrypt(content);
    }
}
