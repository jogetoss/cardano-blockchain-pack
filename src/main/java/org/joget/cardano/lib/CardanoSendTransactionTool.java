package org.joget.cardano.lib;

import org.joget.cardano.util.PluginUtil;
import org.joget.cardano.util.BackendUtil;
import org.joget.cardano.util.TxUtil;
import java.math.BigDecimal;
import org.joget.apps.app.service.AppUtil;
import org.joget.commons.util.LogUtil;
import org.springframework.context.ApplicationContext;
import org.joget.workflow.util.WorkflowUtil;
import com.bloxbean.cardano.client.transaction.model.PaymentTransaction;
import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.helper.model.TransactionResult;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.backend.model.TransactionContent;
import com.bloxbean.cardano.client.common.ADAConversionUtil;
import static com.bloxbean.cardano.client.common.CardanoConstants.LOVELACE;
import com.bloxbean.cardano.client.exception.AddressExcepion;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.metadata.Metadata;
import com.bloxbean.cardano.client.transaction.model.TransactionDetailsParams;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import org.joget.apps.app.dao.DatalistDefinitionDao;
import org.joget.apps.app.model.DatalistDefinition;
import org.joget.apps.datalist.model.DataList;
import org.joget.apps.datalist.model.DataListBinder;
import org.joget.apps.datalist.model.DataListCollection;
import org.joget.apps.datalist.service.DataListService;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.cardano.model.CardanoProcessTool;
import org.joget.cardano.model.NetworkType;
import org.joget.cardano.model.explorer.Explorer;
import org.joget.cardano.model.explorer.ExplorerFactory;
import static org.joget.cardano.model.explorer.ExplorerFactory.DEFAULT_EXPLORER;
import org.joget.cardano.util.MetadataUtil;
import org.joget.cardano.util.TokenUtil;
import static org.joget.cardano.util.TxUtil.MAX_FEE_LIMIT;
import org.joget.commons.util.PluginThread;
import org.springframework.beans.BeansException;

/**
* @deprecated
* This plugin does not support CIP-30 wallet interaction.
* <p> Use {@link org.joget.cardano.lib.processformmodifier.actions.TokenTransferAction TokenTransferAction} instead.
*/
@Deprecated
public class CardanoSendTransactionTool extends CardanoProcessTool {
    
    protected DataListBinder binder = null;
    
    @Override
    public String getName() {
        return "Cardano Send Transaction Tool";
    }

    @Override
    public String getDescription() {
        return "Send assets from one account to another on the Cardano blockchain, with optional transaction metadata.";
    }
    
    @Override
    public String getPropertyOptions() {
        String backendConfigs = PluginUtil.readGenericBackendConfigs(getClassName());
        String wfVarMappings = PluginUtil.readGenericWorkflowVariableMappings(getClassName());
        return AppUtil.readPluginResource(getClassName(), "/properties/CardanoSendTransactionTool.json", new String[]{backendConfigs, wfVarMappings}, true, PluginUtil.MESSAGE_PATH);
    }
    
    @Override
    public boolean isInputDataValid() {        
        String formDefId = getPropertyString("formDefId");
        final String primaryKey = appService.getOriginProcessId(wfAssignment.getProcessId());
        
        FormRowSet rowSet = appService.loadFormData(appDef.getAppId(), appDef.getVersion().toString(), formDefId, primaryKey);
        
        if (rowSet == null || rowSet.isEmpty()) {
            LogUtil.warn(getClassName(), "Send transaction aborted. No record found with record ID '" + primaryKey + "' from this form '" + formDefId + "'");
            return false;
        }
        
        FormRow row = rowSet.get(0);
        
        final String senderAddress = row.getProperty(getPropertyString("senderAddress"));
        final String accountMnemonic = PluginUtil.decrypt(WorkflowUtil.processVariable(getPropertyString("accountMnemonic"), "", wfAssignment));
        
        final NetworkType networkType = BackendUtil.getNetworkType(props);

        final Account senderAccount = new Account(networkType.getNetwork(), accountMnemonic);
        
        if (!senderAddress.equals(senderAccount.baseAddress())) {
            LogUtil.warn(getClassName(), "Send transaction aborted. Sender account encountered invalid mnemonic phrase.");
            return false;
        }
        
        return true;
    }
    
