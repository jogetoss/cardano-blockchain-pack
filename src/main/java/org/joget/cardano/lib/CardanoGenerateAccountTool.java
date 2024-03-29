package org.joget.cardano.lib;

import org.joget.cardano.util.PluginUtil;
import org.joget.cardano.util.BackendUtil;
import com.bloxbean.cardano.client.account.Account;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.apps.form.service.FormUtil;
import org.joget.cardano.model.CardanoProcessTool;
import org.joget.cardano.model.NetworkType;
import org.joget.commons.util.LogUtil;
import org.joget.workflow.util.WorkflowUtil;

public class CardanoGenerateAccountTool extends CardanoProcessTool {
    
    @Override
    public String getName() {
        return "Cardano Generate Account Tool";
    }

    @Override
    public String getDescription() {
        return "Generates a new account on the Cardano blockchain.";
    }
    
    @Override
    public String getPropertyOptions() {
        String backendConfigs = PluginUtil.readGenericBackendConfigs(getClassName());
        return AppUtil.readPluginResource(getClassName(), "/properties/CardanoGenerateAccountTool.json", new String[]{backendConfigs}, true, PluginUtil.MESSAGE_PATH);
    }
    
    @Override
    public boolean isInputDataValid() {
        String formDefId = getPropertyString("formDefId");
        
        if (formDefId == null || formDefId.isEmpty()) {
            LogUtil.warn(getClassName(), "Unable to store account data to form. Encountered blank form ID.");
            return false;
        }
        
        return true;
    }
    
    @Override
    public boolean requiresBackend() {
        return false;
    }
    
    @Override
    public Object runTool() {
         
        final NetworkType networkType = BackendUtil.getNetworkType(props);
        final boolean isTest = networkType.isTestNetwork();
        
        final Account account = new Account(networkType.getNetwork());
        
        storeToForm(isTest, account);
        storeToWorkflowVariable(wfAssignment.getActivityId(), isTest, account);
        
        return null;
    }
    
    private void fundTestAccount(String faucetUrl, String testAddress) {
        //Not supported. Must be manually done at https://testnets.cardano.org/en/testnets/cardano/tools/faucet/
        throw new UnsupportedOperationException("Not supported yet.");
    }
    
    protected void storeToForm(boolean isTest, final Account account) {
        String formDefId = getPropertyString("formDefId");
        
        String accountBaseAddressField = getPropertyString("accountBaseAddress");
        String accountMnemonicField = getPropertyString("accountMnemonicField");
        String accountOwnerField = getPropertyString("accountOwnerField");
        String accountOwnerValue = WorkflowUtil.processVariable(getPropertyString("accountOwnerValue"), "", wfAssignment);
        String isTestAccountField = getPropertyString("isTestAccount");
        String accountEAddressField = getPropertyString("accountEnterpriseAddress");
        
        FormRow row = new FormRow();

        if ((FormUtil.PROPERTY_ID).equals(accountBaseAddressField)) {
            row.setId(account.baseAddress());
        } else {
            row = addRow(row, accountBaseAddressField, account.baseAddress());
        }
        
        //Mnemonic phrase MUST be secured at all times.
        row = addRow(row, accountMnemonicField, PluginUtil.encrypt(account.mnemonic()));
        row = addRow(row, accountOwnerField, accountOwnerValue);
        row = addRow(row, isTestAccountField, String.valueOf(isTest));
        row = addRow(row, accountEAddressField, account.enterpriseAddress());

        FormRowSet rowSet = new FormRowSet();
        rowSet.add(row);

        if (!rowSet.isEmpty()) {
            FormRowSet storedData = appService.storeFormData(appDef.getId(), appDef.getVersion().toString(), formDefId, rowSet, null);
            if (storedData == null) {
                LogUtil.warn(getClassName(), "Unable to store account data to form. Encountered invalid form ID of '" + formDefId + "'.");
            }
        }
    }
    
    private FormRow addRow(FormRow row, String field, String value) {
        if (row != null && !field.isEmpty()) {
            row.put(field, value);
        }
        
        return row;
    }
    
    protected void storeToWorkflowVariable(String activityId, boolean isTest, final Account account) {
        String isTestAccountVar = getPropertyString("wfIsTestAccount");

        storeValuetoActivityVar(activityId, isTestAccountVar, String.valueOf(isTest));
    }
    
    private void storeValuetoActivityVar(String activityId, String variable, String value) {
        if (activityId == null || activityId.isEmpty() || variable.isEmpty() || value == null) {
            return;
        }
        
        workflowManager.activityVariable(activityId, variable, value);
    }
}
