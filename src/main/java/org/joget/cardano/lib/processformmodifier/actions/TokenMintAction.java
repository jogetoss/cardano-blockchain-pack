package org.joget.cardano.lib.processformmodifier.actions;

import com.bloxbean.cardano.client.cip.cip25.NFT;
import com.bloxbean.cardano.client.cip.cip25.NFTFile;
import com.bloxbean.cardano.client.cip.cip25.NFTMetadata;
import com.bloxbean.cardano.client.crypto.KeyGenUtil;
import com.bloxbean.cardano.client.crypto.Keys;
import com.bloxbean.cardano.client.crypto.SecretKey;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.function.Output;
import com.bloxbean.cardano.client.function.TxBuilder;
import com.bloxbean.cardano.client.function.TxOutputBuilder;
import com.bloxbean.cardano.client.function.TxSigner;
import static com.bloxbean.cardano.client.function.helper.AuxDataProviders.metadataProvider;
import static com.bloxbean.cardano.client.function.helper.MintCreators.mintCreator;
import static com.bloxbean.cardano.client.function.helper.SignerProviders.signerFrom;
import com.bloxbean.cardano.client.transaction.spec.Asset;
import com.bloxbean.cardano.client.transaction.spec.MultiAsset;
import com.bloxbean.cardano.client.transaction.spec.Policy;
import com.bloxbean.cardano.client.transaction.spec.script.NativeScript;
import com.bloxbean.cardano.client.util.PolicyUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

public class TokenMintAction extends CardanoTransactionAction {
    
    private Policy policy = null;
    private MultiAsset multiAsset = new MultiAsset();
    private List<NFT> nfts = new ArrayList<>();
    
    @Override
    public String getName() {
        return "Token Mint";
    }

    @Override
    public String getDescription() {
        return "Mint native tokens and NFTs on the Cardano blockchain.";
    }

    @Override
    public String getPropertyOptions() {
        return AppUtil.readPluginResource(getClassName(), "/properties/actions/TokenMintAction.json", null, true, PluginUtil.MESSAGE_PATH);
    }
    
    @Override
    public TxOutputBuilder buildOutputs(FormData formData, HttpServletRequest request) {
        
        TxOutputBuilder txOutputBuilder = (txBuilderContext, outputs) -> {};
        
        try {
            policy = findTokenPolicy();
            multiAsset.setPolicyId(policy.getPolicyId());
        
            //Native Token Mints
            final boolean multipleNativeTokenMints = "true".equalsIgnoreCase(getPropertyString("multipleNativeTokenMints"));
            List<Output> nativeTokenMintOutputs = multipleNativeTokenMints
                    ? getNativeTokenMintOutputsFromBinderData(getDataList(getPropertyString("nativeTokenDatalistId")))
                    : getNativeTokenMintsFromProperties(formData);
            for (Output nativeTokenOutput : nativeTokenMintOutputs) {
                txOutputBuilder = txOutputBuilder.and(nativeTokenOutput.mintOutputBuilder());
            }
            
            //NFT Mints
            final boolean multipleNftMints = "true".equalsIgnoreCase(getPropertyString("multipleNftMints"));
            List<Output> nftMintOutputs = multipleNftMints
                    ? getNftMintOutputsFromBinderData(getDataList(getPropertyString("nftDatalistId")))
                    : getNftMintsFromProperties(formData);
            for (Output nftOutput : nftMintOutputs) {
                txOutputBuilder = txOutputBuilder.and(nftOutput.mintOutputBuilder());
            }
            
        } catch (CborSerializationException e) {
            throw new RuntimeException(e.getClass().getName() + " : " + e.getMessage());
        }

        return txOutputBuilder;
    }
    
