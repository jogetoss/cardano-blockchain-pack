package org.joget.cardano.lib.processformmodifier.actions;

import com.bloxbean.cardano.client.common.ADAConversionUtil;
import static com.bloxbean.cardano.client.common.CardanoConstants.LOVELACE;
import com.bloxbean.cardano.client.function.Output;
import com.bloxbean.cardano.client.function.TxOutputBuilder;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
import org.joget.commons.util.LogUtil;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;

public class TokenTransferAction extends CardanoTransactionAction {
    
    @Override
    public String getName() {
        return "Token Transfer";
    }

    @Override
    public String getDescription() {
        return "Transfer assets from one account to another on the Cardano blockchain.";
    }

    @Override
    public String getPropertyOptions() {
        return AppUtil.readPluginResource(getClassName(), "/properties/actions/TokenTransferAction.json", null, true, PluginUtil.MESSAGE_PATH);
    }
    
    @Override
    public TxOutputBuilder buildOutputs(FormData formData, HttpServletRequest request) {
        
        TxOutputBuilder txOutputBuilder = (txBuilderContext, outputs) -> {};
        
        //ADA Transfers
        final boolean multipleAdaTransfers = "true".equalsIgnoreCase(getPropertyString("multipleAdaTransfers"));
        List<Output> adaOutputs = multipleAdaTransfers 
                ? getAdaOutputsFromBinderData(getDataList(getPropertyString("adaDatalistId"))) 
                : getAdaTransfersFromProperties(formData);
        for (Output adaOutput : adaOutputs) {
            txOutputBuilder = txOutputBuilder.and(adaOutput.outputBuilder());
        }
        
        //Native Token Transfers
        final boolean multipleNativeTokenTransfers = "true".equalsIgnoreCase(getPropertyString("multipleNativeTokenTransfers"));
        List<Output> nativeTokenOutputs = multipleNativeTokenTransfers
                ? getNativeTokenOutputsFromBinderData(getDataList(getPropertyString("nativeTokenDatalistId")))
                : getNativeTokenTransfersFromProperties(formData);
        for (Output nativeTokenOutput : nativeTokenOutputs) {
            txOutputBuilder = txOutputBuilder.and(nativeTokenOutput.outputBuilder());
        }
        
        //NFT Transfers
        final boolean multipleNftTransfers = "true".equalsIgnoreCase(getPropertyString("multipleNftTransfers"));
        List<Output> nftOutputs = multipleNftTransfers
                ? getNftOutputsFromBinderData(getDataList(getPropertyString("nftDatalistId")))
                : getNftTransfersFromProperties(formData);
        for (Output nftOutput : nftOutputs) {
            txOutputBuilder = txOutputBuilder.and(nftOutput.outputBuilder());
        }
        
        return txOutputBuilder;
    }
    
    protected List<Output> getAdaTransfersFromProperties(FormData formData) {
        List adaTransfers = (ArrayList) getProperty("adaTransfers");
        if (adaTransfers == null || adaTransfers.isEmpty()) {
            return new ArrayList<>();
        }
        
        List<Output> adaOutputs = new ArrayList<>();
        for (Object o : adaTransfers) {
            Map mapping = (Map) o;
            
            String receiverAddress = formData.getRequestParameter(mapping.get("receiverAddress").toString());
            String amount = mapping.get("amount").toString();
            if (formData.getRequestParameter(amount) != null) {
                amount = formData.getRequestParameter(amount);
            }
            
            BigInteger amountBgInt = ADAConversionUtil.adaToLovelace(new BigDecimal(amount));

            // Check for illogical transfer amount of less or equal to 0
            if (amountBgInt.compareTo(BigInteger.ZERO) <= 0) {
                LogUtil.info(getClassName(), "Skipping receiver address \"" + receiverAddress + "\" with invalid amount of \"" + amount + "\"");
                continue;
            }

            adaOutputs.add(
                    Output.builder()
                            .address(receiverAddress)
                            .assetName(LOVELACE)
                            .qty(amountBgInt)
                            .build()
            );
        }
        
        return adaOutputs;
    }
    
    protected List<Output> getNativeTokenTransfersFromProperties(FormData formData) {
        List nativeTokenTransfers = (ArrayList) getProperty("nativeTokenTransfers");
        if (nativeTokenTransfers == null || nativeTokenTransfers.isEmpty()) {
            return new ArrayList<>();
        }
        
        List<Output> nativeTokenOutputs = new ArrayList<>();
        for (Object o : nativeTokenTransfers) {
            Map mapping = (Map) o;
            String policyId = mapping.get("policyId").toString();
            String assetName = mapping.get("assetName").toString();
            String receiverAddress = formData.getRequestParameter(mapping.get("receiverAddress").toString());
            String amount = mapping.get("amount").toString();
            if (formData.getRequestParameter(amount) != null) {
                amount = formData.getRequestParameter(amount);
            }

            BigInteger amountBgInt = new BigInteger(amount);

            // Check for illogical transfer amount of less or equal to 0
            if (amountBgInt.compareTo(BigInteger.ZERO) <= 0) {
                LogUtil.info(getClassName(), "Skipping receiver address \"" + receiverAddress + "\" with invalid amount of \"" + amount + "\"");
                continue;
            }

            nativeTokenOutputs.add(
                    Output.builder()
                            .address(receiverAddress)
                            .policyId(policyId)
                            .assetName(assetName)
                            .qty(amountBgInt)
                            .build()
            );
        }
        
        return nativeTokenOutputs;
    }
    
