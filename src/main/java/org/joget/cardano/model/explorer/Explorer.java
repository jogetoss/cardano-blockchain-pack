package org.joget.cardano.model.explorer;

public interface Explorer {

    String getTransactionUrl(String transactionId);
    String getAddressUrl(String accountAddress);
    String getPolicyUrl(String policyId);
    String getTokenUrl(String assetId);
    
}