    protected List<Output> getNativeTokenMintsFromProperties(FormData formData) {
        List nativeTokenMints = (ArrayList) getProperty("nativeTokenMints");
        if (nativeTokenMints == null || nativeTokenMints.isEmpty()) {
            return new ArrayList<>();
        }
        
        List<Output> nativeTokenMintOutputs = new ArrayList<>();
        try {
            for (Object o : nativeTokenMints) {
                Map mapping = (Map) o;
                String assetName = mapping.get("assetName").toString();
                String receiverAddress = formData.getRequestParameter(mapping.get("receiverAddress").toString());
                String mintAmount = mapping.get("mintAmount").toString();
                if (formData.getRequestParameter(mintAmount) != null) {
                    mintAmount = formData.getRequestParameter(mintAmount);
                }

                BigInteger amountBgInt = new BigInteger(mintAmount);

                if (amountBgInt.compareTo(BigInteger.ZERO) <= 0) {
                    LogUtil.info(getClassName(), "Skipping receiver address \"" + receiverAddress + "\" with invalid amount of \"" + mintAmount + "\"");
                    continue;
                }

                multiAsset.getAssets().add(
                        new Asset(assetName, amountBgInt)
                );

                nativeTokenMintOutputs.add(
                        Output.builder()
                                .address(receiverAddress)
                                .policyId(policy.getPolicyId())
                                .assetName(assetName)
                                .qty(amountBgInt)
                                .build()
                );
            }
        } catch (CborSerializationException e) {
            throw new RuntimeException(e.getClass().getName() + " : " + e.getMessage());
        }
        
        return nativeTokenMintOutputs;
    }
    
    protected List<Output> getNftMintsFromProperties(FormData formData) {
        List nftMints = (ArrayList) getProperty("nftMints");
        if (nftMints == null || nftMints.isEmpty()) {
            return new ArrayList<>();
        }
        
        List<Output> nftOutputs = new ArrayList<>();
        try {
            for (Object o : nftMints) {
                Map mapping = (Map) o;
                String assetName = mapping.get("assetName").toString();
                String assetDesc = mapping.get("assetDesc").toString();
                String fileName = mapping.get("fileName").toString();
                String fileType = mapping.get("fileType").toString();
                String ipfsCid = mapping.get("ipfsContentId").toString();
                String receiverAddress = formData.getRequestParameter(mapping.get("receiverAddress").toString());

                multiAsset.getAssets().add(
                        new Asset(assetName, BigInteger.ONE)
                );

                nfts.add(
                        NFT.create()
                                .assetName(assetName)
                                .name(assetName)
                                .description(assetDesc)
                                .image("ipfs://" + ipfsCid)
                                .mediaType(fileType)
                                .addFile(NFTFile.create()
                                        .name(fileName)
                                        .mediaType(fileType)
                                        .src("ipfs/" + ipfsCid))
                );
                
                nftOutputs.add(
                        Output.builder()
                                .address(receiverAddress)
                                .policyId(policy.getPolicyId())
                                .assetName(assetName)
                                .qty(BigInteger.ONE)
                                .build()
                );
            }
        } catch (CborSerializationException e) {
            throw new RuntimeException(e.getClass().getName() + " : " + e.getMessage());
        }
        
        return nftOutputs;
    }
    
    protected List<Output> getNativeTokenMintOutputsFromBinderData(DataList datalist) {
        List<Output> nativeTokenMintOutputs = new ArrayList<>();
        
        try {
            DataListCollection binderData = datalist.getRows();
            if (binderData == null || binderData.isEmpty()) {
                return new ArrayList<>();
            }

            for (Object r : binderData) {
                String assetName = (String) DataListService.evaluateColumnValueFromRow(r, getPropertyString("nativeTokenAssetNameColumn"));
                String mintAmount = (String) DataListService.evaluateColumnValueFromRow(r, getPropertyString("nativeTokenMintAmountColumn"));
                String receiverAddress = (String) DataListService.evaluateColumnValueFromRow(r, getPropertyString("nativeTokenReceiverAddressColumn"));

                if (assetName == null || 
                        assetName.isBlank() ||
                        receiverAddress == null || 
                        receiverAddress.isBlank() || 
                        mintAmount == null || 
                        mintAmount.isBlank()) {
                    continue;
                }

                BigInteger amountBgInt = new BigInteger(mintAmount);

                if (amountBgInt.compareTo(BigInteger.ZERO) <= 0) {
                    LogUtil.info(getClassName(), "Skipping receiver address \"" + receiverAddress + "\" with invalid amount of \"" + mintAmount + "\"");
                    continue;
                }

                multiAsset.getAssets().add(
                        new Asset(assetName, amountBgInt)
                );
                
                nativeTokenMintOutputs.add(
                        Output.builder()
                                .address(receiverAddress)
                                .policyId(policy.getPolicyId())
                                .assetName(assetName)
                                .qty(amountBgInt)
                                .build()
                );
            }
        } catch (CborSerializationException e) {
            throw new RuntimeException(e.getClass().getName() + " : " + e.getMessage());
        }
        
        return nativeTokenMintOutputs;
    }
    
