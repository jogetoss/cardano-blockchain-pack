package org.joget.cardano.service;

public class ExplorerLinkUtil {
    
    private static final String NATIVE_TYPE = "native";
    private static final String CARDANOSCAN_TYPE = "cardanoscan";
    private static final String CEXPLORER_TYPE = "cexplorer";
    
    //Cardano Explorer Links
    private static final String NATIVE_MAINNET = "https://explorer.cardano.org/en/";
    private static final String NATIVE_TESTNET = "https://explorer.cardano-testnet.iohkdev.io/en/";
    
    /* Cardanoscan currently only supports preprod testnet, due to funding. */
    private static final String CARDANOSCAN_MAINNET = "https://cardanoscan.io/";
    private static final String CARDANOSCAN_PREVIEW_TESTNET = "https://testnet.cardanoscan.io/";
    private static final String CARDANOSCAN_PREPROD_TESTNET = "https://testnet.cardanoscan.io/";
    
    private static final String CEXPLORER_MAINNET = "https://cexplorer.io/";
//    private static final String CEXPLORER_LEGACY_TESTNET = "https://testnet.cexplorer.io/";
     private static final String CEXPLORER_PREVIEW_TESTNET = "https://preview.cexplorer.io/";
    private static final String CEXPLORER_PREPROD_TESTNET = "https://preprod.cexplorer.io/";
   
    public static String getTransactionExplorerUrl(boolean isTest, String transactionId) {
        return getTransactionExplorerUrl(isTest, transactionId, "native");
    }
    
    public static String getTransactionExplorerUrl(boolean isTest, String transactionId, String explorerType) {
        //No need to return immediately, in case user wants to show link as is
        if (transactionId == null || transactionId.isBlank()) {
            transactionId = "";
        }

        String explorerUrl;
        
        switch (explorerType) {
            case CARDANOSCAN_TYPE:
                explorerUrl = isTest ? CARDANOSCAN_PREVIEW_TESTNET : CARDANOSCAN_MAINNET;
                explorerUrl += "transaction/";
                break;
            case CEXPLORER_TYPE:
                explorerUrl = isTest ? CEXPLORER_PREVIEW_TESTNET : CEXPLORER_MAINNET;
                explorerUrl += "tx/";
                break;
            case NATIVE_TYPE:
            default:
                explorerUrl = isTest ? NATIVE_TESTNET : NATIVE_MAINNET;
                explorerUrl += "transaction?id=";
                break;
        }
        
        explorerUrl += transactionId;
        
        return explorerUrl;
    }
    
    public static String getAddressExplorerUrl(boolean isTest, String accountAddress, String explorerType) {
        //No need to return immediately, in case user wants to show link as is
        if (accountAddress == null || accountAddress.isBlank()) {
            accountAddress = "";
        }

        String explorerUrl;
        
        switch (explorerType) {
            case CARDANOSCAN_TYPE:
                explorerUrl = isTest ? CARDANOSCAN_PREVIEW_TESTNET : CARDANOSCAN_MAINNET;
                explorerUrl += "address/";
                break;
            case CEXPLORER_TYPE:
                explorerUrl = isTest ? CEXPLORER_PREVIEW_TESTNET : CEXPLORER_MAINNET;
                explorerUrl += "address/";
                break;
            case NATIVE_TYPE:
            default:
                explorerUrl = isTest ? NATIVE_TESTNET : NATIVE_MAINNET;
                explorerUrl += "address?address=";
                break;
        }
        
        explorerUrl += accountAddress;
        
        return explorerUrl;
    }
    
    public static String getPolicyExplorerUrl(boolean isTest, String policyId, String explorerType) {
        //No need to return immediately, in case user wants to show link as is
        if (policyId == null || policyId.isBlank()) {
            policyId = "";
        }

        String explorerUrl;
        
        switch (explorerType) {
            case CARDANOSCAN_TYPE:
                explorerUrl = isTest ? CARDANOSCAN_PREVIEW_TESTNET : CARDANOSCAN_MAINNET;
                explorerUrl += "tokenPolicy/";
                break;
            case CEXPLORER_TYPE:
                explorerUrl = isTest ? CEXPLORER_PREVIEW_TESTNET : CEXPLORER_MAINNET;
                explorerUrl += "policy/";
                break;
            case NATIVE_TYPE:
            default:
                //Native not supported
                return "";
        }
        
        explorerUrl += policyId;
        
        return explorerUrl;
    }
    
    public static String getTokenExplorerUrl(boolean isTest, String assetId, String explorerType) {
        
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

        String explorerUrl;
        
        switch (explorerType) {
            case CARDANOSCAN_TYPE:
                explorerUrl = isTest ? CARDANOSCAN_PREVIEW_TESTNET : CARDANOSCAN_MAINNET;
                explorerUrl += "token/";
                break;
            case CEXPLORER_TYPE:
                explorerUrl = isTest ? CEXPLORER_PREVIEW_TESTNET : CEXPLORER_MAINNET;
                explorerUrl += "asset/";
                break;
            case NATIVE_TYPE:
            default:
                //Native not supported
                return "";
        }
        
        explorerUrl += assetFingerprint;
        
        return explorerUrl;
    }
}
