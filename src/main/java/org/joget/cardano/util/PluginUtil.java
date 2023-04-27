package org.joget.cardano.util;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;
import org.joget.apps.app.dao.PluginDefaultPropertiesDao;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.model.PluginDefaultProperties;
import org.joget.apps.app.service.AppUtil;
import org.joget.commons.util.LogUtil;
import org.joget.commons.util.SecurityUtil;
import org.joget.commons.util.StringUtil;
import org.joget.plugin.property.service.PropertyUtil;

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
        Map defaultProps = getBackendDefaultConfig();
        if (defaultProps != null) {
            return AppUtil.readPluginResource(className, "/properties/backendConfigAlreadyExists.json");
        }
        
        return forceReadGenericBackendConfigs(className);
    }
    
    public static String forceReadGenericBackendConfigs(String className) {
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
    
    public static Map getBackendDefaultConfig() {
        final String defaultPluginClasspath = "org.joget.cardano.lib.plugindefaultproperties.CardanoDefaultBackendPlugin";
        
        AppDefinition appDef = AppUtil.getCurrentAppDefinition();
        PluginDefaultPropertiesDao pluginDefaultPropertiesDao = (PluginDefaultPropertiesDao) AppUtil.getApplicationContext().getBean("pluginDefaultPropertiesDao");
        PluginDefaultProperties prop = pluginDefaultPropertiesDao.loadById(defaultPluginClasspath, appDef);
        
        if (prop == null) {
            return null;
        }
        
        String json = prop.getPluginProperties();
        json = AppUtil.processHashVariable(json, null, StringUtil.TYPE_JSON, null);
        Map defaultProps = PropertyUtil.getPropertiesValueFromJson(json);
        
        return defaultProps;
    }
}
