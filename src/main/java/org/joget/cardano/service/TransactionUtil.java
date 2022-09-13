package org.joget.cardano.service;

import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.helper.model.TransactionResult;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.backend.api.BlockService;
import com.bloxbean.cardano.client.backend.api.TransactionService;
import com.bloxbean.cardano.client.backend.model.TransactionContent;
import java.math.BigInteger;
import org.joget.apps.form.service.FormUtil;
import org.joget.commons.util.LogUtil;

public class TransactionUtil {
    
    public static final BigInteger MAX_FEE_LIMIT = BigInteger.valueOf(999999999);
    
    public static final long DEFAULT_WAIT_INTERVAL_MS = 2000;
    
    public static final int DEFAULT_SLOT_TTL = 1000;
    
    public static long getTtl(BlockService blockService) throws ApiException {
        return blockService.getLatestBlock().getValue().getSlot() + DEFAULT_SLOT_TTL;
    }
    
    public static long getTtl(BlockService blockService, int numberOfSlots) throws ApiException {
        return blockService.getLatestBlock().getValue().getSlot() + numberOfSlots;
    }
    
    public static boolean isTransactionIdExist(TransactionService transactionService, String transactionId) throws ApiException {
        //If within a Form Builder, don't make useless API calls
        if (transactionId == null || transactionId.isBlank() || FormUtil.isFormBuilderActive()) {
            return false;
        }
        
        try {
            return transactionService.getTransaction(transactionId).isSuccessful();
        } catch (Exception ex) {
            //Ignore if not successful
        }
        
        return false;
    }
    
    public static boolean checkFeeLimit(BigInteger fee, BigInteger limit) {
        if (fee == null || limit == null) {
            return false;
        }
        
        return fee.compareTo(limit) <= 0;
    }
    
    public static Result<TransactionContent> waitForTransaction(TransactionService transactionService, Result transactionResult) 
            throws ApiException, InterruptedException {
        if (!transactionResult.isSuccessful()) {
            return null;
        }
        
        String transactionId;
        
        if (transactionResult.getValue() instanceof TransactionResult) {
            TransactionResult txResultObj = (TransactionResult) transactionResult.getValue();
            transactionId = txResultObj.getTransactionId();
        } else if (transactionResult.getValue() instanceof String) {
            String txResultObj = (String) transactionResult.getValue();
            transactionId = txResultObj;
        } else {
            LogUtil.warn(TransactionUtil.class.getName(), "Something went wrong. Unknown tx result type found!");
            return null;
        }
        
        //Wait for transaction to be mined
        int count = 0;
        while (count < 60) {
            Result<TransactionContent> txnResult = transactionService.getTransaction(transactionId);
            if (txnResult.isSuccessful()) {
                //LogUtil.info(getClass().getName(), JsonUtil.getPrettyJson(txnResult.getValue()));
                return txnResult;
            }

            count++;
            Thread.sleep(DEFAULT_WAIT_INTERVAL_MS);
        }
        
        return null;
    }
}
