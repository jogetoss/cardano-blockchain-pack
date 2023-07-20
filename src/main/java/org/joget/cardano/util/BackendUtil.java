package org.joget.cardano.util;

import com.bloxbean.cardano.client.backend.api.BackendService;
import static com.bloxbean.cardano.client.backend.blockfrost.common.Constants.BLOCKFROST_MAINNET_URL;
import static com.bloxbean.cardano.client.backend.blockfrost.common.Constants.BLOCKFROST_PREVIEW_URL;
import static com.bloxbean.cardano.client.backend.blockfrost.common.Constants.BLOCKFROST_PREPROD_URL;
import static com.bloxbean.cardano.client.backend.blockfrost.common.Constants.BLOCKFROST_TESTNET_URL;
import static com.bloxbean.cardano.client.backend.koios.Constants.KOIOS_MAINNET_URL;
import static com.bloxbean.cardano.client.backend.koios.Constants.KOIOS_PREVIEW_URL;
import static com.bloxbean.cardano.client.backend.koios.Constants.KOIOS_PREPROD_URL;
import com.bloxbean.cardano.client.backend.blockfrost.service.BFBackendService;
import com.bloxbean.cardano.client.backend.koios.KoiosBackendService;
import java.util.Map;
import org.joget.apps.app.dao.PluginDefaultPropertiesDao;
import org.joget.apps.app.model.PluginDefaultProperties;
import org.joget.apps.app.service.AppUtil;
import org.joget.cardano.lib.plugindefaultproperties.CardanoDefaultBackendPlugin;
import org.joget.cardano.model.NetworkType;
import org.joget.commons.util.LogUtil;
import org.joget.commons.util.StringUtil;
import org.joget.plugin.property.service.PropertyUtil;

public class BackendUtil {
    
    private BackendUtil() {}
    
    private static final String DEFAULT_CONFIG_PLUGIN_CLASSPATH = CardanoDefaultBackendPlugin.class.getCanonicalName();
    
    public static BackendService getBackendService(Map properties) {
        Map defaultProps = getBackendDefaultConfig();
        if (defaultProps != null) {
            properties.putAll(defaultProps);
        }
        String backendServiceName = (String) properties.get("backendService");
        
        final NetworkType networkType = getNetworkType(properties);
        
        switch (backendServiceName) {
            case "blockfrost":
                final String blockfrostProjectKey = (String) properties.get("blockfrostProjectKey");
                return new BFBackendService(getBlockfrostEndpointUrl(networkType), blockfrostProjectKey);
            case "koios":
                return new KoiosBackendService(getKoiosEndpointUrl(networkType));
//            case "ogmios":
//                return new OgmiosBackendService("your_url_here");
            default:
                LogUtil.warn(getClassName(), "Unknown backend selection found!");
                return null;
        }
    }
    
    private static String getBlockfrostEndpointUrl(NetworkType networkType) {
        switch (networkType) {
            case LEGACY_TESTNET:
                return BLOCKFROST_TESTNET_URL;
            case PREPROD_TESTNET:
                return BLOCKFROST_PREPROD_URL;
            case PREVIEW_TESTNET:
                return BLOCKFROST_PREVIEW_URL;
            case MAINNET:
                return BLOCKFROST_MAINNET_URL;
            default:
                LogUtil.warn(getClassName(), "Unknown network selection found!");
                return null;
        }
    }
    
    private static String getKoiosEndpointUrl(NetworkType networkType) {
        switch (networkType) {
            case LEGACY_TESTNET:
                return null; //Not available
            case PREPROD_TESTNET:
                return KOIOS_PREPROD_URL;
            case PREVIEW_TESTNET:
                return KOIOS_PREVIEW_URL;
            case MAINNET:
                return KOIOS_MAINNET_URL;
            default:
                LogUtil.warn(getClassName(), "Unknown network selection found!");
                return null;
        }
    }
    
    public static NetworkType getNetworkType(Map properties) {
        Map defaultProps = getBackendDefaultConfig();
        if (defaultProps != null) {
            properties.putAll(defaultProps);
        }
        return NetworkType.fromString((String) properties.get("networkType"));
    }
    
    public static Map getBackendDefaultConfig() {
        PluginDefaultPropertiesDao pluginDefaultPropertiesDao = (PluginDefaultPropertiesDao) AppUtil.getApplicationContext().getBean("pluginDefaultPropertiesDao");
        PluginDefaultProperties prop = pluginDefaultPropertiesDao.loadById(DEFAULT_CONFIG_PLUGIN_CLASSPATH, AppUtil.getCurrentAppDefinition());
        
        if (prop == null) {
            return null;
        }
        
        return PropertyUtil.getPropertiesValueFromJson(
            AppUtil.processHashVariable(prop.getPluginProperties(), null, StringUtil.TYPE_JSON, null)
        );
    }
    
    private static String getClassName() {
        return BackendUtil.class.getName();
    }
}
