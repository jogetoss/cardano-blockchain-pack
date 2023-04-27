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
import com.bloxbean.cardano.client.common.model.Network;
import java.util.Map;
import org.joget.cardano.model.NetworkType;
import org.joget.commons.util.LogUtil;

public class BackendUtil {
    
    private BackendUtil() {}
    
    public static BackendService getBackendService(Map properties) {
        Map defaultProps = PluginUtil.getBackendDefaultConfig();
        if (defaultProps != null) {
            properties.putAll(defaultProps);
        }
        String backendServiceName = (String) properties.get("backendService");
        String blockfrostProjectKey = (String) properties.get("blockfrostProjectKey");
        
        final NetworkType networkType = getNetworkType(properties);
        
        switch (backendServiceName) {
            case "blockfrost":
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
        Map defaultProps = PluginUtil.getBackendDefaultConfig();
        if (defaultProps != null) {
            properties.putAll(defaultProps);
        }
        return NetworkType.fromString((String) properties.get("networkType"));
    }
    
    public static NetworkType getNetworkType(Network network) {
        return NetworkType.fromNetwork(network);
    }
    
    private static String getClassName() {
        return BackendUtil.class.getName();
    }
}
