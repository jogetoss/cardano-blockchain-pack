package org.joget.cardano.util;

import java.io.IOException;
import java.util.Properties;
import org.joget.apps.app.service.AppUtil;
import org.joget.commons.util.LogUtil;
import org.joget.commons.util.SecurityUtil;

public class PluginUtil {
    
    private PluginUtil() {}
    
    public static final String MESSAGE_PATH = "messages/CardanoMessages";
    
    public static final String FORM_ELEMENT_CATEGORY = "Cardano";
    
    public static String getProjectVersion(Class classObj) {
        final Properties projectProp = new Properties();
        try {
            projectProp.load(classObj.getClassLoader().getResourceAsStream("project.properties"));
        } catch (IOException ex) {
            LogUtil.error(classObj.getName(), ex, "Unable to get project version from project properties...");
        }
        return projectProp.getProperty("version");
    }

    public static String readGenericBackendConfigs(String className) {
        return AppUtil.readPluginResource(className, "/properties/genericBackendConfigs.json");
    }
    
    public static String readGenericWorkflowVariableMappings(String className) {
        return AppUtil.readPluginResource(className, "/properties/genericWfVarMappings.json");
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
