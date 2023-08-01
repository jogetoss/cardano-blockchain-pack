package org.joget.cardano.lib.processformmodifier.components;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.api.BlockService;
import com.bloxbean.cardano.client.backend.api.DefaultProtocolParamsSupplier;
import com.bloxbean.cardano.client.backend.api.DefaultUtxoSupplier;
import com.bloxbean.cardano.client.backend.api.EpochService;
import com.bloxbean.cardano.client.backend.api.TransactionService;
import com.bloxbean.cardano.client.backend.api.UtxoService;
import com.bloxbean.cardano.client.cip.cip20.MessageMetadata;
import com.bloxbean.cardano.client.coinselection.impl.LargestFirstUtxoSelectionStrategy;
import com.bloxbean.cardano.client.common.ADAConversionUtil;
import com.bloxbean.cardano.client.function.TxBuilder;
import com.bloxbean.cardano.client.function.TxBuilderContext;
import com.bloxbean.cardano.client.function.TxInputBuilder;
import com.bloxbean.cardano.client.function.TxOutputBuilder;
import com.bloxbean.cardano.client.function.TxSigner;
import static com.bloxbean.cardano.client.function.helper.AuxDataProviders.metadataProvider;
import static com.bloxbean.cardano.client.function.helper.BalanceTxBuilders.balanceTx;
import com.bloxbean.cardano.client.function.helper.InputBuilders;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.util.HexUtil;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.service.AppService;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.model.FormData;
import org.joget.apps.form.model.FormRowSet;
import org.joget.apps.form.service.FormService;
import org.joget.cardano.model.NetworkType;
import org.joget.cardano.model.transaction.CardanoTransactionAction;
import org.joget.cardano.util.BackendUtil;
import org.joget.cardano.util.MetadataUtil;
import org.joget.cardano.util.PluginUtil;
import org.joget.cardano.util.TxUtil;
import static org.joget.cardano.util.TxUtil.DEFAULT_WAIT_INTERVAL_MS;
import org.joget.commons.util.LogUtil;
import org.joget.plugin.base.PluginManager;
import org.springframework.context.ApplicationContext;

public class TransactionHandler {
    
    private final HttpServletRequest request;
    private final Map pluginProperties;
    
    private final BackendService backendService;
    private final NetworkType networkType;
    
    //Cardano backend services
//    protected AssetService assetService;
    protected BlockService blockService;
//    protected NetworkInfoService networkInfoService;
    protected TransactionService transactionService;
    private final UtxoService utxoService;
//    protected AddressService addressService;
//    protected AccountService accountService;
    private final EpochService epochService;
//    protected MetadataService metadataService;
//    protected TransactionHelperService transactionHelperService;
//    protected UtxoTransactionBuilder utxoTransactionBuilder;
//    protected FeeCalculationService feeCalculationService;
    
    private final AppDefinition appDef;
    private final ApplicationContext appContext;
    private final PluginManager pluginManager;
    
    public TransactionHandler(Map pluginProperties, HttpServletRequest request) {
        this.request = request;
        this.pluginProperties = pluginProperties;
        
        this.backendService = BackendUtil.getBackendService(pluginProperties);
        this.networkType = BackendUtil.getNetworkType(pluginProperties);
        
//        this.assetService = backendService.getAssetService();
        this.blockService = backendService.getBlockService();
//        this.networkInfoService = backendService.getNetworkInfoService();
        this.transactionService = backendService.getTransactionService();
        this.utxoService = backendService.getUtxoService();
//        this.addressService = backendService.getAddressService();
//        this.accountService = backendService.getAccountService();
        this.epochService = backendService.getEpochService();
//        this.metadataService = backendService.getMetadataService();
//        this.transactionHelperService = backendService.getTransactionHelperService();
//        this.utxoTransactionBuilder = backendService.getUtxoTransactionBuilder();
//        this.feeCalculationService = backendService.getFeeCalculationService();

        this.appDef = AppUtil.getCurrentAppDefinition();
        this.appContext = AppUtil.getApplicationContext();
        this.pluginManager = (PluginManager) AppUtil.getApplicationContext().getBean("pluginManager");
    }
    
