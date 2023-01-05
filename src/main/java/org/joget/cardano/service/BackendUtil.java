package org.joget.cardano.service;

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
import com.bloxbean.cardano.client.common.model.Networks;
import java.util.Map;
import org.joget.commons.util.LogUtil;

public class BackendUtil {
    
    public static boolean isTestnet(Map properties) {
        String networkType = (String) properties.get("networkType");
        
        return "testnet".equalsIgnoreCase(networkType) || //Don't delete this, to prevent loss of existing configs from previous versions
                "previewTestnet".equalsIgnoreCase(networkType) || 
                "preprodTestnet".equalsIgnoreCase(networkType);
    }
    
    public static BackendService getBackendService(Map properties) {
        String backendServiceName = (String) properties.get("backendService");
        String blockfrostProjectKey = (String) properties.get("blockfrostProjectKey");
        
        switch (backendServiceName) {
            case "blockfrost":
                return new BFBackendService(getBlockfrostEndpointUrl(properties), blockfrostProjectKey);
            case "koios":
                return new KoiosBackendService(getKoiosEndpointUrl(properties));
//            case "ogmios":
//                return new OgmiosBackendService("your_url_here");
            default:
                LogUtil.warn(BackendUtil.class.getName(), "Unknown backend selection found!");
                return null;
        }
    }
    
    private static String getBlockfrostEndpointUrl(Map properties) {
        String networkType = (String) properties.get("networkType");
        
        switch (networkType) {
            case "testnet":
                return BLOCKFROST_TESTNET_URL;
            case "preprodTestnet":
                return BLOCKFROST_PREPROD_URL;
            case "previewTestnet":
                return BLOCKFROST_PREVIEW_URL;
            case "mainnet":
                return BLOCKFROST_MAINNET_URL;
            default:
                LogUtil.warn(BackendUtil.class.getName(), "Unknown network selection found!");
                return null;
        }
    }
    
    private static String getKoiosEndpointUrl(Map properties) {
        String networkType = (String) properties.get("networkType");
        
        switch (networkType) {
            case "testnet":
            case "preprodTestnet":
                return KOIOS_PREPROD_URL;
            case "previewTestnet":
                return KOIOS_PREVIEW_URL;
            case "mainnet":
                return KOIOS_MAINNET_URL;
            default:
                LogUtil.warn(BackendUtil.class.getName(), "Unknown network selection found!");
                return null;
        }
    }
    
    public static Network getNetwork(Map properties) {
        String networkType = (String) properties.get("networkType");
        
        switch (networkType) {
            case "testnet":
                return Networks.testnet();
            case "preprodTestnet":
                return Networks.preprod();
            case "previewTestnet":
                return Networks.preview();
            case "mainnet":
                return Networks.mainnet();
            default:
                LogUtil.warn(BackendUtil.class.getName(), "Unknown network selection found!");
                return null;
        }
    }
}
