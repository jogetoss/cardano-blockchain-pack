package org.joget.cardano.model;

import com.bloxbean.cardano.client.common.model.Network;
import com.bloxbean.cardano.client.common.model.Networks;
import org.joget.commons.util.LogUtil;

/**
 * Carries info about Cardano network
 */
public enum NetworkType {
    
    MAINNET("mainnet", Networks.mainnet(), false),
    PREVIEW_TESTNET("previewTestnet", Networks.preview(), true),
    PREPROD_TESTNET("preprodTestnet", Networks.preprod(), true),
    LEGACY_TESTNET("testnet", Networks.testnet(), true);
    
    private final String value;
    private final Network network;
    private final boolean isTestNetwork;
    
    NetworkType(String value, Network network, boolean isTestNetwork) {
        this.value = value;
        this.network = network;
        this.isTestNetwork = isTestNetwork;
    }
    
    @Override
    public String toString() {
        return value;
    }
    
    public Network getNetwork() {
        return this.network;
    }
    
    public boolean isTestNetwork() {
        return this.isTestNetwork;
    }
    
    public static NetworkType fromString(String text) {
        for (NetworkType type : NetworkType.values()) {
            if ((type.value).equalsIgnoreCase(text)) {
                return type;
            }
        }
        
        LogUtil.warn(getClassName(), "Unknown network type found!");
        return null;
    }
    
    public static NetworkType fromNetwork(Network network) {
        for (NetworkType type : NetworkType.values()) {
            if ((type.network).equals(network)) {
                return type;
            }
        }
        
        LogUtil.warn(getClassName(), "Unknown network type found!");
        return null;
    }
    
    private static String getClassName() {
        return NetworkType.class.getName();
    }
}
