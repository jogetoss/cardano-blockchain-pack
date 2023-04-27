package org.joget.cardano.lib.plugindefaultproperties;

import java.util.Map;
import org.joget.apps.app.service.AppUtil;
import org.joget.cardano.util.PluginUtil;
import org.joget.plugin.base.DefaultAuditTrailPlugin;

/**
 * Use this class to store backend configs to reuse throughout entire app.
*/
public class CardanoDefaultBackendPlugin extends DefaultAuditTrailPlugin {
    
    @Override
    public String getName() {
        return "Cardano Default Backend Configurator";
    }

    @Override
    public String getDescription() {
        return "Store backend configurations for the Cardano Blockchain Pack, to be applied throughout an entire app.";
    }
    
    @Override
    public String getPropertyOptions() {
        String backendConfigs = PluginUtil.forceReadGenericBackendConfigs(getClassName());
        return AppUtil.readPluginResource(getClassName(), "/properties/CardanoDefaultBackendPlugin.json", new String[]{backendConfigs}, true, PluginUtil.MESSAGE_PATH);
    }
    
    @Override
    public Object execute(Map props) { return null; } //Do nothing
    
    @Override
    public String getVersion() {
        return PluginUtil.getProjectVersion(this.getClass());
    }
    
    @Override
    public String getLabel() {
        return getName();
    }

    @Override
    public String getClassName() {
        return getClass().getName();
    }
}
