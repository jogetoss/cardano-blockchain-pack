package org.joget.cardano.lib.processformmodifier.actions;

import com.bloxbean.cardano.client.crypto.SecretKey;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.function.Output;
import com.bloxbean.cardano.client.function.TxBuilder;
import com.bloxbean.cardano.client.function.TxOutputBuilder;
import com.bloxbean.cardano.client.function.TxSigner;
import static com.bloxbean.cardano.client.function.helper.MintCreators.mintCreator;
import static com.bloxbean.cardano.client.function.helper.SignerProviders.signerFrom;
import com.bloxbean.cardano.client.transaction.spec.Asset;
import com.bloxbean.cardano.client.transaction.spec.MultiAsset;
import com.bloxbean.cardano.client.transaction.spec.Policy;
import com.bloxbean.cardano.client.transaction.spec.TransactionOutput;
import com.bloxbean.cardano.client.transaction.spec.script.NativeScript;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletRequest;
import org.joget.apps.app.dao.DatalistDefinitionDao;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.model.DatalistDefinition;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.datalist.model.DataList;
import org.joget.apps.datalist.model.DataListCollection;
import org.joget.apps.datalist.service.DataListService;
import org.joget.apps.form.model.FormData;
import org.joget.cardano.model.transaction.CardanoTransactionAction;
import org.joget.cardano.util.PluginUtil;
import org.joget.cardano.util.TokenUtil;
import org.joget.commons.util.LogUtil;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;

public class TokenBurnAction extends CardanoTransactionAction {

    private Policy policy = null;
    private String usedAddress = "";
    private MultiAsset multiAsset = new MultiAsset();
    
    @Override
    public String getName() {
        return "Token Burn";
    }

    @Override
    public String getDescription() {
        return "Burn native tokens and NFTs that was previously minted by an account on the Cardano blockchain.";
    }

    @Override
    public String getPropertyOptions() {
        return AppUtil.readPluginResource(getClassName(), "/properties/actions/TokenBurnAction.json", null, true, PluginUtil.MESSAGE_PATH);
    }
    
    @Override
    public TxOutputBuilder buildOutputs(FormData formData, HttpServletRequest request) {
        
        TxOutputBuilder txOutputBuilder = (txBuilderContext, outputs) -> {};
        
        try {
            this.policy = findTokenPolicy();
            this.usedAddress = request.getParameter("_usedAddress");
            this.multiAsset.setPolicyId(policy.getPolicyId());
            
            //Native Token Burns
            final boolean multipleNativeTokenBurns = "true".equalsIgnoreCase(getPropertyString("multipleNativeTokenBurns"));
            List<Output> nativeTokenBurnOutputs = multipleNativeTokenBurns
                    ? getNativeTokenBurnOutputsFromBinderData(getDataList(getPropertyString("nativeTokenDatalistId")))
                    : getNativeTokenBurnsFromProperties(formData);
            for (Output nativeTokenOutput : nativeTokenBurnOutputs) {
                txOutputBuilder = txOutputBuilder.and(nativeTokenOutput.outputBuilder());
            }

            //NFT Burns
            final boolean multipleNftBurns = "true".equalsIgnoreCase(getPropertyString("multipleNftBurns"));
            List<Output> nftBurnOutputs = multipleNftBurns
                    ? getNftBurnOutputsFromBinderData(getDataList(getPropertyString("nftDatalistId")))
                    : getNftBurnsFromProperties(formData);
            for (Output nftOutput : nftBurnOutputs) {
                txOutputBuilder = txOutputBuilder.and(nftOutput.outputBuilder());
            }
        } catch (CborSerializationException e) {
            throw new RuntimeException(e.getClass().getName() + " : " + e.getMessage());
        }
        
        return txOutputBuilder;
    }
    
    protected List<Output> getNativeTokenBurnsFromProperties(FormData formData) {
        List nativeTokenBurns = (ArrayList) getProperty("nativeTokenBurns");
        if (nativeTokenBurns == null || nativeTokenBurns.isEmpty()) {
            return new ArrayList<>();
        }
        
        List<Output> nativeTokenBurnOutputs = new ArrayList<>();
        try {
            for (Object o : nativeTokenBurns) {
                Map mapping = (Map) o;
                String assetName = mapping.get("assetName").toString();
                String burnAmount = mapping.get("burnAmount").toString();
                if (formData.getRequestParameter(burnAmount) != null) {
                    burnAmount = formData.getRequestParameter(burnAmount);
                }

                BigInteger amountBgInt = new BigInteger(burnAmount);

                if (amountBgInt.compareTo(BigInteger.ZERO) <= 0) {
                    LogUtil.info(getClassName(), "Skipping asset burn for \"" + assetName + "\" with invalid amount of \"" + burnAmount + "\". Not expecting an already negated value.");
                    continue;
                }

                multiAsset.getAssets().add(
                        new Asset(assetName, amountBgInt.negate())
                );

                nativeTokenBurnOutputs.add(
                        Output.builder()
                                .address(usedAddress)
                                .policyId(policy.getPolicyId())
                                .assetName(assetName)
                                .qty(amountBgInt)
                                .build()
                );
            }
        } catch (CborSerializationException e) {
            throw new RuntimeException(e.getClass().getName() + " : " + e.getMessage());
        }
        
        return nativeTokenBurnOutputs;
    }
    
