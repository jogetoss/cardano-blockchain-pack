package org.joget.cardano.service;

import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.helper.model.TransactionResult;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.backend.api.BlockService;
import com.bloxbean.cardano.client.backend.api.TransactionService;
import com.bloxbean.cardano.client.backend.model.TransactionContent;
import com.bloxbean.cardano.client.crypto.SecretKey;
import com.bloxbean.cardano.client.metadata.cbor.CBORMetadataMap;
import com.bloxbean.cardano.client.util.HexUtil;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.codec.digest.DigestUtils;
import org.joget.apps.form.model.FormRow;

public class TransactionUtil {

    public static final String MAINNET_TX_EXPLORER_URL = "https://explorer.cardano.org/en/transaction";
    public static final String TESTNET_TX_EXPLORER_URL = "https://explorer.cardano-testnet.iohkdev.io/en/transaction";
    
    public static final long DEFAULT_WAIT_TIME_MS = 2000;
    
    public static long getTtl(BlockService blockService) throws ApiException {
        return blockService.getLatestBlock().getValue().getSlot() + 1000;
    }
    
    public static long getTtl(BlockService blockService, int numberOfSlots) throws ApiException {
        return blockService.getLatestBlock().getValue().getSlot() + numberOfSlots;
    }
    
    public static String getAssetId(String policyId, String assetName) {
        return policyId + HexUtil.encodeHexString(assetName.getBytes(StandardCharsets.UTF_8));
    }
    
    //Perhaps allow fully-customizable policy name?
    public static String getFormattedPolicyName(String policyId, String assetName) {
        return "mintPolicy-" + policyId.substring(0, 6) + "-" + assetName;
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
    
    public static CBORMetadataMap generateMetadataMapFromFormData(Object[] metadataFields, FormRow row) {
        if (metadataFields == null || metadataFields.length == 0) {
            return null;
        }
        
        CBORMetadataMap metadataMap = new CBORMetadataMap();
        for (Object o : metadataFields) {
            Map mapping = (HashMap) o;
            String fieldId = mapping.get("fieldId").toString();

//            String isFile = mapping.get("isFile").toString();
//            if ("true".equalsIgnoreCase(isFile)) {
//                String appVersion = appDef.getVersion().toString();
//                String filePath = getFilePath(row.getProperty(fieldId), appDef.getAppId(), appVersion, formDefId, primaryKey);
//                metadataMap.put(fieldId, getFileHashSha256(filePath));
//            } else {
//                metadataMap.put(fieldId, row.getProperty(fieldId));
//            }
            
            metadataMap.put(fieldId, row.getProperty(fieldId));
        }
        
        return metadataMap;
    }
    
    public static String getFilePath(String fileName, String appId, String appVersion, String formDefId, String primaryKeyValue) {
        if (fileName == null || fileName.isEmpty()) {
            return null;
        }
        
        String encodedFileName = fileName;

        try {
            encodedFileName = URLEncoder.encode(fileName, "UTF8").replaceAll("\\+", "%20");
        } catch (UnsupportedEncodingException ex) {
            // ignore
        }

        return "/web/client/app/" + appId + "/" + appVersion + "/form/download/" + formDefId + "/" + primaryKeyValue + "/" + encodedFileName + ".";
    }
    
    public static String getFileHashSha256(String filePath) throws FileNotFoundException, IOException {
        if (filePath == null || filePath.isEmpty()) {
            return null;
        }
        
        return DigestUtils.sha256Hex(new FileInputStream(filePath));
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
            Thread.sleep(DEFAULT_WAIT_TIME_MS);
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
                //LogUtil.info(getClass().getName(), JsonUtil.getPrettyJson(txnResult.getValue()));
                return txnResult;
            } else {
                //LogUtil.info(getClass().getName(), "Waiting for transaction to be mined....");
            }

            count++;
            Thread.sleep(DEFAULT_WAIT_TIME_MS);
        }
        
        return null;
    }
}
