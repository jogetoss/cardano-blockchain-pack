package org.joget.cardano.lib;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.helper.model.TransactionResult;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.model.TransactionContent;
import com.bloxbean.cardano.client.cip.cip20.MessageMetadata;
import com.bloxbean.cardano.client.cip.cip25.NFT;
import com.bloxbean.cardano.client.cip.cip25.NFTFile;
import com.bloxbean.cardano.client.cip.cip25.NFTMetadata;
import com.bloxbean.cardano.client.common.ADAConversionUtil;
import com.bloxbean.cardano.client.crypto.SecretKey;
import com.bloxbean.cardano.client.exception.AddressExcepion;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.metadata.Metadata;
import com.bloxbean.cardano.client.metadata.cbor.CBORMetadata;
import com.bloxbean.cardano.client.metadata.cbor.CBORMetadataMap;
import com.bloxbean.cardano.client.transaction.model.MintTransaction;
import com.bloxbean.cardano.client.transaction.model.TransactionDetailsParams;
import com.bloxbean.cardano.client.transaction.spec.Asset;
import com.bloxbean.cardano.client.transaction.spec.MultiAsset;
import com.bloxbean.cardano.client.transaction.spec.Policy;
import com.bloxbean.cardano.client.transaction.spec.script.NativeScript;
import com.bloxbean.cardano.client.util.PolicyUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
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
import org.joget.apps.form.service.FormUtil;
import org.joget.cardano.model.CardanoProcessTool;
import org.joget.cardano.model.NetworkType;
import org.joget.cardano.service.ExplorerLinkUtil;
import org.joget.cardano.service.MetadataUtil;
import static org.joget.cardano.service.MetadataUtil.NFT_FORMDATA_PROPERTY_LABEL;
import static org.joget.cardano.service.MetadataUtil.TOKEN_INFO_METADATUM_LABEL;
import org.joget.cardano.service.TokenUtil;
import static org.joget.cardano.service.TransactionUtil.MAX_FEE_LIMIT;
import org.joget.commons.util.LogUtil;
import org.joget.commons.util.PluginThread;
import org.joget.workflow.model.WorkflowAssignment;
import org.joget.workflow.model.service.WorkflowManager;
import org.joget.workflow.util.WorkflowUtil;
import org.springframework.context.ApplicationContext;

public class CardanoMintTokenTool extends CardanoProcessTool {
    
    AppService appService;
    AppDefinition appDef;
    WorkflowManager workflowManager;
    
    @Override
    public String getName() {
        return "Cardano Mint Token Tool";
    }

    @Override
    public String getDescription() {
        return "Mint native tokens and NFTs on the Cardano blockchain.";
    }

    @Override
    public String getPropertyOptions() {
        String backendConfigs = PluginUtil.readGenericBackendConfigs(getClassName());
        String wfVarMappings = PluginUtil.readGenericWorkflowVariableMappings(getClassName());
        return AppUtil.readPluginResource(getClassName(), "/properties/CardanoMintTokenTool.json", new String[]{backendConfigs, wfVarMappings}, true, PluginUtil.MESSAGE_PATH);
    }
    
    protected void initUtils(Map props) {        
        ApplicationContext ac = AppUtil.getApplicationContext();
        
        appService = (AppService) ac.getBean("appService");
        appDef = (AppDefinition) props.get("appDef");
        workflowManager = (WorkflowManager) ac.getBean("workflowManager");
    }
    
