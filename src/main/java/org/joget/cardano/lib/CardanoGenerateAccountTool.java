package org.joget.cardano.lib;

import org.joget.cardano.service.PluginUtil;
import org.joget.cardano.service.BackendUtil;
import java.util.Map;
import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.common.model.Network;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.service.AppService;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.apps.form.service.FormUtil;
import org.joget.cardano.model.CardanoProcessTool;
import org.joget.commons.util.LogUtil;
import org.joget.workflow.model.WorkflowAssignment;
import org.joget.workflow.model.service.WorkflowManager;
import org.joget.workflow.util.WorkflowUtil;
import org.springframework.context.ApplicationContext;

public class CardanoGenerateAccountTool extends CardanoProcessTool {

    AppService appService;
    AppDefinition appDef;
    WorkflowManager workflowManager;
    
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
        return AppUtil.readPluginResource(getClassName(), "/properties/CardanoGenerateAccountTool.json", null, true, PluginUtil.MESSAGE_PATH);
    }
    
    protected void initUtils(Map props) {
        ApplicationContext ac = AppUtil.getApplicationContext();
        
        appService = (AppService) ac.getBean("appService");
        appDef = (AppDefinition) props.get("appDef");
        workflowManager = (WorkflowManager) ac.getBean("workflowManager");
    }
    
    @Override
    public boolean isInputDataValid(Map props, WorkflowAssignment wfAssignment) {
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
    public void initBackendServices(BackendService backendService) { /* Do nothing */ }
    
    @Override
    public Object runTool(Map props, WorkflowAssignment wfAssignment) {
        initUtils(props);
        
        boolean isTest = BackendUtil.isTestnet(props);
        
        Network network = BackendUtil.getNetwork(props);
        
        final Account account = new Account(network);
        
        storeToForm(isTest, account, wfAssignment);
        storeToWorkflowVariable(wfAssignment.getActivityId(), isTest, account);
        
        return null;
    }
    
    private void fundTestAccount(String faucetUrl, String testAddress) {
        //Not supported. Must be manually done at https://testnets.cardano.org/en/testnets/cardano/tools/faucet/
        throw new UnsupportedOperationException("Not supported yet.");
    }
    
    protected void storeToForm(boolean isTest, final Account account, WorkflowAssignment wfAssignment) {
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
