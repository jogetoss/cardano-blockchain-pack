package org.joget.cardano.lib;

import org.joget.cardano.service.PluginUtil;
import org.joget.cardano.service.BackendUtil;
import org.joget.cardano.service.TransactionUtil;
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
import com.bloxbean.cardano.client.api.helper.FeeCalculationService;
import com.bloxbean.cardano.client.api.helper.TransactionHelperService;
import com.bloxbean.cardano.client.api.helper.model.TransactionResult;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.api.BlockService;
import com.bloxbean.cardano.client.backend.api.TransactionService;
import com.bloxbean.cardano.client.backend.model.TransactionContent;
import com.bloxbean.cardano.client.common.ADAConversionUtil;
import static com.bloxbean.cardano.client.common.CardanoConstants.LOVELACE;
import com.bloxbean.cardano.client.metadata.Metadata;
import com.bloxbean.cardano.client.metadata.cbor.CBORMetadata;
import com.bloxbean.cardano.client.metadata.cbor.CBORMetadataMap;
import com.bloxbean.cardano.client.transaction.model.TransactionDetailsParams;
import java.math.BigInteger;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.service.AppService;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.commons.util.PluginThread;

public class CardanoSendTransactionTool extends DefaultApplicationPlugin {

    BackendService backendService;
    BlockService blockService;
    TransactionHelperService transactionHelperService;
    FeeCalculationService feeCalculationService;
    TransactionService transactionService;
    
    AppService appService;
    WorkflowAssignment wfAssignment;
    AppDefinition appDef;
    WorkflowManager workflowManager;
    
    protected void initBackend() {
        backendService = BackendUtil.getBackendService(getProperties());
        
        blockService = backendService.getBlockService();
        transactionHelperService = backendService.getTransactionHelperService();
        feeCalculationService = backendService.getFeeCalculationService();
        transactionService = backendService.getTransactionService();
    }
    
    protected void initUtils(Map props) {
        ApplicationContext ac = AppUtil.getApplicationContext();
        
        appService = (AppService) ac.getBean("appService");
        wfAssignment = (WorkflowAssignment) props.get("workflowAssignment");
        appDef = (AppDefinition) props.get("appDef");
        workflowManager = (WorkflowManager) ac.getBean("workflowManager");
    }
    
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
        initUtils(props);
        
        String formDefId = getPropertyString("formDefId");
        final String primaryKey = appService.getOriginProcessId(wfAssignment.getProcessId());
        
        FormRowSet rowSet = appService.loadFormData(appDef.getAppId(), appDef.getVersion().toString(), formDefId, primaryKey);
        
        if (rowSet == null || rowSet.isEmpty()) {
            LogUtil.warn(getClass().getName(), "Send transaction aborted. No record found with record ID '" + primaryKey + "' from this form '" + formDefId + "'.");
            return null;
        }
        
        FormRow row = rowSet.get(0);
        
        final String senderAddress = row.getProperty(getPropertyString("senderAddress"));
        final String accountMnemonic = PluginUtil.decrypt(WorkflowUtil.processVariable(getPropertyString("accountMnemonic"), "", wfAssignment));
        final String receiverAddress = row.getProperty(getPropertyString("receiverAddress"));
        final String amount = row.getProperty(getPropertyString("amount"));
        
