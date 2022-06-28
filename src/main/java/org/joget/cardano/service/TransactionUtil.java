package org.joget.cardano.service;

import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.helper.model.TransactionResult;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.backend.api.BlockService;
import com.bloxbean.cardano.client.backend.api.TransactionService;
import com.bloxbean.cardano.client.backend.model.TransactionContent;
import com.bloxbean.cardano.client.crypto.SecretKey;
import com.bloxbean.cardano.client.util.HexUtil;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class TransactionUtil {

    public static final String MAINNET_TX_EXPLORER_URL = "https://explorer.cardano.org/en/transaction";
    public static final String TESTNET_TX_EXPLORER_URL = "https://explorer.cardano-testnet.iohkdev.io/en/transaction";
    
    public static final long DEFAULT_WAIT_INTERVAL_MS = 2000;
    
    public static final int DEFAULT_SLOT_TTL = 1000;
    
    public static long getTtl(BlockService blockService) throws ApiException {
        return blockService.getLatestBlock().getValue().getSlot() + DEFAULT_SLOT_TTL;
    }
    
    public static long getTtl(BlockService blockService, int numberOfSlots) throws ApiException {
        return blockService.getLatestBlock().getValue().getSlot() + numberOfSlots;
    }
    
    public static String getAssetId(String policyId, String assetName) {
        return policyId + HexUtil.encodeHexString(assetName.getBytes(StandardCharsets.UTF_8));
    }
    
    //Perhaps allow fully-customizable policy name?
    public static String getFormattedPolicyName(String policyId) {        
        return "mintPolicy-joget-" + policyId.substring(0, 8);
    }
    
    public static String getTransactionExplorerUrl(boolean isTest, String transactionId) {
        if (transactionId == null || transactionId.isEmpty()) {
            return null;
        }

        String transactionUrl = isTest ? TESTNET_TX_EXPLORER_URL : MAINNET_TX_EXPLORER_URL;
        return transactionUrl += "?id=" + transactionId;
    }
    
    // Combine all secret key(s) into string delimited by semicolon (e.g.: skey1;skey2;skey3)
    public static String getSecretKeysAsCborHexStringList(List<SecretKey> skeys) {
        if (skeys == null || skeys.isEmpty()) {
            return null;
        }
        
        return skeys.stream().map(skey -> skey.getCborHex())
				.collect(Collectors.joining(";"));
    }
    
    public static List<SecretKey> getSecretKeysStringAsList(String skeysStringList) {
        if (skeysStringList == null || skeysStringList.trim().isEmpty()) {
            return null;
        }
        
        List<SecretKey> skeys = new ArrayList<>();
        
        for (String skey : skeysStringList.split(";")) {
            skeys.add(new SecretKey(skey.trim()));
        }
        
        return skeys;
    }
    
    public static Result<TransactionContent> waitForTransactionHash(TransactionService transactionService, Result<String> transactionResult) 
            throws ApiException, InterruptedException {
        if (!transactionResult.isSuccessful()) {
            return null;
        }
        
        //Wait for transaction to be mined
        int count = 0;
        while (count < 60) {
            Result<TransactionContent> txnResult = transactionService.getTransaction(transactionResult.getValue());
            if (txnResult.isSuccessful()) {
                //LogUtil.info(getClass().getName(), JsonUtil.getPrettyJson(txnResult.getValue()));
                return txnResult;
            } else {
                //LogUtil.info(getClass().getName(), "Waiting for transaction to be mined....");
            }

            count++;
            Thread.sleep(DEFAULT_WAIT_INTERVAL_MS);
        }
        
        return null;
    }
    
    public static Result<TransactionContent> waitForTransaction(TransactionService transactionService, Result<TransactionResult> transactionResult) 
            throws ApiException, InterruptedException {
        if (!transactionResult.isSuccessful()) {
            return null;
        }
        
        //Wait for transaction to be mined
        int count = 0;
        while (count < 60) {
            Result<TransactionContent> txnResult = transactionService.getTransaction(transactionResult.getValue().getTransactionId());
            if (txnResult.isSuccessful()) {
                return txnResult;
            }

            count++;
            Thread.sleep(DEFAULT_WAIT_INTERVAL_MS);
        }
        
        return null;
    }
}