    protected List<Output> getNftTransfersFromProperties(FormData formData) {
        List nftTransfers = (ArrayList) getProperty("nftTransfers");
        if (nftTransfers == null || nftTransfers.isEmpty()) {
            return new ArrayList<>();
        }
        
        List<Output> nftOutputs = new ArrayList<>();
        for (Object o : nftTransfers) {
            Map mapping = (Map) o;
            String policyId = mapping.get("policyId").toString();
            String assetName = mapping.get("assetName").toString();
            String receiverAddress = formData.getRequestParameter(mapping.get("receiverAddress").toString());

            nftOutputs.add(
                    Output.builder()
                            .address(receiverAddress)
                            .policyId(policyId)
                            .assetName(assetName)
                            .qty(BigInteger.ONE)
                            .build()
            );
        }
        
        return nftOutputs;
    }
    
    protected List<Output> getAdaOutputsFromBinderData(DataList datalist) {
        List<Output> adaOutputs = new ArrayList<>();
        
        DataListCollection binderData = datalist.getRows();
        if (binderData == null || binderData.isEmpty()) {
            return new ArrayList<>();
        }

        for (Object r : binderData) {
            String receiverAddress = (String) DataListService.evaluateColumnValueFromRow(r, getPropertyString("adaReceiverAddressColumn"));
            String amount = (String) DataListService.evaluateColumnValueFromRow(r, getPropertyString("adaAmountColumn"));

            // Skip row where receiver address or amount is empty
            if (receiverAddress == null || receiverAddress.isBlank() || amount == null || amount.isBlank()) {
                continue;
            }

            BigInteger amountBgInt = ADAConversionUtil.adaToLovelace(new BigDecimal(amount));

            // Check for illogical transfer amount of less or equal to 0
            if (amountBgInt.compareTo(BigInteger.ZERO) <= 0) {
                LogUtil.info(getClassName(), "Skipping receiver address \"" + receiverAddress + "\" with invalid amount of \"" + amount + "\"");
                continue;
            }

            adaOutputs.add(
                    Output.builder()
                            .address(receiverAddress)
                            .assetName(LOVELACE)
                            .qty(amountBgInt)
                            .build()
            );
        }
        
        return adaOutputs;
    }
    
    protected List<Output> getNativeTokenOutputsFromBinderData(DataList datalist) {
        List<Output> nativeTokenOutputs = new ArrayList<>();
        
        DataListCollection binderData = datalist.getRows();
        if (binderData == null || binderData.isEmpty()) {
            return new ArrayList<>();
        }

        for (Object r : binderData) {
            String policyId = (String) DataListService.evaluateColumnValueFromRow(r, getPropertyString("nativeTokenPolicyIdColumn"));
            String assetName = (String) DataListService.evaluateColumnValueFromRow(r, getPropertyString("nativeTokenAssetNameColumn"));
            String receiverAddress = (String) DataListService.evaluateColumnValueFromRow(r, getPropertyString("nativeTokenReceiverAddressColumn"));
            String amount = (String) DataListService.evaluateColumnValueFromRow(r, getPropertyString("nativeTokenAmountColumn"));

            // Skip row where receiver address or amount is empty
            if (policyId == null || 
                    policyId.isBlank() ||
                    assetName == null || 
                    assetName.isBlank() ||
                    receiverAddress == null || 
                    receiverAddress.isBlank() || 
                    amount == null || 
                    amount.isBlank()) {
                continue;
            }

            BigInteger amountBgInt = new BigInteger(amount);

            // Check for illogical transfer amount of less or equal to 0
            if (amountBgInt.compareTo(BigInteger.ZERO) <= 0) {
                LogUtil.info(getClassName(), "Skipping receiver address \"" + receiverAddress + "\" with invalid amount of \"" + amount + "\"");
                continue;
            }

            nativeTokenOutputs.add(
                    Output.builder()
                            .address(receiverAddress)
                            .policyId(policyId)
                            .assetName(assetName)
                            .qty(amountBgInt)
                            .build()
            );
        }
        
        return nativeTokenOutputs;
    }
    
    protected List<Output> getNftOutputsFromBinderData(DataList datalist) {
        List<Output> nftOutputs = new ArrayList<>();
        
        DataListCollection binderData = datalist.getRows();
        if (binderData == null || binderData.isEmpty()) {
            return new ArrayList<>();
        }

        for (Object r : binderData) {
            String policyId = (String) DataListService.evaluateColumnValueFromRow(r, getPropertyString("nftPolicyIdColumn"));
            String assetName = (String) DataListService.evaluateColumnValueFromRow(r, getPropertyString("nftAssetNameColumn"));
            String receiverAddress = (String) DataListService.evaluateColumnValueFromRow(r, getPropertyString("nftReceiverAddressColumn"));

            // Skip row where receiver address or amount is empty
            if (policyId == null || 
                    policyId.isBlank() ||
                    assetName == null || 
                    assetName.isBlank() ||
                    receiverAddress == null || 
                    receiverAddress.isBlank()) {
                continue;
            }

            nftOutputs.add(
                    Output.builder()
                            .address(receiverAddress)
                            .policyId(policyId)
                            .assetName(assetName)
                            .qty(BigInteger.ONE)
                            .build()
            );
        }
        
        return nftOutputs;
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
