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
import com.bloxbean.cardano.client.cip.cip20.MessageMetadata;
import com.bloxbean.cardano.client.common.ADAConversionUtil;
import static com.bloxbean.cardano.client.common.CardanoConstants.LOVELACE;
import com.bloxbean.cardano.client.metadata.Metadata;
import com.bloxbean.cardano.client.transaction.model.TransactionDetailsParams;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.service.AppService;
import org.joget.apps.datalist.model.DataListBinder;
import org.joget.apps.datalist.model.DataListCollection;
import org.joget.apps.datalist.model.DataListFilterQueryObject;
import org.joget.apps.datalist.service.DataListService;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.cardano.service.ExplorerLinkUtil;
import org.joget.cardano.service.MetadataUtil;
import org.joget.cardano.service.TokenUtil;
import static org.joget.cardano.service.TransactionUtil.MAX_FEE_LIMIT;
import org.joget.commons.util.PluginThread;

public class CardanoSendTransactionTool extends DefaultApplicationPlugin {
    
    protected DataListBinder binder = null;
    
    BackendService backendService;
    BlockService blockService;
    TransactionHelperService transactionHelperService;
    FeeCalculationService feeCalculationService;
    TransactionService transactionService;
    
    AppService appService;
    WorkflowAssignment wfAssignment;
    AppDefinition appDef;
    WorkflowManager workflowManager;
    DataListService dataListService;
    
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
        dataListService = (DataListService) ac.getBean("dataListService");
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
        return "Send assets from one account to another on the Cardano blockchain, with optional transaction metadata.";
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
        final String nftReceiverAddress = row.getProperty(getPropertyString("nftReceiverAddress")); // separate property to workaround multi-condition in properties options
        final String amount = row.getProperty(getPropertyString("amount"));
        