    protected List<Output> getNftMintOutputsFromBinderData(DataList datalist) {
        List<Output> nftOutputs = new ArrayList<>();
        
        try {
            DataListCollection binderData = datalist.getRows();
            if (binderData == null || binderData.isEmpty()) {
                return new ArrayList<>();
            }

            for (Object r : binderData) {
                String assetName = (String) DataListService.evaluateColumnValueFromRow(r, getPropertyString("nftAssetNameColumn"));
                String assetDesc = (String) DataListService.evaluateColumnValueFromRow(r, getPropertyString("nftAssetDescColumn"));
                String fileName = (String) DataListService.evaluateColumnValueFromRow(r, getPropertyString("nftFileNameColumn"));
                String fileType = (String) DataListService.evaluateColumnValueFromRow(r, getPropertyString("nftFileTypeColumn"));
                String ipfsCid = (String) DataListService.evaluateColumnValueFromRow(r, getPropertyString("nftIpfsCidColumn"));
                String receiverAddress = (String) DataListService.evaluateColumnValueFromRow(r, getPropertyString("nftReceiverAddressColumn"));

                if (assetName == null || 
                        assetName.isBlank() ||
                        receiverAddress == null || 
                        receiverAddress.isBlank()) {
                    continue;
                }

                multiAsset.getAssets().add(
                        new Asset(assetName, BigInteger.ONE)
                );

                nfts.add(
                        NFT.create()
                                .assetName(assetName)
                                .name(assetName)
                                .description(assetDesc)
                                .image("ipfs://" + ipfsCid)
                                .mediaType(fileType)
                                .addFile(NFTFile.create()
                                        .name(fileName)
                                        .mediaType(fileType)
                                        .src("ipfs/" + ipfsCid))
                );
                
                nftOutputs.add(
                        Output.builder()
                                .address(receiverAddress)
                                .policyId(policy.getPolicyId())
                                .assetName(assetName)
                                .qty(BigInteger.ONE)
                                .build()
                );
            }
        } catch (CborSerializationException e) {
            throw new RuntimeException(e.getClass().getName() + " : " + e.getMessage());
        }
        
        return nftOutputs;
    }
    
    protected Policy findTokenPolicy() {        
        try {
            if ("reuse".equalsIgnoreCase(getPropertyString("mintingPolicyHandling"))) {
                return new Policy(
                        NativeScript.deserializeJson(getPropertyString("policyScript")), 
                        TokenUtil.getSecretKeysStringAsList(PluginUtil.decrypt(getPropertyString("policyKeys")))
                );
            } else {
                if ("true".equalsIgnoreCase(getPropertyString("useCustomPolicyScript")) && !getPropertyString("manualPolicyScript").isBlank()) {
                    String policyScriptJson = getPropertyString("manualPolicyScript").replaceAll("\\s", "");
                    List<SecretKey> skeys = TokenUtil.getSecretKeysStringAsList(getPropertyString("manualPolicyKeys"));

                    Pattern pattern = Pattern.compile("#policyKey#");
                    Matcher matcher = pattern.matcher(policyScriptJson);

                    StringBuffer sb = new StringBuffer();
                    while (matcher.find()) {
                        Keys keys = KeyGenUtil.generateKey();
                        matcher.appendReplacement(sb, KeyGenUtil.getKeyHash(keys.getVkey()));
                        skeys.add(keys.getSkey());
                    }
                    matcher.appendTail(sb);

                    return new Policy(
                            NativeScript.deserializeJson(sb.toString()), 
                            skeys
                    );
                } else {
                    return PolicyUtil.createMultiSigScriptAllPolicy("", 1);
                }
            }
        } catch (CborDeserializationException | CborSerializationException | JsonProcessingException e) {
            throw new RuntimeException(e.getClass().getName() + " : " + e.getMessage());
        }
    }
    
    @Override
    public TxBuilder modifyTxBuilder(TxBuilder txBuilder) {
        try {
            txBuilder = txBuilder.andThen(mintCreator(policy.getPolicyScript(), multiAsset));
            
            if (!nfts.isEmpty()) {
                NFTMetadata nftMetadata = NFTMetadata.create();
                for (NFT nft : nfts) {
                    nftMetadata.addNFT(policy.getPolicyId(), nft);
                }
                txBuilder = txBuilder.andThen(metadataProvider(nftMetadata));
            }
            
            return txBuilder;
        } catch (CborSerializationException e) {
            throw new RuntimeException(e.getClass().getName() + " : " + e.getMessage());
        }
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
