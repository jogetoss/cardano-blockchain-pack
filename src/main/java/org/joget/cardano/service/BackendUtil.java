package org.joget.cardano.service;

import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.factory.BackendFactory;
import com.bloxbean.cardano.client.backend.gql.GqlBackendService;
import com.bloxbean.cardano.client.backend.impl.blockfrost.common.Constants;
import com.bloxbean.cardano.client.common.model.Network;
import com.bloxbean.cardano.client.common.model.Networks;
import java.util.Map;

public class BackendUtil {
    
    public static final String TESTNET_DANDELION_BACKEND = "https://graphql-api.testnet.dandelion.link/";
    public static final String MAINNET_DANDELION_BACKEND = "https://graphql-api.mainnet.dandelion.link/";
    
    public static BackendService getBackendService(Map properties) {
        String networkType = (String) properties.get("networkType");
        boolean isTest = "testnet".equalsIgnoreCase(networkType);
        
        String backendServiceName = (String) properties.get("backendService");
        String blockfrostProjectKey = (String) properties.get("blockfrostProjectKey");
        String graphqlEndpointUrl = (String) properties.get("graphqlEndpointUrl");
        
        if ("blockfrost".equalsIgnoreCase(backendServiceName)) {
            return getBlockfrostBackendService(isTest ? Constants.BLOCKFROST_TESTNET_URL : Constants.BLOCKFROST_MAINNET_URL, blockfrostProjectKey);
        } else if ("customGraphQl".equalsIgnoreCase(backendServiceName)) {
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
}
