package org.joget.cardano.lib;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.api.helper.FeeCalculationService;
import com.bloxbean.cardano.client.api.helper.TransactionHelperService;
import com.bloxbean.cardano.client.api.helper.model.TransactionResult;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.api.BlockService;
import com.bloxbean.cardano.client.backend.api.TransactionService;
import com.bloxbean.cardano.client.backend.model.TransactionContent;
import com.bloxbean.cardano.client.cip.cip25.NFT;
import com.bloxbean.cardano.client.cip.cip25.NFTFile;
import com.bloxbean.cardano.client.cip.cip25.NFTMetadata;
import com.bloxbean.cardano.client.common.model.Network;
import com.bloxbean.cardano.client.crypto.SecretKey;
import com.bloxbean.cardano.client.metadata.Metadata;
import com.bloxbean.cardano.client.metadata.cbor.CBORMetadata;
import com.bloxbean.cardano.client.metadata.cbor.CBORMetadataMap;
import com.bloxbean.cardano.client.transaction.model.MintTransaction;
import com.bloxbean.cardano.client.transaction.model.TransactionDetailsParams;
import com.bloxbean.cardano.client.transaction.spec.Asset;
import com.bloxbean.cardano.client.transaction.spec.MultiAsset;
import com.bloxbean.cardano.client.transaction.spec.Policy;
import com.bloxbean.cardano.client.util.PolicyUtil;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import org.joget.cardano.service.PluginUtil;
import org.joget.cardano.service.BackendUtil;
import org.joget.cardano.service.TransactionUtil;
import java.util.Map;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.service.AppService;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.commons.util.LogUtil;
import org.joget.commons.util.PluginThread;
import org.joget.plugin.base.DefaultApplicationPlugin;
import org.joget.workflow.model.WorkflowAssignment;
import org.joget.workflow.model.service.WorkflowManager;
import org.joget.workflow.util.WorkflowUtil;
import org.springframework.context.ApplicationContext;

