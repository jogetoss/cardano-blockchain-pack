package org.joget.cardano.lib;

import com.bloxbean.cardano.client.api.exception.ApiException;
import org.joget.cardano.service.PluginUtil;
import com.bloxbean.cardano.client.backend.model.AddressContent;
import com.bloxbean.cardano.client.common.ADAConversionUtil;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.model.TxContentOutputAmount;
import java.math.BigInteger;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.model.Element;
import org.joget.apps.form.model.FormData;
import org.joget.apps.form.model.FormLoadElementBinder;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.commons.util.LogUtil;
import org.joget.workflow.util.WorkflowUtil;
import static com.bloxbean.cardano.client.common.CardanoConstants.LOVELACE;
import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.joget.cardano.model.CardanoFormBinder;

public class CardanoAccountLoadBinder extends CardanoFormBinder implements FormLoadElementBinder {
    
    @Override
    public String getName() {
        return "Cardano Account Load Binder";
    }

    @Override
    public String getDescription() {
        return "Load account data from the Cardano blockchain into a form.";
    }
    
    @Override
    public String getPropertyOptions() {
        String backendConfigs = PluginUtil.readGenericBackendConfigs(getClassName());
        return AppUtil.readPluginResource(getClassName(), "/properties/CardanoAccountLoadBinder.json", new String[]{backendConfigs}, true, PluginUtil.MESSAGE_PATH);
    }
    
    @Override
    public boolean isInputDataValid() {
        final String accountAddress = WorkflowUtil.processVariable(getPropertyString("accountAddress"), "", null);

        //Prevent error thrown from empty value and invalid hash variable
        return !accountAddress.isEmpty() && !accountAddress.startsWith("#");
    }
    
    @Override
    public void initBackendServices(BackendService backendService) {
        addressService = backendService.getAddressService();
    }
    
    @Override
    public FormRowSet loadData(Element element, String primaryKey, FormData formData)
            throws RuntimeException {
        
        try {
            final String accountAddress = WorkflowUtil.processVariable(getPropertyString("accountAddress"), "", null);

            final Result<AddressContent> addressInfoResult = addressService.getAddressInfo(accountAddress);
            if (!addressInfoResult.isSuccessful()) {
                LogUtil.warn(getClassName(), "Unable to retrieve address info. Response returned --> " + addressInfoResult.getResponse());
                return null;
            }

            final AddressContent addressInfo = addressInfoResult.getValue();

            final String balanceField = getPropertyString("adaBalanceField");
            final String accountType = getPropertyString("accountType");
            final boolean displayAllTokenBalances = "showAll".equalsIgnoreCase(getPropertyString("tokenBalanceDisplayMode"));

            FormRow row = new FormRow();

            row = addRow(row, balanceField, getAdaBalance(addressInfo));
            
            if (displayAllTokenBalances) {
                final String assetBalancesField = getPropertyString("assetBalancesField");
                final String hideAssets = getPropertyString("hideAssets");
                
                List<TxContentOutputAmount> balances = addressInfo.getAmount();
                List<String> tokensToHide = new ArrayList<String>(Arrays.asList(hideAssets.split("\\R|;")));
                tokensToHide.add(LOVELACE);
                List<TxContentOutputAmount> tokensToDelete = new ArrayList<TxContentOutputAmount>();
                for (TxContentOutputAmount balance : balances) {
                    for (String t : tokensToHide) {
                        if (balance.getUnit().equals(t)) {
                            tokensToDelete.add(balance);
                        }
                    }
                }
                balances.removeAll(tokensToDelete);
                
                String result = new Gson().toJson(balances);
                if (balances.isEmpty()) {
                    result = "No token balances found";
                }
                
                row = addRow(row, assetBalancesField, result);
            } else {
                final Object[] assetBalances = (Object[]) getProperty("assetBalances");
                
                for (Object o : assetBalances) {
                    Map mapping = (HashMap) o;
                    String assetId = mapping.get("assetId").toString();
                    String formFieldId = mapping.get("formFieldId").toString();

                    row = addRow(row, formFieldId, getAssetBalance(addressInfo, assetId));
                }
            }
            
            row = addRow(row, accountType, getAccountType(addressInfo));

            FormRowSet rows = new FormRowSet();
            rows.add(row);

            return rows;
        } catch (ApiException e) {
            throw new RuntimeException(e.getClass().getName() + " : " + e.getMessage());
        }
    }
    
    protected String getAdaBalance(AddressContent addressInfo) {
        if (addressInfo.getAmount().isEmpty()) {
            return "No balance found";
        }
        
        return String.valueOf(ADAConversionUtil.lovelaceToAda(new BigInteger(getAssetBalance(addressInfo, LOVELACE))));
    }
    
    private String getAssetBalance(AddressContent addressInfo, String assetId) {
        if (addressInfo.getAmount().isEmpty()) {
            return "No balance found";
        }
        
        return addressInfo.getAmount().stream().filter(
                        accountBalance -> accountBalance.getUnit().equals(assetId)
                    ).findFirst().map(TxContentOutputAmount::getQuantity).orElse("No balance found");
    }
    
    protected String getAccountType(AddressContent addressInfo) {
        return (addressInfo.getType() != null) ? addressInfo.getType().name() : "";
    }
    
    private FormRow addRow(FormRow row, String field, String value) {
        if (row != null && !field.isEmpty()) {
            row.put(field, value);
        }
        
        return row;
    }
}
