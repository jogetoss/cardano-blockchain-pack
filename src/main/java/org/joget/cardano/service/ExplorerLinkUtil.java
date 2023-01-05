package org.joget.cardano.service;

import org.joget.commons.util.LogUtil;

public class ExplorerLinkUtil {
    
    private static final String CARDANOSCAN_TYPE = "cardanoscan";
    private static final String CEXPLORER_TYPE = "cexplorer";
    
    //Cardano Explorer Links
    private static final String CARDANOSCAN_MAINNET = "https://cardanoscan.io/";
    private static final String CARDANOSCAN_PREVIEW_TESTNET = "https://preview.cardanoscan.io/";
    private static final String CARDANOSCAN_PREPROD_TESTNET = "https://preprod.cardanoscan.io/";
    
    private static final String CEXPLORER_MAINNET = "https://cexplorer.io/";
    private static final String CEXPLORER_PREVIEW_TESTNET = "https://preview.cexplorer.io/";
    private static final String CEXPLORER_PREPROD_TESTNET = "https://preprod.cexplorer.io/";
    
    private static String getCardanoscanUrl(String networkType) {
        switch (networkType) {
            case "testnet":
            case "preprodTestnet":
                return CARDANOSCAN_PREPROD_TESTNET;
            case "previewTestnet":
                return CARDANOSCAN_PREVIEW_TESTNET;
            case "mainnet":
                return CARDANOSCAN_MAINNET;
            default:
                LogUtil.warn(ExplorerLinkUtil.class.getName(), "Unknown network selection found!");
                return null;
        }
    }
    
    private static String getCexplorerUrl(String networkType) {
        switch (networkType) {
            case "testnet":
            case "preprodTestnet":
                return CEXPLORER_PREPROD_TESTNET;
            case "previewTestnet":
                return CEXPLORER_PREVIEW_TESTNET;
            case "mainnet":
                return CEXPLORER_MAINNET;
            default:
                LogUtil.warn(ExplorerLinkUtil.class.getName(), "Unknown network selection found!");
                return null;
        }
    }
    
    //Default is Cardanoscan for all transaction URLs
    public static String getTransactionExplorerUrl(String networkType, String transactionId) {
        return getTransactionExplorerUrl(networkType, transactionId, CARDANOSCAN_TYPE);
    }
    
    public static String getTransactionExplorerUrl(String networkType, String transactionId, String explorerType) {
        //No need to return immediately, in case user wants to show link as is
        if (transactionId == null || transactionId.isBlank()) {
            transactionId = "";
        }

        String explorerUrl = "";
        
        switch (explorerType) {
            case CARDANOSCAN_TYPE:
                explorerUrl = getCardanoscanUrl(networkType);
                explorerUrl += "transaction/";
                break;
            case CEXPLORER_TYPE:
                explorerUrl = getCexplorerUrl(networkType);
                explorerUrl += "tx/";
                break;
        }
        
        explorerUrl += transactionId;
        
        return explorerUrl;
    }
    
    public static String getAddressExplorerUrl(String networkType, String accountAddress, String explorerType) {
        //No need to return immediately, in case user wants to show link as is
        if (accountAddress == null || accountAddress.isBlank()) {
            accountAddress = "";
        }

        String explorerUrl = "";
        
        switch (explorerType) {
            case CARDANOSCAN_TYPE:
                explorerUrl = getCardanoscanUrl(networkType);
                explorerUrl += "address/";
                break;
            case CEXPLORER_TYPE:
                explorerUrl = getCexplorerUrl(networkType);
                explorerUrl += "address/";
                break;
        }
        
        explorerUrl += accountAddress;
        
        return explorerUrl;
    }
    
    public static String getPolicyExplorerUrl(String networkType, String policyId, String explorerType) {
        //No need to return immediately, in case user wants to show link as is
        if (policyId == null || policyId.isBlank()) {
            policyId = "";
        }

        String explorerUrl = "";
        
        switch (explorerType) {
            case CARDANOSCAN_TYPE:
                explorerUrl = getCardanoscanUrl(networkType);
                explorerUrl += "tokenPolicy/";
                break;
            case CEXPLORER_TYPE:
                explorerUrl = getCexplorerUrl(networkType);
                explorerUrl += "policy/";
                break;
        }
        
        explorerUrl += policyId;
        
        return explorerUrl;
    }
    
    public static String getTokenExplorerUrl(String networkType, String assetId, String explorerType) {
        
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

        String explorerUrl = "";
        
        switch (explorerType) {
            case CARDANOSCAN_TYPE:
                explorerUrl = getCardanoscanUrl(networkType);
                explorerUrl += "token/";
                break;
            case CEXPLORER_TYPE:
                explorerUrl = getCexplorerUrl(networkType);
                explorerUrl += "asset/";
                break;
        }
        
        explorerUrl += assetFingerprint;
        
        return explorerUrl;
    }
}