    /**
     * Prepares the unsigned transaction
     * @return an unsigned Transaction object
     * @see Transaction
    */
    public Transaction createUnsignedTx() {
        TxInputBuilder txInputBuilder;
        String changeAddress;
        
        if ("internal".equalsIgnoreCase((String) pluginProperties.get("walletHandler"))) {
            final Account senderAccount = getInternalAccount();
            changeAddress = senderAccount.baseAddress();
            
            txInputBuilder = InputBuilders.createFromSender(senderAccount.baseAddress(), changeAddress);
        } else {
            final String walletUtxosJson = request.getHeader("wallet-utxos-json");
            changeAddress = request.getParameter("_changeAddress");
            
            txInputBuilder = InputBuilders.createFromUtxos(getUtxosFromJson(walletUtxosJson), changeAddress);
        }
        
        return constructTx(txInputBuilder, changeAddress);
    }
    
    public boolean internalIsWithinFeeLimit() {
        final String unsignedTxCbor = request.getHeader("unsigned-tx-cbor");
        final String adaFeeLimit = (String) pluginProperties.get("feeLimit");
        
        if (!adaFeeLimit.isBlank()) {
            Transaction unsignedTx;
            try {
                unsignedTx = Transaction.deserialize(HexUtil.decodeHexString(unsignedTxCbor));
            } catch (Exception ex) {
                LogUtil.error(getClassName(), ex, "Unable to deserialize tx for fee limit checking");
                return false;
            }
            
            final BigInteger feeLimit = ADAConversionUtil.adaToLovelace(new BigDecimal(adaFeeLimit));
            
            if (!TxUtil.checkFeeLimit(unsignedTx.getBody().getFee(), feeLimit)) {
                return false;
            }
        }
        
        return true;
    }
    
    public String internalSignAndSubmitTx() {
        final String unsignedTxCbor = request.getHeader("unsigned-tx-cbor");

        try {
            final Account senderAccount = getInternalAccount();

            Transaction unsignedTx = Transaction.deserialize(HexUtil.decodeHexString(unsignedTxCbor));
            Transaction signedTx = senderAccount.sign(unsignedTx);

            return transactionService.submitTransaction(signedTx.serialize()).getValue();
        } catch (Exception ex) {
            LogUtil.error(getClassName(), ex, "Unable to deserialize tx for internal wallet sign and submit");
        }
        
        return null;
    }
    
    public boolean waitForTx(String transactionId) {
        try {
            int count = 0;
            while (count < 60) {
                if (transactionService.getTransaction(transactionId).isSuccessful()) {
                    return true;
                }

                count++;
                Thread.sleep(DEFAULT_WAIT_INTERVAL_MS);
            }
        } catch (Exception ex) {
            LogUtil.error(getClassName(), ex, "Unable to wait for tx confirmation");
        }
        
        return false;
    }
    
    private Transaction constructTx(final TxInputBuilder txInputBuilder, final String changeAddress) {
        final Map txActionProps = (Map) pluginProperties.get("txAction");
        final CardanoTransactionAction actionPlugin = (CardanoTransactionAction) pluginManager.getPlugin(txActionProps.get("className").toString());
        if (actionPlugin == null) {
            LogUtil.warn(getClassName(), "Encountered unknown transaction action plugin!");
            return null;
        }
        actionPlugin.setProperties((Map) txActionProps.get("properties"));

        FormService formService = (FormService) appContext.getBean("formService");
        FormData formData = formService.retrieveFormDataFromRequest(null, request);
        
        TxOutputBuilder txOutputBuilder = actionPlugin.buildOutputs(formData, request);
        
        if (txOutputBuilder == null) {
            LogUtil.warn(getClassName(), "No valid transaction outputs found. Transaction must have at least 1 output.");
            return null;
        }
        
        TxBuilder txBuilder = txOutputBuilder.buildInputs(txInputBuilder);
        
        List metadataFields = (ArrayList) pluginProperties.get("metadata");
        if (metadataFields != null) {
            MessageMetadata cip20Metadata = MetadataUtil.generateMsgMetadataFromFormData(metadataFields, formData);
            txBuilder = txBuilder.andThen(metadataProvider(cip20Metadata));
        }
        
        TxBuilder modifiedTxBuilder = actionPlugin.modifyTxBuilder(txBuilder);
        if (modifiedTxBuilder != null) {
            txBuilder = modifiedTxBuilder;
        }
        
        final int numberOfSigners = 2 + actionPlugin.numberOfAdditionalSigners();
        
        txBuilder = txBuilder.andThen(balanceTx(changeAddress, numberOfSigners));
        
        UtxoSupplier utxoSupplier = new DefaultUtxoSupplier(utxoService);
        TxBuilderContext txBuilderContext = TxBuilderContext.init(utxoSupplier, new DefaultProtocolParamsSupplier(epochService));
        txBuilderContext.setUtxoSelectionStrategy(new LargestFirstUtxoSelectionStrategy(utxoSupplier));
        
        Transaction unsignedTx = txBuilderContext.build(txBuilder);
        
        TxSigner signers = actionPlugin.addSigners();
        if (signers != null) {
            unsignedTx = signers.sign(unsignedTx);
        }
        
        try {
            unsignedTx.getBody().setTtl(TxUtil.getTtl(blockService));
        } catch (Exception ex) {
            LogUtil.error(getClassName(), ex, "Unable to prepare final unsigned tx");
            return null;
        }
        
        return unsignedTx;
    }
    
