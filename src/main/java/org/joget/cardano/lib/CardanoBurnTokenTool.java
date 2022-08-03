package org.joget.cardano.lib;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.helper.FeeCalculationService;
import com.bloxbean.cardano.client.api.model.ProtocolParams;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.api.BlockService;
import com.bloxbean.cardano.client.backend.api.DefaultUtxoSupplier;
import com.bloxbean.cardano.client.backend.api.EpochService;
import com.bloxbean.cardano.client.backend.api.TransactionService;
import com.bloxbean.cardano.client.backend.api.UtxoService;
import com.bloxbean.cardano.client.coinselection.UtxoSelectionStrategy;
import com.bloxbean.cardano.client.coinselection.impl.DefaultUtxoSelectionStrategyImpl;
import com.bloxbean.cardano.client.backend.model.TransactionContent;
import com.bloxbean.cardano.client.common.ADAConversionUtil;
import static com.bloxbean.cardano.client.common.CardanoConstants.LOVELACE;
import com.bloxbean.cardano.client.common.MinAdaCalculator;
import com.bloxbean.cardano.client.common.model.Network;
import com.bloxbean.cardano.client.crypto.KeyGenUtil;
import com.bloxbean.cardano.client.crypto.SecretKey;
import com.bloxbean.cardano.client.crypto.VerificationKey;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.transaction.TransactionSigner;
import com.bloxbean.cardano.client.transaction.spec.Asset;
import com.bloxbean.cardano.client.transaction.spec.MultiAsset;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.transaction.spec.TransactionBody;
import com.bloxbean.cardano.client.transaction.spec.TransactionInput;
import com.bloxbean.cardano.client.transaction.spec.TransactionOutput;
import com.bloxbean.cardano.client.transaction.spec.TransactionWitnessSet;
import com.bloxbean.cardano.client.transaction.spec.Value;
import com.bloxbean.cardano.client.transaction.spec.script.ScriptAll;
import com.bloxbean.cardano.client.transaction.spec.script.ScriptPubkey;
import com.bloxbean.cardano.client.util.AssetUtil;
import com.bloxbean.cardano.client.util.HexUtil;
import com.bloxbean.cardano.client.util.Tuple;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import org.joget.cardano.service.PluginUtil;
import org.joget.cardano.service.BackendUtil;
import org.joget.cardano.service.TransactionUtil;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.service.AppService;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.cardano.service.ExplorerLinkUtil;
import org.joget.cardano.service.TokenUtil;
import static org.joget.cardano.service.TransactionUtil.MAX_FEE_LIMIT;
import org.joget.commons.util.LogUtil;
import org.joget.commons.util.PluginThread;
import org.joget.plugin.base.DefaultApplicationPlugin;
import org.joget.workflow.model.WorkflowAssignment;
import org.joget.workflow.model.service.WorkflowManager;
import org.joget.workflow.util.WorkflowUtil;
import org.springframework.context.ApplicationContext;

public class CardanoBurnTokenTool extends DefaultApplicationPlugin {

    BackendService backendService;
    BlockService blockService;
    FeeCalculationService feeCalculationService;
    TransactionService transactionService;
    UtxoService utxoService;
    EpochService epochService;
    
    UtxoSupplier utxoSupplier;
    
    AppService appService;
    WorkflowAssignment wfAssignment;
    AppDefinition appDef;
    WorkflowManager workflowManager;
    
    protected void initBackend() {
        backendService = BackendUtil.getBackendService(getProperties());
        
        blockService = backendService.getBlockService();
        feeCalculationService = backendService.getFeeCalculationService();
        transactionService = backendService.getTransactionService();
        utxoService = backendService.getUtxoService();
        epochService = backendService.getEpochService();
        
        utxoSupplier = new DefaultUtxoSupplier(utxoService);
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
        return "Cardano Burn Token Tool";
    }

    @Override
    public String getVersion() {
        return PluginUtil.getProjectVersion(this.getClass());
    }

    @Override
    public String getDescription() {
        return "Burn native tokens and NFTs that was previously minted by an account on the Cardano blockchain.";
    }

