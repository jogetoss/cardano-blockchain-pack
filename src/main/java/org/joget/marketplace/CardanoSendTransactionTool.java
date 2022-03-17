package org.joget.marketplace;

import java.math.BigDecimal;
import java.util.Map;
import org.joget.apps.app.service.AppUtil;
import org.joget.commons.util.LogUtil;
import org.joget.plugin.base.DefaultApplicationPlugin;
import org.joget.workflow.model.WorkflowAssignment;
import org.joget.workflow.model.service.WorkflowManager;
import org.springframework.context.ApplicationContext;
import org.joget.workflow.util.WorkflowUtil;
import com.bloxbean.cardano.client.common.model.Network;
import com.bloxbean.cardano.client.transaction.model.PaymentTransaction;
import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.api.BlockService;
import com.bloxbean.cardano.client.backend.api.TransactionService;
import com.bloxbean.cardano.client.backend.api.helper.FeeCalculationService;
import com.bloxbean.cardano.client.backend.api.helper.TransactionHelperService;
import com.bloxbean.cardano.client.backend.api.helper.model.TransactionResult;
import com.bloxbean.cardano.client.backend.exception.ApiException;
import com.bloxbean.cardano.client.backend.model.Result;
import com.bloxbean.cardano.client.backend.model.TransactionContent;
import com.bloxbean.cardano.client.common.ADAConversionUtil;
import static com.bloxbean.cardano.client.common.CardanoConstants.LOVELACE;
import com.bloxbean.cardano.client.exception.AddressExcepion;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.metadata.Metadata;
import com.bloxbean.cardano.client.metadata.cbor.CBORMetadata;
import com.bloxbean.cardano.client.metadata.cbor.CBORMetadataMap;
import com.bloxbean.cardano.client.transaction.model.TransactionDetailsParams;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.net.URLEncoder;
import java.util.HashMap;
import org.apache.commons.codec.digest.DigestUtils;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.service.AppService;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.commons.util.PluginThread;

public class CardanoSendTransactionTool extends DefaultApplicationPlugin {

    @Override
    public String getName() {
        return "Cardano Send Transaction Tool";
    }

    @Override
    public String getVersion() {
        return PluginUtil.getProjectVersion(this.getClass());
    }

    @Override
    public String getDescription() {
        return "Send funds from one account to another on the Cardano blockchain, with optional transaction metadata.";
    }

