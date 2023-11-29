package org.joget.cardano.lib;

import com.bloxbean.cardano.client.api.exception.ApiException;
import org.joget.cardano.util.PluginUtil;
import com.bloxbean.cardano.client.backend.model.Asset;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.bloxbean.cardano.client.api.model.Result;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.model.Element;
import org.joget.apps.form.model.FormData;
import org.joget.apps.form.model.FormLoadElementBinder;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.commons.util.LogUtil;
import org.joget.workflow.util.WorkflowUtil;
import java.util.HashMap;
import java.util.Map;
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
            
            final String assetName = getPropertyString("assetName");
            final String policyId = getPropertyString("policyId");
            final String fingerprint = getPropertyString("fingerprint");
            final String quantity = getPropertyString("quantity");
            final String initialMintTxHash = getPropertyString("initialMintTxHash");
            final String mintOrBurnCount = getPropertyString("mintOrBurnCount");
            final String onchainMetadata = getPropertyString("onchainMetadata");

            final Result<Asset> assetInfoResult = assetService.getAsset(assetId);

            if (!assetInfoResult.isSuccessful()) {
                LogUtil.warn(getClassName(),
                        "Unable to retrieve token info. Response returned --> " + assetInfoResult.getResponse());
                return null;
            }

            final Asset assetInfo = assetInfoResult.getValue();

            FormRow row = new FormRow();

            row = addRow(row, assetName, getAssetName(assetInfo));
            row = addRow(row, policyId, getPolicyId(assetInfo));
            row = addRow(row, fingerprint, getFingerprint(assetInfo));
            row = addRow(row, quantity, getQuantity(assetInfo));
            row = addRow(row, initialMintTxHash, getInitialMintTxHash(assetInfo));
            row = addRow(row, mintOrBurnCount, getMintOrBurnCount(assetInfo));
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
        if (onChainData != null) {
            LogUtil.info(getClass().getName(), "onchainMetadata JSON: " + onChainData.toString());

            // Assuming you have an ObjectMapper instance
            ObjectMapper objectMapper = new ObjectMapper();
            // Convert JsonNode to a Map
            Map<String, Object> onChainDataMap = objectMapper.convertValue(onChainData, Map.class);
            onChainDataMap = onChainDataMap != null ? onChainDataMap : new HashMap<>();

            // Convert the Map to a JSON string
            try {
                return objectMapper.writeValueAsString(onChainDataMap);
            } catch (Exception e) {
                LogUtil.warn(getClass().getName(),
                        "Error converting on-chain metadata to JSON string: " + e.getMessage());
                return null;
            }
        } else {
            return null;
        }
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

    private String getMintOrBurnCount(Asset assetInfo) {
        String mintOrBurnCount = String.valueOf(assetInfo.getMintOrBurnCount());

        return mintOrBurnCount;
    }

    private String getInitialMintTxHash(Asset assetInfo) {
        String initialMintTxHash = assetInfo.getInitialMintTxHash();

        return initialMintTxHash;
    }

    private String getQuantity(Asset assetInfo) {
        String quantity = assetInfo.getQuantity();

        return quantity;
    }

    private String getFingerprint(Asset assetInfo) {
        String fingerprint = assetInfo.getFingerprint();

        return fingerprint;
    }

    private String getAssetName(Asset assetInfo) {
        String name = assetInfo.getAssetName();

        return name;
    }

    private String getPolicyId(Asset assetInfo) {
        String policy = assetInfo.getPolicyId();

        return policy;
    }

    private FormRow addRow(FormRow row, String field, String value) {
        if (row != null && !field.isEmpty()) {
            row.put(field, value);
        }

        return row;
    }
}