        try {
            final boolean isTest = BackendUtil.isTestnet(props);
            
            final Network network = BackendUtil.getNetwork(isTest);
            
            final Account senderAccount = new Account(network, accountMnemonic);
            
            if (!senderAddress.equals(senderAccount.baseAddress())) {
                LogUtil.warn(getClass().getName(), "Transaction failed! Sender account encountered invalid mnemonic phrase.");
                return null;
            }
            
            initBackend();
            
            // See https://cips.cardano.org/cips/cip20/
            Metadata metadata = null;
            MessageMetadata messageMetadata = MetadataUtil.generateMsgMetadataFromFormData((Object[]) props.get("metadata"), row);
            if (messageMetadata != null) {
                metadata = messageMetadata;
            }
            
            long ttl = TransactionUtil.getTtl(blockService, 2000);
            TransactionDetailsParams detailsParams = TransactionDetailsParams.builder().ttl(ttl).build();
            
            Result<TransactionResult> transactionResult;
            List<PaymentTransaction> paymentList = new ArrayList<>();
            
            if ("true".equalsIgnoreCase(getPropertyString("multipleReceiverMode"))) { // If enabled multi receiver mode
                //Consider pulling binder plugin/configs directly from user selected Datalist
                paymentList = getPaymentListFromBinderData(senderAccount);
                
                if (paymentList == null || paymentList.isEmpty()) {
                    LogUtil.warn(getClass().getName(), "Transaction aborted. No valid receiver records found from binder.");
                    return null;
                }
            } else { // If not enabled multi receiver mode (single receiver only)
                String tempReceiverAddress;
                
                if ("nft".equalsIgnoreCase(getPropertyString("paymentUnit"))) {
                    tempReceiverAddress = nftReceiverAddress;
                } else {
                    tempReceiverAddress = receiverAddress;
                }
                
                PaymentTransaction paymentTransaction =
                    PaymentTransaction.builder()
                            .sender(senderAccount)
                            .receiver(tempReceiverAddress)
                            .amount(getPaymentAmount(amount))
                            .unit(getPaymentUnit())
                            .build();
                
                paymentList.add(paymentTransaction);
            }
            
            final BigInteger fee = feeCalculationService.calculateFee(paymentList, detailsParams, metadata);
            
            BigInteger feeLimit = MAX_FEE_LIMIT;
            if (!getPropertyString("feeLimit").isBlank()) {
                feeLimit = ADAConversionUtil.adaToLovelace(new BigDecimal(getPropertyString("feeLimit")));
            }
            if (!TransactionUtil.checkFeeLimit(fee, feeLimit)) {
                LogUtil.warn(getClass().getName(), "Send transaction aborted. Transaction fee in units of lovelace of " + fee.toString() + " exceeded set fee limit of " + feeLimit.toString() + ".");
                storeToWorkflowVariable(wfAssignment.getActivityId(), isTest, null, null);
                return null;
            }
            paymentList.get(0).setFee(fee);

            transactionResult = transactionHelperService.transfer(paymentList, detailsParams, metadata);
            
            if (!transactionResult.isSuccessful()) {
                LogUtil.warn(getClass().getName(), "Transaction failed with status code " + transactionResult.code() + ". Response returned --> " + transactionResult.getResponse());
                storeToWorkflowVariable(wfAssignment.getActivityId(), isTest, null, null);
                return null;
            }
            
            //Store successful unvalidated txn result first
            storeToWorkflowVariable(wfAssignment.getActivityId(), isTest, transactionResult, null);
            
            //Use separate thread to wait for transaction validation
            Thread waitTransactionThread = new PluginThread(() -> {
                Result<TransactionContent> validatedTransactionResult = null;
                
                try {
                    validatedTransactionResult = TransactionUtil.waitForTransaction(transactionService, transactionResult);
                } catch (Exception ex) {
                    LogUtil.error(getClass().getName(), ex, "Error waiting for transaction validation...");
                }
                
                if (validatedTransactionResult != null) {
                    //Store validated/confirmed txn result for current activity instance
                    storeToWorkflowVariable(wfAssignment.getActivityId(), isTest, transactionResult, validatedTransactionResult);

                    //Store validated/confirmed txn result for future running activity instance
                    String mostRecentActivityId = workflowManager.getRunningActivityIdByRecordId(primaryKey, wfAssignment.getProcessDefId(), null, null);
                    storeToWorkflowVariable(mostRecentActivityId, isTest, transactionResult, validatedTransactionResult);
                }
            });
            waitTransactionThread.start();
            
            return transactionResult;
        } catch (Exception ex) {
            LogUtil.error(getClass().getName(), ex, "Error executing plugin...");
            return null;
        }
    }
    
    protected DataListBinder getBinder() {
        if (binder == null) {
            Object binderMap = getProperty("binder");
            if (binderMap != null && binderMap instanceof Map) {
                Map bdMap = (Map) binderMap;
                if (bdMap.containsKey("className") && !bdMap.get("className").toString().isEmpty()) {
                    binder = dataListService.getBinder(bdMap.get("className").toString());
                    if (binder != null) {
                        Map bdProps = (Map) bdMap.get("properties");
                        binder.setProperties(bdProps);
                    }
                }
            }
        }
        
        return binder;
    }
    
    //Get receiver(s) & their respective amounts to send from user-selected binder
    protected List<PaymentTransaction> getPaymentListFromBinderData(Account senderAccount) {
        List<PaymentTransaction> paymentList = new ArrayList<>();
            
        try {
            DataListCollection node = getBinder().getData(null, getBinder().getProperties(), new DataListFilterQueryObject[]{}, null, null, null, null);

            if (node == null || node.isEmpty()) {
                return null;
            }
            
            // Unit var placed outside of for-loop to avoid redundant calls just to get payment unit
            String unit = getPaymentUnit();
            
            for (Object r : node) {
                String receiverAddress = (String) DataListService.evaluateColumnValueFromRow(r, getPropertyString("receiverAddressColumn"));
                String amount = (String) DataListService.evaluateColumnValueFromRow(r, getPropertyString("amountColumn"));

                // Skip row where receiver address or amount is empty
                if (receiverAddress == null || receiverAddress.isEmpty() || amount == null || amount.isEmpty()) {
                    continue;
                }
                
                BigInteger amountBgInt = getPaymentAmount(amount);

                // Check for illogical transfer amount of less or equal to 0
                if (amountBgInt.compareTo(BigInteger.ZERO) <= 0) {
                    LogUtil.info(getClass().getName(), "Skipping receiver address \"" + receiverAddress + "\" with invalid amount of \"" + amount + "\"");
                    continue;
                }
                
                PaymentTransaction paymentTransaction =
                    PaymentTransaction.builder()
                            .sender(senderAccount)
                            .receiver(receiverAddress)
                            .amount(amountBgInt)
                            .unit(unit)
                            .build();

                paymentList.add(paymentTransaction);
            }
        } catch (Exception ex) {
            LogUtil.error(getClass().getName(), ex, "Unable to retrieve transaction receivers data from binder.");
        }
        
        return paymentList;
    }
    
    protected BigInteger getPaymentAmount(String amount) {
        String paymentUnit = getPropertyString("paymentUnit");
        
        switch (paymentUnit) {
            case LOVELACE:
                return ADAConversionUtil.adaToLovelace(new BigDecimal(amount));
            case "nativeTokens":
                return new BigDecimal(amount).toBigInteger();
            case "nft":
                return BigInteger.ONE;
        }
        
        return null;
    }
    
    protected String getPaymentUnit() {
        return getPropertyString("paymentUnit").equalsIgnoreCase(LOVELACE) ? LOVELACE : TokenUtil.getAssetId(getPropertyString("policyId"), getPropertyString("assetName"));
    }
    
    protected void storeToWorkflowVariable(
            String activityId,
            boolean isTest, 
            Result<TransactionResult> transactionResult, 
            Result<TransactionContent> validatedtransactionResult) {
        
        String transactionSuccessfulVar = getPropertyString("wfTransactionSuccessful");
        String transactionValidatedVar = getPropertyString("wfTransactionValidated");
        String transactionIdVar = getPropertyString("wfTransactionId");
        String transactionUrlVar = getPropertyString("wfTransactionExplorerUrl");
        
        storeValuetoActivityVar(
                activityId, 
                transactionSuccessfulVar, 
                transactionResult != null ? String.valueOf(transactionResult.isSuccessful()) : "false"
        );
        storeValuetoActivityVar(
                activityId, 
                transactionValidatedVar, 
                validatedtransactionResult != null ? String.valueOf(validatedtransactionResult.isSuccessful()) : "false"
        );
        storeValuetoActivityVar(
                activityId, 
                transactionIdVar, 
                transactionResult != null ? transactionResult.getValue().getTransactionId() : ""
        );
        storeValuetoActivityVar(
                activityId, 
                transactionUrlVar, 
                transactionResult != null ? ExplorerLinkUtil.getTransactionExplorerUrl(isTest, transactionResult.getValue().getTransactionId()) : ""
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
        String backendConfigs = PluginUtil.readGenericBackendConfigs(getClass().getName());
        String wfVarMappings = PluginUtil.readGenericWorkflowVariableMappings(getClass().getName());
        return AppUtil.readPluginResource(getClass().getName(), "/properties/CardanoSendTransactionTool.json", new String[]{backendConfigs, wfVarMappings}, true, PluginUtil.MESSAGE_PATH);
    }
}