    private Account getInternalAccount() {
        FormService formService = (FormService) appContext.getBean("formService");
        AppService appService = (AppService) appContext.getBean("appService");
        FormData formData = formService.retrieveFormDataFromRequest(null, request);

        final String formDefId = (String) pluginProperties.get("formDefIdAccountData");
        final String primaryKey = formData.getRequestParameter((String) pluginProperties.get("accountDataRecordId"));
        final FormRowSet rowSet = appService.loadFormData(
                appDef.getAppId(), 
                appDef.getVersion().toString(), 
                formDefId, 
                primaryKey
        );
        if (rowSet == null || rowSet.isEmpty()) {
            LogUtil.warn(getClassName(), "Transaction aborted. No record found with record ID '" + primaryKey + "' from this form '" + formDefId + "'");
            return null;
        }

        final String accountMnemonic = PluginUtil.decrypt(rowSet.get(0).getProperty((String) pluginProperties.get("accountMnemonic")));
        
        return new Account(networkType.getNetwork(), accountMnemonic);
    }
    
    private List<Utxo> getUtxosFromJson(final String walletUtxosJson) {
        List<Utxo> utxos = new ArrayList<>();
        
        for (JsonElement utxosJson : new Gson().fromJson(walletUtxosJson, JsonArray.class).asList()) {
            final JsonObject utxoJsonObj = utxosJson.getAsJsonObject();
            final JsonObject utxoInput = utxoJsonObj.getAsJsonObject("input");
            final JsonObject utxoOutput = utxoJsonObj.getAsJsonObject("output");
            
            final Utxo utxo = new Utxo();
            
            utxo.setOutputIndex(utxoInput.get("outputIndex").getAsInt());
            utxo.setTxHash(utxoInput.get("txHash").getAsString());
            utxo.setAddress(utxoOutput.get("address").getAsString());
            
            List<Amount> amounts = new ArrayList<>();
            for (JsonElement utxoAmount : utxoOutput.getAsJsonArray("amount").asList()) {
                final JsonObject utxoAmountObj = utxoAmount.getAsJsonObject();
                
                final Amount amount = new Amount();
                amount.setUnit(utxoAmountObj.get("unit").getAsString());
                amount.setQuantity(utxoAmountObj.get("quantity").getAsBigInteger());
                
                amounts.add(amount);
            }
            utxo.setAmount(amounts);
            
            utxo.setDataHash(utxoOutput.has("dataHash") ? utxoOutput.get("dataHash").getAsString() : null);
            utxo.setInlineDatum(utxoOutput.has("plutusData") ? utxoOutput.get("plutusData").getAsString() : null);
            utxo.setReferenceScriptHash(utxoOutput.has("scriptRef") ? utxoOutput.get("scriptRef").getAsString() : null);
            
            utxos.add(utxo);
        }
        
        return utxos;
    }
    
    private static String getClassName() {
        return TransactionHandler.class.getName();
    }
}