        try {
            final boolean isTest = "testnet".equalsIgnoreCase(getPropertyString("networkType"));
            
            final Network network = BackendUtil.getNetwork(isTest);
            
            final Account senderAccount = new Account(network, accountMnemonic);
            
            if (!senderAddress.equals(senderAccount.baseAddress())) {
                LogUtil.warn(getClass().getName(), "Transaction failed! Sender account encountered invalid mnemonic phrase.");
                return null;
            }
            
            initBackend();
            
            CBORMetadata cborMetadata = null;
            CBORMetadataMap formDataMetadata = TransactionUtil.generateMetadataMapFromFormData((Object[]) props.get("metadata"), row);
            if (formDataMetadata != null) {
                cborMetadata = new CBORMetadata();
                cborMetadata.put(BigInteger.ZERO, formDataMetadata);
            }
            Metadata metadata = cborMetadata;
            
            PaymentTransaction paymentTransaction =
            PaymentTransaction.builder()
                    .sender(senderAccount)
                    .receiver(receiverAddress)
                    .amount(getPaymentAmount(new BigDecimal(amount)))
                    .unit(getPaymentUnit())
                    .build();
            
            long ttl = TransactionUtil.getTtl(blockService);
            TransactionDetailsParams detailsParams = TransactionDetailsParams.builder().ttl(ttl).build();
            
            final BigInteger fee = feeCalculationService.calculateFee(paymentTransaction, detailsParams, metadata);
            paymentTransaction.setFee(fee);
            
            final Result<TransactionResult> transactionResult = transactionHelperService.transfer(paymentTransaction, detailsParams, metadata);
            
            //Store successful unvalidated txn result first
            storeToWorkflowVariable(wfAssignment.getActivityId(), props, isTest, transactionResult, null);
            
            //Use separate thread to wait for transaction validation
            Thread waitTransactionThread = new PluginThread(() -> {
                if (!transactionResult.isSuccessful()) {
                    LogUtil.warn(getClass().getName(), "Transaction failed with status code " + transactionResult.code() + ". Response returned --> " + transactionResult.getResponse());
                }
                
                Result<TransactionContent> validatedTransactionResult = null;
                
                try {
                    validatedTransactionResult = TransactionUtil.waitForTransaction(transactionService, transactionResult);
                } catch (Exception ex) {
                    LogUtil.error(getClass().getName(), ex, "Error waiting for transaction validation...");
                }
                
                if (validatedTransactionResult != null) {
                    //Store validated/confirmed txn result for current activity instance
                    storeToWorkflowVariable(wfAssignment.getActivityId(), props, isTest, transactionResult, validatedTransactionResult);

                    //Store validated/confirmed txn result for future running activity instance
                    storeToWorkflowVariable(workflowManager.getRunningActivityIdByRecordId(primaryKey, wfAssignment.getProcessDefId(), null, null), props, isTest, transactionResult, validatedTransactionResult);
                }
            });
            waitTransactionThread.start();
            
            return transactionResult;
        } catch (Exception ex) {
            LogUtil.error(getClass().getName(), ex, "Error executing plugin...");
            return null;
        }
    }
    
    protected BigInteger getPaymentAmount(BigDecimal amount) {
        return getPropertyString("paymentUnit").equalsIgnoreCase(LOVELACE) ? ADAConversionUtil.adaToLovelace(amount) : amount.toBigInteger();
    }
    
    protected String getPaymentUnit() {
        return getPropertyString("paymentUnit").equalsIgnoreCase(LOVELACE) ? LOVELACE : TransactionUtil.getAssetId(getPropertyString("policyId"), getPropertyString("assetName"));
    }
    
    protected void storeToWorkflowVariable(
            String activityId,
            Map properties, 
            boolean isTest, 
            Result<TransactionResult> transactionResult, 
            Result<TransactionContent> validatedtransactionResult) {
        
        String transactionValidatedVar = getPropertyString("wfTransactionValidated");
        String transactionIdVar = getPropertyString("wfTransactionId");
        String transactionUrlVar = getPropertyString("wfTransactionExplorerUrl");

        storeValuetoActivityVar(
                activityId, 
                transactionValidatedVar, 
                validatedtransactionResult != null ? String.valueOf(validatedtransactionResult.isSuccessful()) : "false"
        );
        storeValuetoActivityVar(
                activityId, 
                transactionIdVar, 
                transactionResult.getValue().getTransactionId()
        );
        storeValuetoActivityVar(
                activityId, 
                transactionUrlVar, 
                TransactionUtil.getTransactionExplorerUrl(isTest, transactionResult.getValue().getTransactionId())
        );
    }
    
    private void storeValuetoActivityVar(String activityId, String variable, String value) {
        if (activityId == null || activityId.isEmpty() || variable.isEmpty() || value == null) {
            return;
        }
        
        workflowManager.activityVariable(activityId, variable, value);
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
