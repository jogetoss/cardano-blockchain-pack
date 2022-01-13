package org.joget.marketplace;

import com.bloxbean.cardano.client.backend.api.AddressService;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.model.AddressContent;
import com.bloxbean.cardano.client.backend.model.Result;
import com.bloxbean.cardano.client.common.ADAConversionUtil;
import java.math.BigInteger;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.model.Element;
import org.joget.apps.form.model.FormBinder;
import org.joget.apps.form.model.FormData;
import org.joget.apps.form.model.FormLoadBinder;
import org.joget.apps.form.model.FormLoadElementBinder;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.commons.util.LogUtil;
import org.joget.workflow.util.WorkflowUtil;
import static com.bloxbean.cardano.client.common.CardanoConstants.LOVELACE;
import java.io.IOException;
import java.util.Properties;

public class CardanoAccountLoadBinder extends FormBinder implements FormLoadBinder, FormLoadElementBinder {

    @Override
    public String getName() {
        return "Cardano Account Load Binder";
    }

    @Override
    public String getVersion() {
        final Properties projectProp = new Properties();
        try {
            projectProp.load(this.getClass().getClassLoader().getResourceAsStream("project.properties"));
        } catch (IOException ex) {
            LogUtil.error(getClass().getName(), ex, "Unable to get project version from project properties...");
        }
        return projectProp.getProperty("version");
    }

    @Override
    public String getDescription() {
        return "Load account data from the Cardano blockchain into a form.";
    }

    @Override
    public FormRowSet load(Element element, String primaryKey, FormData formData) {
        try {
            final BackendService backendService = CardanoUtil.getBackendService(getProperties());

            final String accountAddress = WorkflowUtil.processVariable(getPropertyString("accountAddress"), "", null);

            //Prevent error thrown from empty value and invalid hash variable
            if (accountAddress.isEmpty() || accountAddress.startsWith("#")) {
                return null;
            }

            //Get account data from blockchain
            AddressService addressService = backendService.getAddressService();            
            final Result<AddressContent> addressInfoResult = addressService.getAddressInfo(accountAddress);
            if (!addressInfoResult.isSuccessful()) {
                LogUtil.warn(getClass().getName(), "Unable to retrieve address info. Response returned --> " + addressInfoResult.getResponse());
                return null;
            }
            final AddressContent addressInfo = addressInfoResult.getValue();
            
            //Get form fields from plugin properties
            String balanceField = getPropertyString("balanceField");
            String accountType = getPropertyString("accountType");

            FormRow row = new FormRow();

            row = addRow(row,balanceField, getAdaBalance(addressInfo));
            // Dandelion missing this info fyi
            row = addRow(row, accountType, getAccountType(addressInfo));
            
            FormRowSet rows = new FormRowSet();
            rows.add(row);
            
            return rows;
        } catch (Exception ex) {
            LogUtil.error(getClass().getName(), ex, "Error executing plugin...");
            return null;
        }
    }
    
    private String getAdaBalance(AddressContent addressInfo) {
        if (!addressInfo.getAmount().isEmpty()) {
            return String.valueOf(
                    ADAConversionUtil.lovelaceToAda(
                        new BigInteger(
                            addressInfo.getAmount().stream().filter(
                                accountBalance -> accountBalance.getUnit().equals(LOVELACE)
                            ).findFirst().get().getQuantity()
                        )
                    )
                );
        } else {
            return "No balance found";
        }
    }
    
    private String getAccountType(AddressContent addressInfo) {
        return (addressInfo.getType() != null) ? addressInfo.getType().name() : "";
    }
    
    private FormRow addRow(FormRow row, String field, String value) {
        if (row != null && !field.isEmpty()) {
            row.put(field, value);
        }
        return row;
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
        return AppUtil.readPluginResource(getClass().getName(), "/properties/CardanoAccountLoadBinder.json", null, true, "messages/CardanoMessages");
    }
}