    @Override
    public Object execute(Map props) {
        Object result = null;
        
        Metadata metadata = null;
        WorkflowAssignment wfAssignment = (WorkflowAssignment) props.get("workflowAssignment");
        
        ApplicationContext ac = AppUtil.getApplicationContext();
        final WorkflowManager workflowManager = (WorkflowManager) ac.getBean("workflowManager");
        
        final String senderAddress = WorkflowUtil.processVariable(getPropertyString("senderAddress"), "", wfAssignment);
        final String accountMnemonic = CardanoUtil.decrypt(WorkflowUtil.processVariable(getPropertyString("accountMnemonic"), "", wfAssignment));
        final String receiverAddress = WorkflowUtil.processVariable(getPropertyString("receiverAddress"), "", wfAssignment);
        final String amount = WorkflowUtil.processVariable(getPropertyString("amount"), "", wfAssignment);
        
        try {
            String networkType = getPropertyString("networkType");
            boolean isTest = "testnet".equalsIgnoreCase(networkType);
            
            final Network.ByReference network = CardanoUtil.getNetwork(isTest);
            final BackendService backendService = CardanoUtil.getBackendService(getProperties());
            
            final Account senderAccount = new Account(network, accountMnemonic);
            
            if (!senderAddress.equals(senderAccount.baseAddress())) {
                LogUtil.warn(getClass().getName(), "Transaction failed! Sender account encountered invalid mnemonic phrase.");
                return null;
            }
            
            AppService appService = (AppService) AppUtil.getApplicationContext().getBean("appService");
            final String primaryKey = appService.getOriginProcessId(wfAssignment.getProcessId());
            
            //Optional insert metadata from form data
            if ("true".equals(getPropertyString("enableMetadata"))) {                
                String formDefId = getPropertyString("formDefId");
                Object[] metadataFields = (Object[]) props.get("metadata");
                
                AppDefinition appDef = (AppDefinition) props.get("appDef");
                FormRow row = new FormRow();
                FormRowSet rowSet = appService.loadFormData(appDef.getAppId(), appDef.getVersion().toString(), formDefId, primaryKey);
                if (!rowSet.isEmpty()) {
                    row = rowSet.get(0);
                }
                
                CBORMetadataMap metadataMap = new CBORMetadataMap();
                for (Object o : metadataFields) {
                    Map mapping = (HashMap) o;
                    String fieldId = mapping.get("fieldId").toString();
                    
//                    String isFile = mapping.get("isFile").toString();
//                    if ("true".equalsIgnoreCase(isFile)) {
//                        String appVersion = appDef.getVersion().toString();
//                        String filePath = getFilePath(row.getProperty(fieldId), appDef.getAppId(), appVersion, formDefId, primaryKey);
//                        metadataMap.put(fieldId, getFileHashSha256(filePath));
//                    } else {
//                        metadataMap.put(fieldId, row.getProperty(fieldId));
//                    }
                    
                    metadataMap.put(fieldId, row.getProperty(fieldId));
                }
                
                CBORMetadata binaryMetadata = new CBORMetadata();
                
                binaryMetadata.put(BigInteger.ZERO, metadataMap);
                
                metadata = binaryMetadata;
            }
            
            BlockService blockService = backendService.getBlockService();
            TransactionHelperService transactionHelperService = backendService.getTransactionHelperService();
            
            long ttl = blockService.getLastestBlock().getValue().getSlot() + 1000;
            TransactionDetailsParams detailsParams = TransactionDetailsParams.builder().ttl(ttl).build();
            
            final PaymentTransaction paymentTransaction = constructPayment(backendService, detailsParams, metadata, senderAccount, receiverAddress, amount);
            
            final Result<TransactionResult> transactionResult = transactionHelperService.transfer(paymentTransaction, detailsParams, metadata);
            
            //Store successful unvalidated txn result first
            storeToWorkflowVariable(wfAssignment.getActivityId(), workflowManager, props, isTest, transactionResult, null);
            
            //Workaround for waiting validation in separate thread
            final boolean isTestFinal = isTest;
            
            Thread waitTransactionThread = new PluginThread(() -> {
                
                Result<TransactionContent> validatedTransactionResult = null;
                
                if (transactionResult.isSuccessful()) {
                    validatedTransactionResult = waitForTransaction(backendService, transactionResult);
                } else {
                    LogUtil.warn(getClass().getName(), "Transaction failed with status code " + transactionResult.code() + ". Response returned --> " + transactionResult.getResponse());
                }
                
                //Store validated/confirmed txn result for current activity instance
                storeToWorkflowVariable(wfAssignment.getActivityId(), workflowManager, props, isTestFinal, transactionResult, validatedTransactionResult);
                
                //Store validated/confirmed txn result for future running activity instance
                storeToWorkflowVariable(workflowManager.getRunningActivityIdByRecordId(primaryKey, wfAssignment.getProcessDefId(), null, null), workflowManager, props, isTestFinal, transactionResult, validatedTransactionResult);
            });
            waitTransactionThread.start();
            
            return result;
        } catch (Exception ex) {
            LogUtil.error(getClass().getName(), ex, "Error executing plugin...");
            return null;
        }
    }
    
    protected Result<TransactionContent> waitForTransaction(BackendService backendService, Result<TransactionResult> transactionResult) {
        try {
            if (transactionResult.isSuccessful()) {
                //Wait for transaction to be mined
                TransactionService transactionService = backendService.getTransactionService();
                
                int count = 0;
                while (count < 60) {
                    Result<TransactionContent> txnResult = transactionService.getTransaction(transactionResult.getValue().getTransactionId());
                    if (txnResult.isSuccessful()) {
//                        LogUtil.info(getClass().getName(), JsonUtil.getPrettyJson(txnResult.getValue()));
                        return txnResult;
                    } else {
                        //LogUtil.info(getClass().getName(), "Waiting for transaction to be mined....");
                    }

                    count++;
                    Thread.sleep(2000);
                }
                return null;
            }
            return null;
        } catch (Exception ex) {
            LogUtil.error(getClass().getName(), ex, "Error waiting for transaction validation...");
            return null;
        }
    }
    