    @Override
    public Object execute(Map props) {
        initUtils(props);
        
        String formDefId = getPropertyString("formDefId");
        final String primaryKey = appService.getOriginProcessId(wfAssignment.getProcessId());
        
        FormRowSet rowSet = appService.loadFormData(appDef.getAppId(), appDef.getVersion().toString(), formDefId, primaryKey);
        
        if (rowSet == null || rowSet.isEmpty()) {
            LogUtil.warn(getClass().getName(), "Token burning aborted. No record found with record ID '" + primaryKey + "' from this form '" + formDefId + "'.");
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
            
            final String assetId = row.getProperty(getPropertyString("assetId"));
            final String policyId = row.getProperty(getPropertyString("policyId"));
            final String policySigningKey = PluginUtil.decrypt(WorkflowUtil.processVariable(getPropertyString("policySigningKey"), "", wfAssignment));
            final String derivedPolicyId = AssetUtil.getPolicyIdAndAssetName(assetId)._1;
            final String tokenNameInHex = AssetUtil.getPolicyIdAndAssetName(assetId)._2;
            final String derivedTokenName = new String(HexUtil.decodeHexString(tokenNameInHex), StandardCharsets.UTF_8);
            final String amountToBurn = row.getProperty(getPropertyString("amountToBurn"));
            
            //Check if retrieved policy ID matches the derived policy ID from asset ID.
            if (!policyId.equals(derivedPolicyId)) {
                LogUtil.warn(getClass().getName(), "Token burning aborted. Retrieved policy ID does not match the derived policy ID from asset ID.");
                return null;
            }
            
            BigInteger amountToBurnAbs;
            
            if ("nft".equalsIgnoreCase(getPropertyString("burnType"))) {
                amountToBurnAbs = BigInteger.ONE; //NFT = Exactly 1 amount of native token
            } else {
                amountToBurnAbs = new BigInteger(amountToBurn).abs();
            }
            
            initBackend();
            
            final List<SecretKey> skeys = TokenUtil.getSecretKeysStringAsList(policySigningKey);
            ScriptAll scriptAll = new ScriptAll();
            for (SecretKey skey : skeys) {
                VerificationKey vkey = KeyGenUtil.getPublicKeyFromPrivateKey(skey);
                scriptAll.addScript(ScriptPubkey.create(vkey));
            }
            
            /* Burn logic starts here */
            MultiAsset multiAsset = new MultiAsset();
            multiAsset.setPolicyId(policyId);
            //negative amount required
            multiAsset.getAssets().add(new Asset(derivedTokenName, amountToBurnAbs.negate()));
        
            //Get utxos for such asset ID
            UtxoSelectionStrategy utxoSelectionStrategy = new DefaultUtxoSelectionStrategyImpl(utxoSupplier);
            List<Utxo> utxos = utxoSelectionStrategy.selectUtxos(senderAddress, assetId, amountToBurnAbs, Collections.EMPTY_SET);
            
            //Create inputs
            List<TransactionInput> inputs = new ArrayList<>();
            utxos.forEach(utxo -> {
                TransactionInput transactionInput = TransactionInput.builder()
                        .transactionId(utxo.getTxHash())
                        .index(utxo.getOutputIndex())
                        .build();

                inputs.add(transactionInput);
            });
            
            //Create outputs
            List<TransactionOutput> outputs = new ArrayList<>();
            TransactionOutput transactionOutput = TransactionOutput.builder()
                    .address(senderAddress)
                    .value(Value.builder()
                            .multiAssets(new ArrayList<>())
                            .coin(BigInteger.ZERO)
                            .build())
                    .build();
            
            //Copy selected utxos content to transactionoutput
            utxos.forEach(utxo -> copyUtxoValuesToChangeOutput(transactionOutput, utxo));

            //Update asset value. Deduct burn amount
            transactionOutput.getValue().getMultiAssets()
                    .stream().filter(mulAsset -> mulAsset.getPolicyId().equals(policyId))
                    .forEach(ma -> {
                        Optional<Asset> assetOptional = ma.getAssets().stream().filter(ast ->
                                ast.getName().equals(HexUtil.encodeHexString(derivedTokenName.getBytes(StandardCharsets.UTF_8), true)))
                                .findFirst();
                        if (assetOptional.isPresent()) {
                            Asset asset = assetOptional.get();
                            asset.setValue(asset.getValue().add(amountToBurnAbs.negate()));
                        }
                    });
            outputs.add(transactionOutput);
            
            long ttl = TransactionUtil.getTtl(blockService);
            
            TransactionBody body = TransactionBody.builder()
                .inputs(inputs)
                .outputs(outputs)
                .mint(Arrays.asList(multiAsset))
                .ttl(ttl)
                .fee(BigInteger.valueOf(170000)) //dummy fee to calculate actual fee
                .build();

            //Add script witness
            TransactionWitnessSet transactionWitnessSet = TransactionWitnessSet.builder()
                    .nativeScripts(Arrays.asList(scriptAll))
                    .build();

            Transaction transaction = Transaction.builder()
                    .body(body)
                    .witnessSet(transactionWitnessSet)
                    .build();

            calculateEstimatedFeeAndMinAdaRequirementAndUpdateTxnOutput(senderAccount, skeys, utxoSelectionStrategy, utxos, transaction);

            final BigInteger fee = transaction.getBody().getFee();
            
            BigInteger feeLimit = MAX_FEE_LIMIT;
            if (!getPropertyString("feeLimit").isBlank()) {
                feeLimit = ADAConversionUtil.adaToLovelace(new BigDecimal(getPropertyString("feeLimit")));
            }
            if (!TransactionUtil.checkFeeLimit(fee, feeLimit)) {
                LogUtil.warn(getClass().getName(), "Burn transaction aborted. Transaction fee in units of lovelace of " + fee.toString() + " exceeded set fee limit of " + feeLimit.toString() + ".");
                storeToWorkflowVariable(wfAssignment.getActivityId(), isTest, null, null);
                return null;
            }
            
            byte[] signedCBorBytes = signTransactionWithSenderAndSecretKey(senderAccount, skeys, transaction).serialize();

            final Result<String> transactionResult = transactionService.submitTransaction(signedCBorBytes);

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
                    validatedTransactionResult = TransactionUtil.waitForTransactionHash(transactionService, transactionResult);
                } catch (Exception ex) {
                    LogUtil.error(getClass().getName(), ex, "Error waiting for transaction validation...");
                }
                
                if (validatedTransactionResult != null) {
                    //Store validated/confirmed txn result for current activity instance
                    storeToWorkflowVariable(wfAssignment.getActivityId(), isTest, transactionResult, validatedTransactionResult);

                    //Store validated/confirmed txn result for future running activity instance
                    String mostRecentActivityId = workflowManager.getRunningActivityIdByRecordId(appService.getOriginProcessId(wfAssignment.getProcessId()), wfAssignment.getProcessDefId(), null, null);
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
    
    private void calculateEstimatedFeeAndMinAdaRequirementAndUpdateTxnOutput(Account minter, List<SecretKey> skeys, UtxoSelectionStrategy utxoSelectionStrategy,
                                                                             List<Utxo> utxos, Transaction transaction) 
            throws ApiException, CborSerializationException, CborDeserializationException {
        List<TransactionInput> inputs = transaction.getBody().getInputs();
        TransactionOutput transactionOutput = transaction.getBody().getOutputs().get(0);

        //Calculate fee with signed transaction
        BigInteger estimatedFee = feeCalculationService.calculateFee(signTransactionWithSenderAndSecretKey(minter, skeys, transaction));

        //Check if min-ada is there in transaction output. If not, get some additional utxos
        ProtocolParams protocolParams = epochService.getProtocolParameters().getValue();
        MinAdaCalculator minAdaCalculator = new MinAdaCalculator(protocolParams);
        BigInteger minAda = minAdaCalculator.calculateMinAda(transactionOutput);

        Set<Utxo> utxosToExclude = new HashSet<>();
        utxosToExclude.addAll(utxos);

        //Check if not enough lovelace in the transaction output. Get some additional utxos and recalculate fee again and iterate
        while (minAda.compareTo(transactionOutput.getValue().getCoin().subtract(estimatedFee)) == 1) {
            //Get some additional utxos
            BigInteger reqAdditionalLovelace = minAda.subtract(transactionOutput.getValue().getCoin().subtract(estimatedFee));
            List<Utxo> additionalUtxos = utxoSelectionStrategy.selectUtxos(minter.baseAddress(), LOVELACE, reqAdditionalLovelace, utxosToExclude);
            if (!additionalUtxos.isEmpty()) {
                additionalUtxos.forEach(utxo -> {
                    TransactionInput transactionInput = TransactionInput.builder()
                            .transactionId(utxo.getTxHash())
                            .index(utxo.getOutputIndex())
                            .build();
                    inputs.add(transactionInput);

                    copyUtxoValuesToChangeOutput(transactionOutput, utxo);
                });
                utxosToExclude.addAll(additionalUtxos);

                //Calculate fee again as new utxos were added
                estimatedFee = feeCalculationService.calculateFee(signTransactionWithSenderAndSecretKey(minter, skeys, transaction));
            }
            minAda = minAdaCalculator.calculateMinAda(transactionOutput);
        }

        //Set final estimated fee and lovelace amount in output
        transaction.getBody().setFee(estimatedFee);
        transactionOutput.getValue().setCoin(transactionOutput.getValue().getCoin().subtract(estimatedFee));
    }

    
    private Transaction signTransactionWithSenderAndSecretKey(Account minter, List<SecretKey> skeys, Transaction transaction)
            throws CborSerializationException, CborDeserializationException {
        //sign with minter account
        Transaction signTxn = minter.sign(transaction);

        //sign with secret key
        for (SecretKey skey : skeys) {
            signTxn = TransactionSigner.INSTANCE.sign(signTxn, skey);
        }

        return signTxn;
    }
    
    private void copyUtxoValuesToChangeOutput(TransactionOutput changeOutput, Utxo utxo) {
        utxo.getAmount().forEach(utxoAmt -> { //For each amt in utxo
            String utxoUnit = utxoAmt.getUnit();
            BigInteger utxoQty = utxoAmt.getQuantity();
            if (utxoUnit.equals(LOVELACE)) {
                BigInteger existingCoin = changeOutput.getValue().getCoin();
                if (existingCoin == null) existingCoin = BigInteger.ZERO;
                changeOutput.getValue().setCoin(existingCoin.add(utxoQty));
            } else {
                Tuple<String, String> policyIdAssetName = AssetUtil.getPolicyIdAndAssetName(utxoUnit);

                //Find if the policy id is available
                Optional<MultiAsset> multiAssetOptional =
                        changeOutput.getValue().getMultiAssets().stream().filter(ma -> policyIdAssetName._1.equals(ma.getPolicyId())).findFirst();
                if (multiAssetOptional.isPresent()) {
                    Optional<Asset> assetOptional = multiAssetOptional.get().getAssets().stream()
                            .filter(ast -> policyIdAssetName._2.equals(ast.getName()))
                            .findFirst();
                    if (assetOptional.isPresent()) {
                        BigInteger changeVal = assetOptional.get().getValue().add(utxoQty);
                        assetOptional.get().setValue(changeVal);
                    } else {
                        Asset asset = new Asset(policyIdAssetName._2, utxoQty);
                        multiAssetOptional.get().getAssets().add(asset);
                    }
                } else {
                    Asset asset = new Asset(policyIdAssetName._2, utxoQty);
                    MultiAsset multiAsset = new MultiAsset(policyIdAssetName._1, new ArrayList<>(Arrays.asList(asset)));
                    changeOutput.getValue().getMultiAssets().add(multiAsset);
                }
            }
        });

        //Remove any empty MultiAssets
        List<MultiAsset> multiAssets = changeOutput.getValue().getMultiAssets();
        List<MultiAsset> markedForRemoval = new ArrayList<>();
        if (multiAssets != null && !multiAssets.isEmpty()) {
            multiAssets.forEach(ma -> {
                if (ma.getAssets() == null || ma.getAssets().isEmpty())
                    markedForRemoval.add(ma);
            });

            if (!markedForRemoval.isEmpty()) {
                multiAssets.removeAll(markedForRemoval);
            }
        }
    }
    
    protected void storeToWorkflowVariable(
            String activityId,
            boolean isTest,
            Result<String> transactionResult, 
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
                transactionResult != null ? transactionResult.getValue() : ""
        );
        storeValuetoActivityVar(
                activityId, 
                transactionUrlVar, 
                transactionResult != null ? ExplorerLinkUtil.getTransactionExplorerUrl(isTest, transactionResult.getValue()) : ""
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
        return AppUtil.readPluginResource(getClass().getName(), "/properties/CardanoBurnTokenTool.json", new String[]{backendConfigs, wfVarMappings}, true, PluginUtil.MESSAGE_PATH);
    }
}
