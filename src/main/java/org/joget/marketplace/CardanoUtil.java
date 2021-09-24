package org.joget.marketplace;

import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.factory.BackendFactory;
import com.bloxbean.cardano.client.backend.impl.blockfrost.common.Constants;
import com.bloxbean.cardano.client.common.model.Network;
import com.bloxbean.cardano.client.common.model.Networks;
import org.joget.commons.util.SecurityUtil;

public class CardanoUtil {
    
    public static final String TESTNET_TX_EXPLORER_URL = "https://explorer.cardano-testnet.iohkdev.io/en/transaction";
    public static final String MAINNET_TX_EXPLORER_URL = "https://explorer.cardano.org/en/transaction";
    
    public static BackendService getBlockfrostBackendService(boolean isTest, String blockfrostProjectKey) {
        if (isTest) {
            return BackendFactory.getBlockfrostBackendService(Constants.BLOCKFROST_TESTNET_URL, blockfrostProjectKey);
        } else {
            return BackendFactory.getBlockfrostBackendService(Constants.BLOCKFROST_MAINNET_URL, blockfrostProjectKey);
        }
    }
    
    public static Network.ByReference getNetwork(boolean isTest) {
        if (isTest) {
            return Networks.testnet();
        } else {
            return Networks.mainnet();
        }
    }
    
    public static String getTransactionExplorerUrl(boolean isTest, String transactionId) {
        String transactionUrl = null;
        
        if (transactionId != null) {
            if (isTest) {
                transactionUrl = TESTNET_TX_EXPLORER_URL;
            } else {
                transactionUrl = MAINNET_TX_EXPLORER_URL;
            }

            transactionUrl += "?id=" + transactionId;
        }
        
        return transactionUrl;
    }
    
    
    //Feel free to implement more secure encryption algo
    public static String encrypt(String content) {
        content = SecurityUtil.encrypt(content);
        
        return content;
    }
    
    //Feel free to implement more secure encryption algo, and decrypt accordingly
    public static String decrypt(String content) {
        content = SecurityUtil.decrypt(content);
        
        return content;
    }
}