    @Override
    public boolean isInputDataValid(Map props, WorkflowAssignment wfAssignment) {
        initUtils(props);
        
        String formDefId = getPropertyString("formDefId");
        final String primaryKey = appService.getOriginProcessId(wfAssignment.getProcessId());

        FormRowSet rowSet = appService.loadFormData(appDef.getAppId(), appDef.getVersion().toString(), formDefId, primaryKey);
        
        if (rowSet == null || rowSet.isEmpty()) {
            LogUtil.warn(getClassName(), "Mint transaction aborted. No record found with record ID '" + primaryKey + "' from this form '" + formDefId + "'.");
            return false;
        }
        
        FormRow row = rowSet.get(0);
        
        final String senderAddress = row.getProperty(getPropertyString("senderAddress"));
        final String accountMnemonic = PluginUtil.decrypt(WorkflowUtil.processVariable(getPropertyString("accountMnemonic"), "", wfAssignment));
        
        final NetworkType networkType = BackendUtil.getNetworkType(props);

        final Account senderAccount = new Account(networkType.getNetwork(), accountMnemonic);

        if (!senderAddress.equals(senderAccount.baseAddress())) {
            LogUtil.warn(getClassName(), "Mint transaction aborted. Minter account encountered invalid mnemonic phrase.");
            return false;
        }
        
        return true;
    }
    
    @Override
    public void initBackendServices(BackendService backendService) {
        blockService = backendService.getBlockService();
        transactionHelperService = backendService.getTransactionHelperService();
        feeCalculationService = backendService.getFeeCalculationService();
        transactionService = backendService.getTransactionService();
    }
    
