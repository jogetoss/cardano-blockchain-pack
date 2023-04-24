package org.joget.cardano.util;

import org.joget.cardano.model.NetworkType;
import org.joget.commons.util.LogUtil;

public class ExplorerLinkUtil {
    
    private ExplorerLinkUtil() {}
    
    //Default is Cardanoscan for all transaction URLs
    public static String getTransactionExplorerUrl(NetworkType networkType, String transactionId) {
        return getTransactionExplorerUrl(networkType, transactionId, ExplorerType.CARDANOSCAN.value);
    }
    
    public static String getTransactionExplorerUrl(NetworkType networkType, String transactionId, String explorerType) {
        //No need to return immediately, in case user wants to show link as is
        if (transactionId == null || transactionId.isBlank()) {
            transactionId = "";
        }
        
        final ExplorerType explorer = ExplorerType.fromString(explorerType);
        final String explorerUrl = ExplorerEndpoint.getUrl(explorer, networkType);
        
        switch (explorer) {
            case CARDANOSCAN:
                return explorerUrl 
                        + "transaction/"
                        + transactionId;
            case CEXPLORER:
                return explorerUrl 
                        + "tx/"
                        + transactionId;
        }
        
        return null;
    }
    
    public static String getAddressExplorerUrl(NetworkType networkType, String accountAddress, String explorerType) {
        //No need to return immediately, in case user wants to show link as is
        if (accountAddress == null || accountAddress.isBlank()) {
            accountAddress = "";
        }

        final ExplorerType explorer = ExplorerType.fromString(explorerType);
        final String explorerUrl = ExplorerEndpoint.getUrl(explorer, networkType);
        
        switch (explorer) {
            case CARDANOSCAN:
                return explorerUrl 
                        + "address/"
                        + accountAddress;
            case CEXPLORER:
                return explorerUrl 
                        + "address/"
                        + accountAddress;
        }
        
        return null;
    }
    
    public static String getPolicyExplorerUrl(NetworkType networkType, String policyId, String explorerType) {
        //No need to return immediately, in case user wants to show link as is
        if (policyId == null || policyId.isBlank()) {
            policyId = "";
        }

        final ExplorerType explorer = ExplorerType.fromString(explorerType);
        final String explorerUrl = ExplorerEndpoint.getUrl(explorer, networkType);
        
        switch (explorer) {
            case CARDANOSCAN:
                return explorerUrl 
                        + "tokenPolicy/"
                        + policyId;
            case CEXPLORER:
                return explorerUrl 
                        + "policy/"
                        + policyId;
        }
        
        return null;
    }
    
    public static String getTokenExplorerUrl(NetworkType networkType, String assetId, String explorerType) {
        
        String assetFingerprint = "";
        
        //No need to return immediately, in case user wants to show link as is
        if (assetId == null || assetId.isBlank()) {
            assetId = "";
        } else {
            try {
                assetFingerprint = TokenUtil.getAssetFingerprintFromAssetId(assetId);
            } catch (Exception ex) {
                //Ignore if asset ID is malformed
            }
        }

        final ExplorerType explorer = ExplorerType.fromString(explorerType);
        final String explorerUrl = ExplorerEndpoint.getUrl(explorer, networkType);
        
        switch (explorer) {
            case CARDANOSCAN:
                return explorerUrl 
                        + "token/"
                        + assetFingerprint;
            case CEXPLORER:
                return explorerUrl 
                        + "asset/"
                        + assetFingerprint;
        }
        
        return null;
    }
    
    private static String getClassName() {
        return ExplorerLinkUtil.class.getName();
    }
    
    private enum ExplorerEndpoint {
        
        CARDANOSCAN_MAINNET(ExplorerType.CARDANOSCAN, NetworkType.MAINNET, "https://cardanoscan.io/"),
        CARDANOSCAN_PREVIEW_TESTNET(ExplorerType.CARDANOSCAN, NetworkType.PREVIEW_TESTNET, "https://preview.cardanoscan.io/"),
        CARDANOSCAN_PREPROD_TESTNET(ExplorerType.CARDANOSCAN, NetworkType.PREPROD_TESTNET, "https://preprod.cardanoscan.io/"),
        
        CEXPLORER_MAINNET(ExplorerType.CEXPLORER, NetworkType.MAINNET, "https://cexplorer.io/"),
        CEXPLORER_PREVIEW_TESTNET(ExplorerType.CEXPLORER, NetworkType.PREVIEW_TESTNET, "https://preview.cexplorer.io/"),
        CEXPLORER_PREPROD_TESTNET(ExplorerType.CEXPLORER, NetworkType.PREPROD_TESTNET, "https://preprod.cexplorer.io/");
        
        private final ExplorerType explorerType;
        private final NetworkType networkType;
        private final String endpointUrl;
        
        ExplorerEndpoint(ExplorerType explorerType, NetworkType networkType, String endpointUrl) {
            this.explorerType = explorerType;
            this.networkType = networkType;
            this.endpointUrl = endpointUrl;
        }
        
        @Override
        public String toString() {
            return endpointUrl;
        }
        
        public static String getUrl(ExplorerType explorerType, NetworkType networkType) {
            for (ExplorerEndpoint endpoint : ExplorerEndpoint.values()) {
                if ((endpoint.explorerType).equals(explorerType) && (endpoint.networkType).equals(networkType)) {
                    return endpoint.endpointUrl;
                }
            }

            LogUtil.warn(getClassName(), "Unknown endpoint selection found!");
            return null;
        }
    }
    
    private enum ExplorerType {
            
        CARDANOSCAN("cardanoscan"),
        CEXPLORER("cexplorer");

        private final String value;

        ExplorerType(String value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return value;
        }

        public static ExplorerType fromString(String value) {
            for (ExplorerType type : ExplorerType.values()) {
                if ((type.value).equalsIgnoreCase(value)) {
                    return type;
                }
            }

            LogUtil.warn(getClassName(), "Unknown explorer type found!");
            return null;
        }
    }
}
