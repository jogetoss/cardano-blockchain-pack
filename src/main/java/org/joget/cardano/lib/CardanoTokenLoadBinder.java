package org.joget.cardano.lib;

import com.bloxbean.cardano.client.api.exception.ApiException;
import org.joget.cardano.util.PluginUtil;
import com.bloxbean.cardano.client.backend.model.Asset;
import com.fasterxml.jackson.databind.JsonNode;
import com.bloxbean.cardano.client.api.model.Result;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.model.Element;
import org.joget.apps.form.model.FormData;
import org.joget.apps.form.model.FormLoadElementBinder;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.commons.util.LogUtil;
import org.joget.workflow.util.WorkflowUtil;
import org.joget.cardano.model.CardanoFormBinder;

public class CardanoTokenLoadBinder extends CardanoFormBinder implements FormLoadElementBinder {
    
    @Override
    public String getName() {
        return "Cardano Token Load Binder";
    }

    @Override
    public String getDescription() {
        return "Load token data from the Cardano blockchain into a form.";
    }

    @Override
    public String getPropertyOptions() {
        String backendConfigs = PluginUtil.readGenericBackendConfigs(getClassName());
        return AppUtil.readPluginResource(getClassName(), "/properties/CardanoTokenLoadBinder.json", new String[] { backendConfigs }, true, PluginUtil.MESSAGE_PATH);
    }

    @Override
    public boolean isInputDataValid() {
        final String assetId = WorkflowUtil.processVariable(getPropertyString("assetId"), "", null);

        // Prevent error thrown from empty value and invalid hash variable
        return !assetId.isEmpty() && !assetId.startsWith("#");
    }

    @Override
    public FormRowSet loadData(Element element, String primaryKey, FormData formData)
            throws RuntimeException {

        try {
            final String assetId = WorkflowUtil.processVariable(getPropertyString("assetId"), "", null);            

            final Result<Asset> assetInfoResult = assetService.getAsset(assetId);

            if (!assetInfoResult.isSuccessful()) {
                LogUtil.warn(getClassName(),
                        "Unable to retrieve token info. Response returned --> " + assetInfoResult.getResponse());
                return null;
            }

            final String assetName = getPropertyString("assetName");
            final String policyId = getPropertyString("policyId");
            final String fingerprint = getPropertyString("fingerprint");
            final String quantity = getPropertyString("quantity");
            final String initialMintTxHash = getPropertyString("initialMintTxHash");
            final String mintOrBurnCount = getPropertyString("mintOrBurnCount");
            final String onchainMetadata = getPropertyString("onchainMetadata");
            
            final Asset assetInfo = assetInfoResult.getValue();

            FormRow row = new FormRow();

            row = addRow(row, assetName, assetInfo.getAssetName());
            row = addRow(row, policyId, assetInfo.getPolicyId());
            row = addRow(row, fingerprint, assetInfo.getFingerprint());
            row = addRow(row, quantity, assetInfo.getQuantity());
            row = addRow(row, initialMintTxHash, assetInfo.getInitialMintTxHash());
            row = addRow(row, mintOrBurnCount, String.valueOf(assetInfo.getMintOrBurnCount()));
            row = addRow(row, onchainMetadata, getOnchainMetadata(assetInfo));

            FormRowSet rows = new FormRowSet();
            rows.add(row);

            return rows;
        } catch (ApiException e) {
            throw new RuntimeException(e.getClass().getName() + " : " + e.getMessage());
        }
    }

    private String getOnchainMetadata(Asset assetInfo) {
        final JsonNode onChainData = assetInfo.getOnchainMetadata();
        
        return (onChainData != null && !onChainData.isNull()) ? onChainData.toString() : "";
    }

    private FormRow addRow(FormRow row, String field, String value) {
        if (row != null && !field.isEmpty()) {
            row.put(field, value);
        }

        return row;
    }
    
    // private String getMetadata(Asset assetInfo) {
    // final JsonNode metadata = assetInfo.getMetadata();
    // if (metadata != null) {
    // LogUtil.info(getClass().getName(), "metadata JSON: " + metadata.toString());

    // // Assuming you have an ObjectMapper instance
    // ObjectMapper objectMapper = new ObjectMapper();
    // // Convert JsonNode to a Map
    // Map<String, Object> metadataMap = objectMapper.convertValue(metadata,
    // Map.class);
    // metadataMap = metadataMap != null ? metadataMap : new HashMap<>();

    // // Convert the Map to a JSON string
    // try {
    // return objectMapper.writeValueAsString(metadataMap);
    // } catch (Exception e) {
    // LogUtil.warn(getClass().getName(), "Error converting metadata to JSON string:
    // " + e.getMessage());
    // return null;
    // }
    // } else {
    // return null;
    // }
    // }
}
