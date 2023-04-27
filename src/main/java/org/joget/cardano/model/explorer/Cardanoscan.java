package org.joget.cardano.model.explorer;

import org.joget.cardano.model.NetworkType;
import org.joget.cardano.util.TokenUtil;
import org.joget.commons.util.LogUtil;

public class Cardanoscan implements Explorer {

    private static final String ENDPOINT_MAINNET = "https://cardanoscan.io/";
    private static final String ENDPOINT_PREVIEW = "https://preview.cardanoscan.io/";
    private static final String ENDPOINT_PREPROD = "https://preprod.cardanoscan.io/";
    
    private final String endpointUrl;

    public Cardanoscan(NetworkType networkType) {
        switch (networkType) {
            case MAINNET:
                this.endpointUrl = ENDPOINT_MAINNET;
                break;
            case PREVIEW_TESTNET:
                this.endpointUrl = ENDPOINT_PREVIEW;
                break;
            case PREPROD_TESTNET:
                this.endpointUrl = ENDPOINT_PREPROD;
                break;
            case LEGACY_TESTNET:
            default:
                LogUtil.warn(getClassName(), "Unknown network type found!");
                this.endpointUrl = "";
        }
    }
    
    @Override
    public String getTransactionUrl(String transactionId) {
        //No need to return immediately, in case user wants to show link as is
        if (transactionId == null || transactionId.isBlank()) {
            transactionId = "";
        }
        
        return endpointUrl + "transaction/" + transactionId;
    }

    @Override
    public String getAddressUrl(String accountAddress) {
        //No need to return immediately, in case user wants to show link as is
        if (accountAddress == null || accountAddress.isBlank()) {
            accountAddress = "";
        }
        
        return endpointUrl + "address/" + accountAddress;
    }

    @Override
    public String getPolicyUrl(String policyId) {
        //No need to return immediately, in case user wants to show link as is
        if (policyId == null || policyId.isBlank()) {
            policyId = "";
        }
        
        return endpointUrl + "tokenPolicy/" + policyId;
    }

    @Override
    public String getTokenUrl(String assetId) {
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
        
        return endpointUrl + "token/" + assetFingerprint;
    }
    
    private static String getClassName() {
        return Cardanoscan.class.getName();
    }
}
