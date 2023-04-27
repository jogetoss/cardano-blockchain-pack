package org.joget.cardano.lib;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.ProtocolParams;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.backend.api.DefaultUtxoSupplier;
import com.bloxbean.cardano.client.coinselection.UtxoSelectionStrategy;
import com.bloxbean.cardano.client.coinselection.impl.DefaultUtxoSelectionStrategyImpl;
import com.bloxbean.cardano.client.backend.model.TransactionContent;
import com.bloxbean.cardano.client.common.ADAConversionUtil;
import static com.bloxbean.cardano.client.common.CardanoConstants.LOVELACE;
import com.bloxbean.cardano.client.common.MinAdaCalculator;
import com.bloxbean.cardano.client.crypto.KeyGenUtil;
import com.bloxbean.cardano.client.crypto.SecretKey;
import com.bloxbean.cardano.client.crypto.VerificationKey;
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
import org.joget.cardano.util.PluginUtil;
import org.joget.cardano.util.BackendUtil;
import org.joget.cardano.util.TransactionUtil;
import java.util.Optional;
import java.util.Set;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.cardano.model.CardanoProcessTool;
import org.joget.cardano.model.NetworkType;
import org.joget.cardano.model.explorer.Explorer;
import org.joget.cardano.model.explorer.ExplorerFactory;
import static org.joget.cardano.model.explorer.ExplorerFactory.DEFAULT_EXPLORER;
import org.joget.cardano.util.TokenUtil;
import static org.joget.cardano.util.TransactionUtil.MAX_FEE_LIMIT;
import org.joget.commons.util.LogUtil;
import org.joget.commons.util.PluginThread;
import org.joget.workflow.util.WorkflowUtil;

public class CardanoBurnTokenTool extends CardanoProcessTool {
    
    @Override
    public String getName() {
        return "Cardano Burn Token Tool";
    }

    @Override
    public String getDescription() {
        return "Burn native tokens and NFTs that was previously minted by an account on the Cardano blockchain.";
    }

    @Override
    public String getPropertyOptions() {
        String backendConfigs = PluginUtil.readGenericBackendConfigs(getClassName());
        String wfVarMappings = PluginUtil.readGenericWorkflowVariableMappings(getClassName());
        return AppUtil.readPluginResource(getClassName(), "/properties/CardanoBurnTokenTool.json", new String[]{backendConfigs, wfVarMappings}, true, PluginUtil.MESSAGE_PATH);
    }
    
