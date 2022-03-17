package org.joget.marketplace;

import java.io.IOException;
import java.util.Properties;
import org.joget.commons.util.LogUtil;

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
}
