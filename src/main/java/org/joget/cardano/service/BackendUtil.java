package org.joget.cardano.service;

import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.factory.BackendFactory;
import com.bloxbean.cardano.client.backend.gql.GqlBackendService;
import com.bloxbean.cardano.client.backend.impl.blockfrost.common.Constants;
import com.bloxbean.cardano.client.common.model.Network;
import com.bloxbean.cardano.client.common.model.Networks;
import java.io.InputStream;
import java.util.Map;
import java.util.Properties;
import org.joget.commons.util.LogUtil;
import org.joget.commons.util.SecurityUtil;

public class BackendUtil {
    
    public static final String TESTNET_DANDELION_BACKEND = "https://graphql-api.testnet.dandelion.link/";
    public static final String MAINNET_DANDELION_BACKEND = "https://graphql-api.mainnet.dandelion.link/";
    
    public static BackendService getBackendService(Map properties) {
        String networkType = (String) properties.get("networkType");
        boolean isTest = "testnet".equalsIgnoreCase(networkType);
        
        String backendServiceName = (String) properties.get("backendService");
        String blockfrostProjectKey = (String) properties.get("blockfrostProjectKey");
        String graphqlEndpointUrl = (String) properties.get("graphqlEndpointUrl");
        
        BackendService backend;
        
        switch (backendServiceName) {
            case "blockfrost":
                backend = getBlockfrostBackendService(isTest ? Constants.BLOCKFROST_TESTNET_URL : Constants.BLOCKFROST_MAINNET_URL, blockfrostProjectKey);
                break;
            case "customGraphQl":
                backend = getGqlBackendService(graphqlEndpointUrl);
                break;
            default:
                final String dandelionGql = getDedicatedDandelionGqlBackend(isTest);
                if (!dandelionGql.isEmpty()) {
                    backend = getGqlBackendService(dandelionGql);
                } else {
                    backend = getGqlBackendService(isTest ? TESTNET_DANDELION_BACKEND : MAINNET_DANDELION_BACKEND);
                }
                break;
        }
        
        return backend;
    }
    
    private static String getDedicatedDandelionGqlBackend(boolean isTest) {
        final Properties secureProp = new Properties();
        
        String result = "";
        
        try {
            InputStream inputStream = BackendUtil.class.getClassLoader().getResourceAsStream("secure.properties");
            secureProp.load(inputStream);
            
            result = isTest ? secureProp.getProperty("d-dandelion-gql-testnet") : secureProp.getProperty("d-dandelion-gql-mainnet");
            
            result = SecurityUtil.decrypt(result);
        } catch (Exception ex) {
            LogUtil.debug(BackendUtil.class.getName(), "Unable to get secure properties. Fallback to community endpoints.");
        }
        
        return result;
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