    @Override
    public boolean isInputDataValid() {        
        String formDefId = getPropertyString("formDefId");
        final String primaryKey = appService.getOriginProcessId(wfAssignment.getProcessId());
        
        FormRowSet rowSet = appService.loadFormData(appDef.getAppId(), appDef.getVersion().toString(), formDefId, primaryKey);
        
        if (rowSet == null || rowSet.isEmpty()) {
            LogUtil.warn(getClassName(), "Burn transaction aborted. No record found with record ID '" + primaryKey + "' from this form '" + formDefId + "'.");
            return false;
        }
        
        FormRow row = rowSet.get(0);
        
        final String senderAddress = row.getProperty(getPropertyString("senderAddress"));
        final String accountMnemonic = PluginUtil.decrypt(WorkflowUtil.processVariable(getPropertyString("accountMnemonic"), "", wfAssignment));
        
        final NetworkType networkType = BackendUtil.getNetworkType(props);

        final Account senderAccount = new Account(networkType.getNetwork(), accountMnemonic);

        if (!senderAddress.equals(senderAccount.baseAddress())) {
            LogUtil.warn(getClassName(), "Burn transaction aborted. Minter account encountered invalid mnemonic phrase.");
            return false;
        }
        
        final String assetId = row.getProperty(getPropertyString("assetId"));
        final String policyId = row.getProperty(getPropertyString("policyId"));
        final String derivedPolicyId = AssetUtil.getPolicyIdAndAssetName(assetId)._1;
        
        //Check if retrieved policy ID matches the derived policy ID from asset ID.
        if (!policyId.equals(derivedPolicyId)) {
            LogUtil.warn(getClassName(), "Burn transaction aborted. Retrieved policy ID does not match the derived policy ID from asset ID.");
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

            final String senderAddress = row.getProperty(getPropertyString("senderAddress"));
            final String accountMnemonic = PluginUtil.decrypt(WorkflowUtil.processVariable(getPropertyString("accountMnemonic"), "", wfAssignment));
            final String assetId = row.getProperty(getPropertyString("assetId"));
            final String policyId = row.getProperty(getPropertyString("policyId"));
            final String policySigningKey = PluginUtil.decrypt(WorkflowUtil.processVariable(getPropertyString("policySigningKey"), "", wfAssignment));
            final String tokenNameInHex = AssetUtil.getPolicyIdAndAssetName(assetId)._2;
            final String derivedTokenName = new String(HexUtil.decodeHexString(tokenNameInHex), StandardCharsets.UTF_8);
            final String amountToBurn = row.getProperty(getPropertyString("amountToBurn"));
            final boolean burnTypeNft = "nft".equalsIgnoreCase(getPropertyString("burnType"));

            final NetworkType networkType = BackendUtil.getNetworkType(props);

            final Account senderAccount = new Account(networkType.getNetwork(), accountMnemonic);

            BigInteger amountToBurnAbs;

            if (burnTypeNft) {
                amountToBurnAbs = BigInteger.ONE; //NFT = Exactly 1 amount of native token
            } else {
                amountToBurnAbs = new BigInteger(amountToBurn).abs();
            }

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
            UtxoSelectionStrategy utxoSelectionStrategy = new DefaultUtxoSelectionStrategyImpl(new DefaultUtxoSupplier(utxoService));
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
                LogUtil.warn(getClassName(), "Burn transaction aborted. Transaction fee in units of lovelace of " + fee.toString() + " exceeded set fee limit of " + feeLimit.toString() + ".");
                storeToWorkflowVariable(wfAssignment.getActivityId(), networkType, null, null);
                return null;
            }

            byte[] signedCBorBytes = signTransactionWithSenderAndSecretKey(senderAccount, skeys, transaction).serialize();

            final Result<String> transactionResult = transactionService.submitTransaction(signedCBorBytes);

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
                    //Store validated/confirmed txn result for current activity instance
                    storeToWorkflowVariable(wfAssignment.getActivityId(), networkType, transactionResult, validatedTransactionResult);

                    //Store validated/confirmed txn result for future running activity instance
                    String mostRecentActivityId = workflowManager.getRunningActivityIdByRecordId(appService.getOriginProcessId(wfAssignment.getProcessId()), wfAssignment.getProcessDefId(), null, null);
                    storeToWorkflowVariable(mostRecentActivityId, networkType, transactionResult, validatedTransactionResult);
                }
            });
            waitTransactionThread.start();

            return transactionResult;
        } catch (ApiException | CborSerializationException e) {
            throw new RuntimeException(e.getClass().getName() + " : " + e.getMessage());
        }
    }
    
    private void calculateEstimatedFeeAndMinAdaRequirementAndUpdateTxnOutput(Account minter, List<SecretKey> skeys, UtxoSelectionStrategy utxoSelectionStrategy,
                                                                             List<Utxo> utxos, Transaction transaction) 
            throws RuntimeException {
        
        try {
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
        } catch (ApiException | CborSerializationException e) {
            throw new RuntimeException(e.getClass().getName() + " : " + e.getMessage());
        }
    }

    
    private Transaction signTransactionWithSenderAndSecretKey(Account minter, List<SecretKey> skeys, Transaction transaction) {
        
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
            NetworkType networkType,
            Result<String> transactionResult, 
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
                transactionResult != null ? transactionResult.getValue() : ""
        );
        storeValuetoActivityVar(
                activityId, 
                transactionUrlVar, 
                transactionResult != null ? explorer.getTransactionUrl(transactionResult.getValue()) : ""
        );
    }
    
    private void storeValuetoActivityVar(String activityId, String variable, String value) {
        if (activityId == null || activityId.isEmpty() || variable.isEmpty() || value == null) {
            return;
        }
        
        workflowManager.activityVariable(activityId, variable, value);
    }
}