    protected PaymentTransaction constructPayment(BackendService backendService, TransactionDetailsParams detailsParams, Metadata metadata, Account senderAccount, String receiverAddress, String amount) throws ApiException, CborSerializationException, AddressExcepion {
        PaymentTransaction paymentTransaction =
            PaymentTransaction.builder()
                    .sender(senderAccount)
                    .receiver(receiverAddress)
                    .amount(getPaymentAmount(new BigDecimal(amount)))
                    .unit(getPaymentUnit())
                    .build();
        
        final BigInteger fee = calculateFeeFromTransactionSize(backendService, detailsParams, paymentTransaction, metadata);
        
        paymentTransaction.setFee(fee);
        
        return paymentTransaction;
    }
    
    protected BigInteger getPaymentAmount(BigDecimal amount) {
        return getPropertyString("paymentUnit").equalsIgnoreCase(LOVELACE) ? ADAConversionUtil.adaToLovelace(amount) : amount.toBigInteger();
    }
    
    protected String getPaymentUnit() {
        return getPropertyString("paymentUnit").equalsIgnoreCase(LOVELACE) ? LOVELACE : getPropertyString("policyId");
    }
    
    protected BigInteger calculateFeeFromTransactionSize(BackendService backendService, TransactionDetailsParams detailsParams, PaymentTransaction paymentTransaction, Metadata metadata) throws ApiException, CborSerializationException, AddressExcepion {
        FeeCalculationService feeCalculationService = backendService.getFeeCalculationService();
        
        //Calculate fee from transaction size in bytes
        return feeCalculationService.calculateFee(paymentTransaction, detailsParams, metadata);
    }
    
    protected void storeToWorkflowVariable(
            String activityId,
            WorkflowManager workflowManager,
            Map properties, 
            boolean isTest, 
            Result<TransactionResult> transactionResult, 
            Result<TransactionContent> validatedtransactionResult) {
        
        String transactionValidatedVar = getPropertyString("wfTransactionValidated");
        String transactionIdVar = getPropertyString("wfTransactionId");
        String transactionUrlVar = getPropertyString("wfTransactionExplorerUrl");

        storeValuetoActivityVar(
                workflowManager, 
                activityId, 
                transactionValidatedVar, 
                validatedtransactionResult != null ? String.valueOf(validatedtransactionResult.isSuccessful()) : "false"
        );
        storeValuetoActivityVar(
                workflowManager, 
                activityId, 
                transactionIdVar, 
                transactionResult.getValue().getTransactionId()
        );
        storeValuetoActivityVar(
                workflowManager, 
                activityId, 
                transactionUrlVar, 
                CardanoUtil.getTransactionExplorerUrl(isTest, transactionResult.getValue().getTransactionId())
        );
    }
    
    private void storeValuetoActivityVar(WorkflowManager workflowManager, String activityId, String variable, String value) {
        if (!variable.isEmpty() && value != null) {
            workflowManager.activityVariable(activityId, variable, value);
        }
    }
    
    protected String getFilePath(String fileName, String appId, String appVersion, String formDefId, String primaryKeyValue) {
        String filePath = null;
        
        if (fileName != null && !fileName.isEmpty()) {
            String encodedFileName = fileName;
            
            try {
                encodedFileName = URLEncoder.encode(fileName, "UTF8").replaceAll("\\+", "%20");
            } catch (UnsupportedEncodingException ex) {
                // ignore
            }
            
            filePath = "/web/client/app/" + appId + "/" + appVersion + "/form/download/" + formDefId + "/" + primaryKeyValue + "/" + encodedFileName + ".";
        }
        
        return filePath;
    }
    
    protected String getFileHashSha256(String filePath) throws FileNotFoundException, IOException {
        if (filePath != null && !filePath.isEmpty()) {
            return DigestUtils.sha256Hex(new FileInputStream(filePath));
        }
        return null;
    }
    
    @Override
    public String getLabel() {
        return getName();
    }

    @Override
    public String getClassName() {
        return getClass().getName();
    }

    @Override
    public String getPropertyOptions() {
        return AppUtil.readPluginResource(getClass().getName(), "/properties/CardanoSendTransactionTool.json", null, true, "messages/CardanoMessages");
    }
}