    protected List<Output> getNftBurnsFromProperties(FormData formData) {
        List nftBurns = (ArrayList) getProperty("nftBurns");
        if (nftBurns == null || nftBurns.isEmpty()) {
            return new ArrayList<>();
        }
        
        List<Output> nftBurnOutputs = new ArrayList<>();
        try {
            for (Object o : nftBurns) {
                Map mapping = (Map) o;
                String assetName = mapping.get("assetName").toString();

                multiAsset.getAssets().add(
                        new Asset(assetName, BigInteger.ONE.negate())
                );

                nftBurnOutputs.add(
                        Output.builder()
                                .address(usedAddress)
                                .policyId(policy.getPolicyId())
                                .assetName(assetName)
                                .qty(BigInteger.ONE)
                                .build()
                );
            }
        } catch (CborSerializationException e) {
            throw new RuntimeException(e.getClass().getName() + " : " + e.getMessage());
        }
        
        return nftBurnOutputs;
    }
    
    protected List<Output> getNativeTokenBurnOutputsFromBinderData(DataList datalist) {
        List<Output> nativeTokenBurnOutputs = new ArrayList<>();
        
        try {
            DataListCollection binderData = datalist.getRows();
            if (binderData == null || binderData.isEmpty()) {
                return new ArrayList<>();
            }

            for (Object r : binderData) {
                String assetName = (String) DataListService.evaluateColumnValueFromRow(r, getPropertyString("nativeTokenAssetNameColumn"));
                String burnAmount = (String) DataListService.evaluateColumnValueFromRow(r, getPropertyString("nativeTokenBurnAmountColumn"));

                if (assetName == null || 
                        assetName.isBlank() ||
                        burnAmount == null || 
                        burnAmount.isBlank()) {
                    continue;
                }

                BigInteger amountBgInt = new BigInteger(burnAmount);

                if (amountBgInt.compareTo(BigInteger.ZERO) <= 0) {
                    LogUtil.info(getClassName(), "Skipping asset burn for \"" + assetName + "\" with invalid amount of \"" + burnAmount + "\". Not expecting an already negated value.");
                    continue;
                }

                multiAsset.getAssets().add(
                        new Asset(assetName, amountBgInt.negate())
                );
                
                nativeTokenBurnOutputs.add(
                        Output.builder()
                                .address(usedAddress)
                                .policyId(policy.getPolicyId())
                                .assetName(assetName)
                                .qty(amountBgInt)
                                .build()
                );
            }
        } catch (CborSerializationException e) {
            throw new RuntimeException(e.getClass().getName() + " : " + e.getMessage());
        }
        
        return nativeTokenBurnOutputs;
    }
    
    protected List<Output> getNftBurnOutputsFromBinderData(DataList datalist) {
        List<Output> nftBurnOutputs = new ArrayList<>();
        
        try {
            DataListCollection binderData = datalist.getRows();
            if (binderData == null || binderData.isEmpty()) {
                return new ArrayList<>();
            }

            for (Object r : binderData) {
                String assetName = (String) DataListService.evaluateColumnValueFromRow(r, getPropertyString("nftAssetNameColumn"));

                if (assetName == null || assetName.isBlank()) {
                    continue;
                }

                multiAsset.getAssets().add(
                        new Asset(assetName, BigInteger.ONE.negate())
                );
                
                nftBurnOutputs.add(
                        Output.builder()
                                .address(usedAddress)
                                .policyId(policy.getPolicyId())
                                .assetName(assetName)
                                .qty(BigInteger.ONE)
                                .build()
                );
            }
        } catch (CborSerializationException e) {
            throw new RuntimeException(e.getClass().getName() + " : " + e.getMessage());
        }
        
        return nftBurnOutputs;
    }
    
    protected Policy findTokenPolicy() {        
        try {
            NativeScript policyScript = NativeScript.deserializeJson(getPropertyString("policyScript"));
            List<SecretKey> skeys = TokenUtil.getSecretKeysStringAsList(PluginUtil.decrypt(getPropertyString("policyKeys")));

            return new Policy(policyScript, skeys);
        } catch (CborDeserializationException | JsonProcessingException e) {
            throw new RuntimeException(e.getClass().getName() + " : " + e.getMessage());
        }
    }
    
    @Override
    public TxBuilder modifyTxBuilder(TxBuilder txBuilder) {
        txBuilder = txBuilder
                .andThen(mintCreator(policy.getPolicyScript(), multiAsset))
                .andThen((context, transaction) -> {
                    List<TransactionOutput> txOutputs = transaction.getBody().getOutputs();
                    
                    TransactionOutput changeTxOutput = txOutputs.get(txOutputs.size() - 1);
                    List<TransactionOutput> burnOutputs = txOutputs.stream().limit(txOutputs.size() - 1).collect(Collectors.toList());
                    
                    for (TransactionOutput burnOutput : burnOutputs) {
                        txOutputs.remove(burnOutput);
                        
                        //Add ada value from burn outputs to changeoutput
                        changeTxOutput.getValue()
                                .setCoin(changeTxOutput.getValue().getCoin().add(burnOutput.getValue().getCoin()));
                    }
                });

        return txBuilder;
    }
    
    @Override
    public int numberOfAdditionalSigners() {
        return policy.getPolicyKeys().size();
    }
    
    @Override
    public TxSigner addSigners() {
        return signerFrom(policy);
    }
    
    protected DataList getDataList(String datalistId) throws BeansException {
        DataList datalist = null;
        
        ApplicationContext ac = AppUtil.getApplicationContext();
        AppDefinition appDef = AppUtil.getCurrentAppDefinition();
        DatalistDefinitionDao datalistDefinitionDao = (DatalistDefinitionDao) ac.getBean("datalistDefinitionDao");
        DataListService dataListService = (DataListService) ac.getBean("dataListService");
        
        DatalistDefinition datalistDefinition = datalistDefinitionDao.loadById(datalistId, appDef);

        if (datalistDefinition != null) {
            datalist = dataListService.fromJson(datalistDefinition.getJson());
        }
        
        return datalist;
    }
}