public class CardanoMintTokenTool extends DefaultApplicationPlugin {

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
        return "Cardano Mint Token Tool";
    }

    @Override
    public String getVersion() {
        return PluginUtil.getProjectVersion(this.getClass());
    }

    @Override
    public String getDescription() {
        return "Mint native tokens and NFTs on the Cardano blockchain.";
    }

    @Override
    public Object execute(Map props) {
        initUtils(props);
        
        String formDefId = getPropertyString("formDefId");
        final String primaryKey = appService.getOriginProcessId(wfAssignment.getProcessId());

        FormRowSet rowSet = appService.loadFormData(appDef.getAppId(), appDef.getVersion().toString(), formDefId, primaryKey);
        
        if (rowSet == null || rowSet.isEmpty()) {
            LogUtil.warn(getClass().getName(), "Token minting aborted. No record found with record ID '" + primaryKey + "' from this form '" + formDefId + "'.");
            return null;
        }
        
        FormRow row = rowSet.get(0);
        
        final String senderAddress = row.getProperty(getPropertyString("senderAddress"));
        final String accountMnemonic = PluginUtil.decrypt(WorkflowUtil.processVariable(getPropertyString("accountMnemonic"), "", wfAssignment));
        
        try {
            final boolean isTest = BackendUtil.isTestnet(props);
            
            final Network network = BackendUtil.getNetwork(isTest);
            
            final Account senderAccount = new Account(network, accountMnemonic);
            
            if (!senderAddress.equals(senderAccount.baseAddress())) {
                LogUtil.warn(getClass().getName(), "Transaction failed! Minter account encountered invalid mnemonic phrase.");
                return null;
            }
            
            initBackend();
            
            //Perhaps support multisig policy signing in future? 1 signer for now.
            Policy policy = PolicyUtil.createMultiSigScriptAllPolicy("", 1);
            final String policyId = policy.getPolicyId();
            final List<SecretKey> skeys = policy.getPolicyKeys();
            
            MultiAsset multiAsset = new MultiAsset();
            multiAsset.setPolicyId(policyId);
            
            Asset asset;
            
            Metadata metadata;
            
            /* Mint logic starts here */
            if ("nft".equalsIgnoreCase(getPropertyString("mintType"))) { //For minting NFT
                //Perhaps consider creating new File Upload form element plugin to support IPFS read/write...
                final String nftName = row.getProperty(getPropertyString("nftName"));
                final String nftDescription = row.getProperty(getPropertyString("nftDescription"));
                final String nftFileName = row.getProperty(getPropertyString("nftFileName"));
                final String nftFileType = getPropertyString("nftFileType"); // Check for other common supported mime types
                final String ipfsCid = row.getProperty(getPropertyString("ipfsCid")); // Typically looks like --> Qmcv6hwtmdVumrNeb42R1KmCEWdYWGcqNgs17Y3hj6CkP4
                
                asset = new Asset(nftName, BigInteger.ONE);
                
                NFT nft = NFT.create()
                    .assetName(nftName)
                    .name(nftName)
                    .description(nftDescription)
                    .image("ipfs://" + ipfsCid)
                    .mediaType(nftFileType)
                    .addFile(NFTFile.create()
                            .name(nftFileName)
                            .mediaType(nftFileType)
                            .src("ipfs/" + ipfsCid));
            
                // See https://cips.cardano.org/cips/cip25/
                NFTMetadata nftMetadata = NFTMetadata.create()
                        .addNFT(policyId, nft);
                
                metadata = nftMetadata;
            } else { // For minting native tokens
                final String tokenName = row.getProperty(getPropertyString("tokenName"));
                final String tokenSymbol = row.getProperty(getPropertyString("tokenSymbol"));
                final String amountToMint = row.getProperty(getPropertyString("amountToMint"));
                
                asset = new Asset(tokenName, new BigInteger(amountToMint));
                
                CBORMetadata cborMetadata = new CBORMetadata();
                
                //Put token name and symbol into metadata
                CBORMetadataMap tokenInfoMap
                        = new CBORMetadataMap()
                        .put("token", tokenName)
                        .put("symbol", tokenSymbol);
                cborMetadata.put(BigInteger.ZERO, tokenInfoMap);

                // Need check compliance with cip20 --> https://cips.cardano.org/cips/cip20/
                CBORMetadataMap formDataMetadata = TransactionUtil.generateMetadataMapFromFormData((Object[]) props.get("metadata"), row);
                if (formDataMetadata != null) {
                    cborMetadata.put(BigInteger.ONE, formDataMetadata);
                }
                
                metadata = cborMetadata;
            }
            
            policy.setName(TransactionUtil.getFormattedPolicyName(policyId, asset.getName()));
            
            multiAsset.getAssets().add(asset);
            
            MintTransaction mintTransaction =
                MintTransaction.builder()
                        .sender(senderAccount)
                        .receiver(senderAccount.baseAddress())
                        .mintAssets(Arrays.asList(multiAsset))
                        .policy(policy)
                        .build();
            
            long ttl = TransactionUtil.getTtl(blockService, 2000);
            TransactionDetailsParams detailsParams = TransactionDetailsParams.builder().ttl(ttl).build();

            final BigInteger fee = feeCalculationService.calculateFee(mintTransaction, detailsParams, metadata);
            mintTransaction.setFee(fee);

            final Result<TransactionResult> transactionResult = transactionHelperService.mintToken(mintTransaction, detailsParams, metadata);

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
                    //Store minting policy data to form
                    storeToForm(senderAccount, policyId, skeys, asset.getName(), isTest);
                     
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
    
    protected void storeToForm(Account minter, String policyId, List<SecretKey> skeys, String tokenName, boolean isTest) {
        String formDefId = getPropertyString("formDefIdStoreMintData");
        
        if (formDefId == null || formDefId.isEmpty()) {
            LogUtil.warn(getClass().getName(), "Unable to store minting data to form. Encountered blank form ID.");
            return;
        }

        // Combine all secret key(s) into string delimited by semicolon for storage (e.g.: skey1;skey2;skey3)
        String skeyListAsCborHex = TransactionUtil.getSecretKeysAsCborHexStringList(skeys);
        
        String minterAccountField = getPropertyString("minterAccountField");
        String policyIdField = getPropertyString("policyIdField");
        String policySecretKeyField = getPropertyString("policySecretKeyField");
        String tokenNameField = getPropertyString("tokenNameField");
        String isTestnetField = getPropertyString("isTestnetField");

        FormRowSet rowSet = new FormRowSet();

        FormRow row = new FormRow();

        //Asset ID set as Record ID
        row.setId(TransactionUtil.getAssetId(policyId, tokenName));

        row = addRow(row, minterAccountField, minter.baseAddress());
        row = addRow(row, policyIdField, policyId);
        row = addRow(row, policySecretKeyField, PluginUtil.encrypt(skeyListAsCborHex));
        row = addRow(row, tokenNameField, tokenName);
        row = addRow(row, isTestnetField, String.valueOf(isTest));

        rowSet.add(row);

        if (!rowSet.isEmpty()) {
            FormRowSet storedData = appService.storeFormData(appDef.getId(), appDef.getVersion().toString(), formDefId, rowSet, null);
            if (storedData == null) {
                LogUtil.warn(getClass().getName(), "Unable to store minting data to form. Encountered invalid form ID of '" + formDefId + "'.");
            }
        }
    }
    
    private FormRow addRow(FormRow row, String field, String value) {
        if (row != null && !field.isEmpty()) {
            row.put(field, value);
        }
        return row;
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
        return AppUtil.readPluginResource(getClass().getName(), "/properties/CardanoMintTokenTool.json", null, true, "messages/CardanoMessages");
    }
}