    @Override
    public Object runTool() 
            throws RuntimeException {
        
        try {            
            String formDefId = getPropertyString("formDefId");
            final String primaryKey = appService.getOriginProcessId(wfAssignment.getProcessId());

            FormRowSet rowSet = appService.loadFormData(appDef.getAppId(), appDef.getVersion().toString(), formDefId, primaryKey);

            FormRow row = rowSet.get(0);

            final String accountMnemonic = PluginUtil.decrypt(WorkflowUtil.processVariable(getPropertyString("accountMnemonic"), "", wfAssignment));
            final String receiverAddress = row.getProperty(getPropertyString("receiverAddress"));
            final String nftReceiverAddress = row.getProperty(getPropertyString("nftReceiverAddress")); // separate property to workaround multi-condition in properties options
            final String amount = row.getProperty(getPropertyString("amount"));
            final boolean multipleReceiverMode = "true".equalsIgnoreCase(getPropertyString("multipleReceiverMode"));
            final boolean paymentUnitNft = "nft".equalsIgnoreCase(getPropertyString("paymentUnit"));

            final NetworkType networkType = BackendUtil.getNetworkType(props);

            final Account senderAccount = new Account(networkType.getNetwork(), accountMnemonic);

            List<PaymentTransaction> paymentList = new ArrayList<>();

            if (multipleReceiverMode) { // If enabled multi receiver mode
                //Consider pulling binder plugin/configs directly from user selected Datalist
                paymentList = getPaymentListFromBinderData(senderAccount);

                if (paymentList == null || paymentList.isEmpty()) {
                    LogUtil.warn(getClassName(), "Send transaction aborted. No valid receiver records found from binder.");
                    return null;
                }
            } else { // If not enabled multi receiver mode (single receiver only)
                String tempReceiverAddress;

                if (paymentUnitNft) {
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

            long ttl = TxUtil.getTtl(blockService, 2000);
            TransactionDetailsParams detailsParams = TransactionDetailsParams.builder().ttl(ttl).build();

            // See https://cips.cardano.org/cips/cip20/
            Metadata metadata = MetadataUtil.generateMsgMetadataFromFormData((Object[]) props.get("metadata"), row);

            final BigInteger fee = feeCalculationService.calculateFee(paymentList, detailsParams, metadata);

            BigInteger feeLimit = MAX_FEE_LIMIT;
            if (!getPropertyString("feeLimit").isBlank()) {
                feeLimit = ADAConversionUtil.adaToLovelace(new BigDecimal(getPropertyString("feeLimit")));
            }
            if (!TxUtil.checkFeeLimit(fee, feeLimit)) {
                LogUtil.warn(getClassName(), "Send transaction aborted. Transaction fee in units of lovelace of " + fee.toString() + " exceeded set fee limit of " + feeLimit.toString() + ".");
                storeToWorkflowVariable(wfAssignment.getActivityId(), networkType, null, null);
                return null;
            }
            paymentList.get(0).setFee(fee);

            Result<TransactionResult> transactionResult = transactionHelperService.transfer(paymentList, detailsParams, metadata);

            if (!transactionResult.isSuccessful()) {
                LogUtil.warn(getClassName(), "Transaction failed with status code " + transactionResult.code() + ". Response returned --> " + transactionResult.getResponse());
                storeToWorkflowVariable(wfAssignment.getActivityId(), networkType, null, null);
                return null;
            }

            //Store successful unvalidated txn result first
            storeToWorkflowVariable(wfAssignment.getActivityId(), networkType, transactionResult, null);

            //Use separate thread to wait for transaction validation
            Thread waitTransactionThread = new PluginThread(() -> {
                Result<TransactionContent> validatedTransactionResult = null;

                try {
                    validatedTransactionResult = TxUtil.waitForTransaction(transactionService, transactionResult);
                } catch (Exception ex) {
                    LogUtil.error(getClassName(), ex, "Error waiting for transaction validation...");
                }

                if (validatedTransactionResult != null) {
                    //Store validated/confirmed txn result for current activity instance
                    storeToWorkflowVariable(wfAssignment.getActivityId(), networkType, transactionResult, validatedTransactionResult);

                    //Store validated/confirmed txn result for future running activity instance
                    String mostRecentActivityId = workflowManager.getRunningActivityIdByRecordId(primaryKey, wfAssignment.getProcessDefId(), null, null);
                    storeToWorkflowVariable(mostRecentActivityId, networkType, transactionResult, validatedTransactionResult);
                }
            });
            waitTransactionThread.start();

            return transactionResult;
        } catch (ApiException | CborSerializationException | AddressExcepion e) {
            throw new RuntimeException(e.getClass().getName() + " : " + e.getMessage());
        }
    }
    
    protected DataList getDataList() throws BeansException {
        DataList datalist = null;
        
        ApplicationContext ac = AppUtil.getApplicationContext();
        DatalistDefinitionDao datalistDefinitionDao = (DatalistDefinitionDao) ac.getBean("datalistDefinitionDao");
        final String datalistId = getPropertyString("datalistId");
        DatalistDefinition datalistDefinition = datalistDefinitionDao.loadById(datalistId, appDef);

        if (datalistDefinition != null) {
            datalist = dataListService.fromJson(datalistDefinition.getJson());
        }
        
        return datalist;
    }
    
    //Get receiver(s) & their respective amounts to send from binder from user-selected datalist
    protected List<PaymentTransaction> getPaymentListFromBinderData(Account senderAccount) {
        List<PaymentTransaction> paymentList = new ArrayList<>();
            
        try {
            DataList datalist = getDataList();
            DataListCollection binderData = datalist.getRows();
            
            if (binderData == null || binderData.isEmpty()) {
                return null;
            }
            
            // Unit var placed outside of for-loop to avoid redundant calls just to get payment unit
            String unit = getPaymentUnit();
            
            for (Object r : binderData) {
                String receiverAddress = (String) DataListService.evaluateColumnValueFromRow(r, getPropertyString("receiverAddressColumn"));
                String amount = (String) DataListService.evaluateColumnValueFromRow(r, getPropertyString("amountColumn"));

                // Skip row where receiver address or amount is empty
                if (receiverAddress == null || receiverAddress.isEmpty() || amount == null || amount.isEmpty()) {
                    continue;
                }
                
                BigInteger amountBgInt = getPaymentAmount(amount);

                // Check for illogical transfer amount of less or equal to 0
                if (amountBgInt.compareTo(BigInteger.ZERO) <= 0) {
                    LogUtil.info(getClassName(), "Skipping receiver address \"" + receiverAddress + "\" with invalid amount of \"" + amount + "\"");
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
            LogUtil.error(getClassName(), ex, "Unable to retrieve transaction receivers data from datalist binder.");
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
        String paymentUnit = getPropertyString("paymentUnit");
        String policyId = getPropertyString("policyId");
        String assetName = getPropertyString("assetName");
        
        return paymentUnit.equalsIgnoreCase(LOVELACE) ? LOVELACE : TokenUtil.getAssetId(policyId, assetName);
    }
    
    protected void storeToWorkflowVariable(
            String activityId,
            NetworkType networkType, 
            Result<TransactionResult> transactionResult, 
            Result<TransactionContent> validatedtransactionResult) {
        
        Explorer explorer = new ExplorerFactory(networkType).createExplorer(DEFAULT_EXPLORER);
        
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
                transactionResult != null ? explorer.getTransactionUrl(transactionResult.getValue().getTransactionId()) : ""
        );
    }
    
    private void storeValuetoActivityVar(String activityId, String variable, String value) {
        if (activityId == null || activityId.isEmpty() || variable.isEmpty() || value == null) {
            return;
        }
        
        workflowManager.activityVariable(activityId, variable, value);
    }
}
