package org.joget.cardano.service;

import com.bloxbean.cardano.client.backend.api.BlockService;
import com.bloxbean.cardano.client.backend.api.TransactionService;
import com.bloxbean.cardano.client.backend.api.helper.model.TransactionResult;
import com.bloxbean.cardano.client.backend.exception.ApiException;
import com.bloxbean.cardano.client.backend.model.Result;
import com.bloxbean.cardano.client.backend.model.TransactionContent;
import com.bloxbean.cardano.client.util.HexUtil;
import java.nio.charset.StandardCharsets;

public class TransactionUtil {

    public static final String MAINNET_TX_EXPLORER_URL = "https://explorer.cardano.org/en/transaction";
    public static final String TESTNET_TX_EXPLORER_URL = "https://explorer.cardano-testnet.iohkdev.io/en/transaction";
    
    public static long getTtl(BlockService blockService) throws ApiException {
        return blockService.getLastestBlock().getValue().getSlot() + 1000;
    }
    
    public static long getTtl(BlockService blockService, int numberOfSlots) throws ApiException {
        return blockService.getLastestBlock().getValue().getSlot() + numberOfSlots;
    }
    
    public static String getAssetId(String policyId, String assetName) {
        return policyId + HexUtil.encodeHexString(assetName.getBytes(StandardCharsets.UTF_8));
    }
    
    public static String getTransactionExplorerUrl(boolean isTest, String transactionId) {
        String transactionUrl = null;
        if (transactionId != null) {
            transactionUrl = isTest ? TESTNET_TX_EXPLORER_URL : MAINNET_TX_EXPLORER_URL;
            transactionUrl += "?id=" + transactionId;
        }
        return transactionUrl;
    }
    
    public static Result<TransactionContent> waitForTransaction(TransactionService transactionService, Result<TransactionResult> transactionResult) throws ApiException, InterruptedException {
        if (transactionResult.isSuccessful()) {
            //Wait for transaction to be mined
            int count = 0;
            while (count < 60) {
                Result<TransactionContent> txnResult = transactionService.getTransaction(transactionResult.getValue().getTransactionId());
                if (txnResult.isSuccessful()) {
                    //LogUtil.info(getClass().getName(), JsonUtil.getPrettyJson(txnResult.getValue()));
                    return txnResult;
                } else {
                    //LogUtil.info(getClass().getName(), "Waiting for transaction to be mined....");
                }

                count++;
                Thread.sleep(2000);
            }
        }
        return null;
    }
}