    @Override
    public Object runTool(Map props, WorkflowAssignment wfAssignment) 
            throws RuntimeException {
        
        try {
            initUtils(props);
            
            String formDefId = getPropertyString("formDefId");
            final String primaryKey = appService.getOriginProcessId(wfAssignment.getProcessId());

            FormRowSet rowSet = appService.loadFormData(appDef.getAppId(), appDef.getVersion().toString(), formDefId, primaryKey);

            FormRow row = rowSet.get(0);

            final String accountMnemonic = PluginUtil.decrypt(WorkflowUtil.processVariable(getPropertyString("accountMnemonic"), "", wfAssignment));
            final boolean reusePolicy = "reuse".equalsIgnoreCase(getPropertyString("mintingPolicyHandling"));
            final boolean mintTypeNft = "nft".equalsIgnoreCase(getPropertyString("mintType"));

            final NetworkType networkType = BackendUtil.getNetworkType(props);
            final boolean isTest = networkType.isTestNetwork();

            final Account senderAccount = new Account(networkType.getNetwork(), accountMnemonic);

            Policy policy;

            if (reusePolicy) { //Reuse an existing minting policy
                String policyId = WorkflowUtil.processVariable(getPropertyString("policyId"), "", wfAssignment);
                NativeScript policyScript = NativeScript.deserializeJson(
                        WorkflowUtil.processVariable(getPropertyString("policyScript"), "", wfAssignment)
                );
                List<SecretKey> skeys = TokenUtil.getSecretKeysStringAsList(
                        PluginUtil.decrypt(
                                WorkflowUtil.processVariable(getPropertyString("policyKeys"), "", wfAssignment)
                        )
                );

                if (!policyScript.getPolicyId().equals(policyId)) {
                    LogUtil.warn(getClassName(), "Transaction failed! Policy script does not match given policy ID.");
                    return null;
                }

                policy = new Policy(TokenUtil.getFormattedPolicyName(policyId), policyScript, skeys);
            } else { // Generate a new minting policy for this minting transaction
                /* Perhaps support multisig policy signing in future? 1 signer for now. */
                policy = PolicyUtil.createMultiSigScriptAllPolicy("", 1);

                policy.setName(TokenUtil.getFormattedPolicyName(policy.getPolicyId()));
            }

            MultiAsset multiAsset = new MultiAsset();
            multiAsset.setPolicyId(policy.getPolicyId());

            Asset asset;
            Metadata metadata;

            if (mintTypeNft) { //For minting NFT
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

                Map<String, Object> nftPropsMap = MetadataUtil.generateNftPropsFromFormData((Object[]) props.get("nftProperties"), row);
                if (nftPropsMap != null) {
                    nft.property(NFT_FORMDATA_PROPERTY_LABEL, nftPropsMap);
                }

                // See https://cips.cardano.org/cips/cip25/
                metadata = NFTMetadata.create()
                        .addNFT(policy.getPolicyId(), nft);
            } else { // For minting native tokens
                final String tokenName = row.getProperty(getPropertyString("tokenName"));
                final String tokenSymbol = row.getProperty(getPropertyString("tokenSymbol"));
                final String amountToMint = row.getProperty(getPropertyString("amountToMint"));

                asset = new Asset(tokenName, new BigInteger(amountToMint));

                CBORMetadata cborMetadata = new CBORMetadata();

                /* Check for CIP in future for any native token standard */
                CBORMetadataMap tokenInfoMap
                        = new CBORMetadataMap()
                        .put("token", tokenName)
                        .put("symbol", tokenSymbol);
                cborMetadata.put(TOKEN_INFO_METADATUM_LABEL, tokenInfoMap);

                // See https://cips.cardano.org/cips/cip20/
                MessageMetadata messageMetadata = MetadataUtil.generateMsgMetadataFromFormData((Object[]) props.get("metadata"), row);
                if (messageMetadata != null) {
                    cborMetadata = (CBORMetadata) cborMetadata.merge(messageMetadata);
                }

                metadata = cborMetadata;
            }

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

            BigInteger feeLimit = MAX_FEE_LIMIT;
            if (!getPropertyString("feeLimit").isBlank()) {
                feeLimit = ADAConversionUtil.adaToLovelace(new BigDecimal(getPropertyString("feeLimit")));
            }
            if (!TransactionUtil.checkFeeLimit(fee, feeLimit)) {
                LogUtil.warn(getClassName(), "Mint transaction aborted. Transaction fee in units of lovelace of " + fee.toString() + " exceeded set fee limit of " + feeLimit.toString() + ".");
                storeToWorkflowVariable(wfAssignment.getActivityId(), networkType, null, null);
                return null;
            }
            mintTransaction.setFee(fee);

            final Result<TransactionResult> transactionResult = transactionHelperService.mintToken(mintTransaction, detailsParams, metadata);

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
                    validatedTransactionResult = TransactionUtil.waitForTransaction(transactionService, transactionResult);
                } catch (Exception ex) {
                    LogUtil.error(getClassName(), ex, "Error waiting for transaction validation...");
                }

                if (validatedTransactionResult != null) {                    
                    try {
                        //Store minting policy & asset data to form
                        storeMintingPolicyToForm(senderAccount, policy, isTest);
                        storeAssetDataToForm(senderAccount, policy, asset.getName(), isTest);
                    } catch (Exception ex) {
                        LogUtil.error(getClassName(), ex, "Unable to store data to form. Please check logs.");
                    }

                    //Store validated/confirmed txn result for current activity instance
                    storeToWorkflowVariable(wfAssignment.getActivityId(), networkType, transactionResult, validatedTransactionResult);

                    //Store validated/confirmed txn result for future running activity instance
                    String mostRecentActivityId = workflowManager.getRunningActivityIdByRecordId(primaryKey, wfAssignment.getProcessDefId(), null, null);
                    storeToWorkflowVariable(mostRecentActivityId, networkType, transactionResult, validatedTransactionResult);
                }
            });
            waitTransactionThread.start();

            return transactionResult;
        } catch (ApiException | CborSerializationException | CborDeserializationException | AddressExcepion | JsonProcessingException e) {
            throw new RuntimeException(e.getClass().getName() + " : " + e.getMessage());
        }
    }
    
    private void storeMintingPolicyToForm(Account minter, Policy policy, boolean isTest) 
            throws JsonProcessingException, RuntimeException {
        
        //If reusing existing minting policy, don't need to store policy again.
        if ("reuse".equalsIgnoreCase(getPropertyString("mintingPolicyHandling"))) {
            return;
        }
        
        String formDefId = getPropertyString("formDefIdStoreMintingPolicy");
        
        if (formDefId == null || formDefId.isEmpty()) {
            LogUtil.warn(getClassName(), "Unable to store minting policy to form. Encountered blank form ID.");
            return;
        }
        
        try {
            String policyId = policy.getPolicyId();
            NativeScript policyScript = policy.getPolicyScript();
            List<SecretKey> skeys = policy.getPolicyKeys();

            // Combine all secret key(s) into string delimited by semicolon for storage (e.g.: skey1;skey2;skey3)
            String skeyListAsCborHex = TokenUtil.getSecretKeysAsCborHexStringList(skeys);

            String policyIdField = getPropertyString("policyIdField");
            String policyScriptField = getPropertyString("policyScriptField");
            String policySecretKeyField = getPropertyString("policySecretKeyField");
            String minterAccountField = getPropertyString("minterAccountField");
            String isTestnetField = getPropertyString("isTestnetField");

            ObjectMapper mapper = new ObjectMapper();
            String policyScriptAsJson = mapper.writeValueAsString(policyScript);

            FormRowSet rowSet = new FormRowSet();

            FormRow row = new FormRow();

            if ((FormUtil.PROPERTY_ID).equals(policyIdField)) {
                row.setId(policyId);
            } else {
                row = addRow(row, policyIdField, policyId);
            }

            row = addRow(row, policyScriptField, policyScriptAsJson);
            row = addRow(row, policySecretKeyField, PluginUtil.encrypt(skeyListAsCborHex));
            row = addRow(row, minterAccountField, minter.baseAddress());
            row = addRow(row, isTestnetField, String.valueOf(isTest));

            rowSet.add(row);

            if (!rowSet.isEmpty()) {
                FormRowSet storedData = appService.storeFormData(appDef.getId(), appDef.getVersion().toString(), formDefId, rowSet, null);
                if (storedData == null) {
                    LogUtil.warn(getClassName(), "Unable to store minting policy to form. Encountered invalid form ID of '" + formDefId + "'.");
                }
            }
        } catch (CborSerializationException e) {
            throw new RuntimeException(e.getClass().getName() + " : " + e.getMessage());
        }
    }
    
    private void storeAssetDataToForm(Account minter, Policy policy, String tokenName, boolean isTest) 
            throws RuntimeException {
        
        String formDefId = getPropertyString("formDefIdStoreAssetData");
        
        if (formDefId == null || formDefId.isEmpty()) {
            LogUtil.warn(getClassName(), "Unable to store asset data to form. Encountered blank form ID.");
            return;
        }
        
        try {
            String policyId = policy.getPolicyId();

            String assetIdField = getPropertyString("assetIdField");
            String tokenNameField = getPropertyString("tokenNameField");
            String policyIdFkField = getPropertyString("policyIdFkField");
            String assetOwnerField = getPropertyString("assetOwnerField");
            String isAssetOnTestnetField = getPropertyString("isAssetOnTestnetField");

            FormRowSet rowSet = new FormRowSet();

            FormRow row = new FormRow();

            if ((FormUtil.PROPERTY_ID).equals(assetIdField)) {
                row.setId(TokenUtil.getAssetId(policyId, tokenName));
            } else {
                row = addRow(row, assetIdField, TokenUtil.getAssetId(policyId, tokenName));
            }
            
            row = addRow(row, tokenNameField, tokenName);
            row = addRow(row, policyIdFkField, policyId);
            row = addRow(row, assetOwnerField, minter.baseAddress());
            row = addRow(row, isAssetOnTestnetField, String.valueOf(isTest));

            rowSet.add(row);

            if (!rowSet.isEmpty()) {
                FormRowSet storedData = appService.storeFormData(appDef.getId(), appDef.getVersion().toString(), formDefId, rowSet, null);
                if (storedData == null) {
                    LogUtil.warn(getClassName(), "Unable to store asset data to form. Encountered invalid form ID of '" + formDefId + "'.");
                }
            }
        } catch (CborSerializationException e) {
            throw new RuntimeException(e.getClass().getName() + " : " + e.getMessage());
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
            NetworkType networkType, 
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
                transactionResult != null ? ExplorerLinkUtil.getTransactionExplorerUrl(networkType, transactionResult.getValue().getTransactionId()) : ""
        );
    }
    
    private void storeValuetoActivityVar(String activityId, String variable, String value) {
        if (activityId == null || activityId.isEmpty() || variable.isEmpty() || value == null) {
            return;
        }
        
        workflowManager.activityVariable(activityId, variable, value);
    }
}
