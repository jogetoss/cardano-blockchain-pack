package org.joget.cardano.lib;

import com.bloxbean.cardano.client.api.exception.ApiException;
import org.joget.cardano.service.PluginUtil;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.backend.model.metadata.MetadataJSONContent;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.model.Element;
import org.joget.apps.form.model.FormData;
import org.joget.apps.form.model.FormLoadElementBinder;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.cardano.model.CardanoFormBinder;
import org.joget.commons.util.LogUtil;
import org.joget.workflow.util.WorkflowUtil;

public class CardanoMetadataLoadBinder extends CardanoFormBinder implements FormLoadElementBinder {
    
    @Override
    public String getName() {
        return "Cardano Metadata Load Binder";
    }

    @Override
    public String getDescription() {
        return "Load a transaction's metadata from the Cardano blockchain into a form.";
    }
    
    @Override
    public String getPropertyOptions() {
        String backendConfigs = PluginUtil.readGenericBackendConfigs(getClass().getName());
        return AppUtil.readPluginResource(getClass().getName(), "/properties/CardanoMetadataLoadBinder.json", new String[]{backendConfigs}, true, PluginUtil.MESSAGE_PATH);
    }
    
    @Override
    public boolean isInputDataValid() {
        final String transactionId = WorkflowUtil.processVariable(getPropertyString("transactionId"), "", null);

        //Prevent error thrown from empty value and invalid hash variable
        return !transactionId.isEmpty() && !transactionId.startsWith("#");
    }
    
    @Override
    public void initBackendServices(BackendService backendService) {
        metadataService = backendService.getMetadataService();
    }
    
    @Override
    public FormRowSet loadData(Element element, String primaryKey, FormData formData)
            throws RuntimeException {
        
        try {
            final String transactionId = WorkflowUtil.processVariable(getPropertyString("transactionId"), "", null);

            final Result<List<MetadataJSONContent>> metadataResult = metadataService.getJSONMetadataByTxnHash(transactionId);
            if (!metadataResult.isSuccessful()) {
                LogUtil.warn(getClass().getName(), "Unable to retrieve transaction metadata. Response returned --> " + metadataResult.getResponse());
                return null;
            }

            //Cardano Send Transaction Tool only uses CBORMetadataMap on key "0"
            final JsonNode metadata = metadataResult.getValue().get(0).getJsonMetadata();

            FormRow row = new FormRow();

            //Get field mappings here
            Object[] metadataFields = (Object[]) getProperty("metadata");
            for (Object o : metadataFields) {
                Map mapping = (HashMap) o;
                String metadataField = mapping.get("metadataField").toString();
                String formFieldId = mapping.get("formFieldId").toString();

                row = addRow(row, formFieldId, metadata.get(metadataField).asText());
            }

            FormRowSet rows = new FormRowSet();
            rows.add(row);

            return rows;
        } catch (ApiException e) {
            throw new RuntimeException(e.getClass().getName() + " : " + e.getMessage());
        }
    }
    
    private FormRow addRow(FormRow row, String field, String value) {
        if (row != null && !field.isEmpty()) {
            row.put(field, value);
        }
        
        return row;
    }
}
