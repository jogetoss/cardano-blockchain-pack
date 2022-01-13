package org.joget.marketplace;

import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.factory.BackendFactory;
import com.bloxbean.cardano.client.backend.gql.GqlBackendService;
import com.bloxbean.cardano.client.backend.impl.blockfrost.common.Constants;
import com.bloxbean.cardano.client.common.model.Network;
import com.bloxbean.cardano.client.common.model.Networks;
import java.util.Map;
import org.joget.commons.util.SecurityUtil;

public class CardanoUtil {
    
    public static final String TESTNET_TX_EXPLORER_URL = "https://explorer.cardano-testnet.iohkdev.io/en/transaction";
    public static final String MAINNET_TX_EXPLORER_URL = "https://explorer.cardano.org/en/transaction";
    
    public static final String TESTNET_DANDELION_BACKEND = "https://graphql-api.testnet.dandelion.link/";
    public static final String MAINNET_DANDELION_BACKEND = "https://graphql-api.mainnet.dandelion.link/";
    
    public static BackendService getBackendService(Map properties) {
        String networkType = getPropertyString(properties, "networkType");
        boolean isTest = "testnet".equalsIgnoreCase(networkType);
        
        String backendServiceName = getPropertyString(properties, "backendService");
        String blockfrostProjectKey = getPropertyString(properties, "blockfrostProjectKey");
        String graphqlEndpointUrl = getPropertyString(properties, "graphqlEndpointUrl");
        
        if (backendServiceName.equalsIgnoreCase("blockfrost")) {
            return getBlockfrostBackendService(isTest ? Constants.BLOCKFROST_TESTNET_URL : Constants.BLOCKFROST_MAINNET_URL, blockfrostProjectKey);
        } else if (backendServiceName.equalsIgnoreCase("customGraphQl")) {
            return getGqlBackendService(graphqlEndpointUrl);
        } else {
            return getGqlBackendService(isTest ? TESTNET_DANDELION_BACKEND : MAINNET_DANDELION_BACKEND);
        }
    }
    
    private static BackendService getBlockfrostBackendService(String blockfrostEndpointUrl, String blockfrostProjectKey) {
        return BackendFactory.getBlockfrostBackendService(blockfrostEndpointUrl, blockfrostProjectKey);
    }
    
    private static BackendService getGqlBackendService(String graphqlEndpointUrl) {
        return new GqlBackendService(graphqlEndpointUrl);
    }
    
    public static Network.ByReference getNetwork(boolean isTest) {
        return isTest ? Networks.testnet() : Networks.mainnet();
    }
    
    public static String getTransactionExplorerUrl(boolean isTest, String transactionId) {
        String transactionUrl = null;
        
        if (transactionId != null) {
            transactionUrl = isTest ? TESTNET_TX_EXPLORER_URL : MAINNET_TX_EXPLORER_URL;
            transactionUrl += "?id=" + transactionId;
        }
        
        return transactionUrl;
    }
    
    
    //Feel free to implement more secure encryption algo
    public static String encrypt(String content) {
        return SecurityUtil.encrypt(content);
    }
    
    //Feel free to implement more secure encryption algo, and decrypt accordingly
    public static String decrypt(String content) {
        return SecurityUtil.decrypt(content);
    }
    
    private static String getPropertyString(Map properties, String property) {
        return (properties != null && properties.get(property) != null) ? (String) properties.get(property) : "";
    }
}
