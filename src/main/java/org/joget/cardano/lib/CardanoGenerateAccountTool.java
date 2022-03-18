package org.joget.cardano.lib;

import org.joget.cardano.service.PluginUtil;
import org.joget.cardano.service.BackendUtil;
import java.util.Map;
import org.joget.plugin.base.DefaultApplicationPlugin;
import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.common.model.Network;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.service.AppService;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.workflow.model.WorkflowAssignment;
import org.joget.workflow.model.service.WorkflowManager;
import org.joget.workflow.util.WorkflowUtil;
import org.springframework.context.ApplicationContext;

public class CardanoGenerateAccountTool extends DefaultApplicationPlugin {

    AppService appService;
    WorkflowAssignment wfAssignment;
    AppDefinition appDef;
    WorkflowManager workflowManager;
    
    protected void initUtils(Map props) {
        ApplicationContext ac = AppUtil.getApplicationContext();
        
        appService = (AppService) ac.getBean("appService");
        wfAssignment = (WorkflowAssignment) props.get("workflowAssignment");
        appDef = (AppDefinition) props.get("appDef");
        workflowManager = (WorkflowManager) ac.getBean("workflowManager");
    }
    
    @Override
    public String getName() {
        return "Cardano Generate Account Tool";
    }

    @Override
    public String getVersion() {
        return PluginUtil.getProjectVersion(this.getClass());
    }

    @Override
    public String getDescription() {
        return "Generates a new account on the Cardano blockchain.";
    }

    @Override
    public Object execute(Map props) {
        initUtils(props);
        
        String networkType = getPropertyString("networkType");
        boolean isTest = "testnet".equalsIgnoreCase(networkType);
        
        Network.ByReference network = BackendUtil.getNetwork(isTest);
        
        final Account account = new Account(network);
        
        storeToForm(props, isTest, account);
        storeToWorkflowVariable(wfAssignment.getActivityId(), props, isTest, account);
        
        return null;
    }
    
    private void fundTestAccount(String faucetUrl, String testBaseAddress) {
        //Not supported. Must be manually done at https://testnets.cardano.org/en/testnets/cardano/tools/faucet/
        throw new UnsupportedOperationException("Not supported yet.");
    }
    
    protected void storeToForm(Map properties, boolean isTest, final Account account) {
        String formDefId = getPropertyString("formDefId");
        
        if (formDefId != null && formDefId.trim().length() > 0) {

            String accountMnemonicField = getPropertyString("accountMnemonicField");
            String accountOwnerField = getPropertyString("accountOwnerField");
            String accountOwnerValue = WorkflowUtil.processVariable(getPropertyString("accountOwnerValue"), "", wfAssignment);
            String isTestAccountField = getPropertyString("isTestAccount");
            String accountEAddressField = getPropertyString("accountEnterpriseAddress");
            
            FormRowSet rowSet = new FormRowSet();
            
            FormRow row = new FormRow();
            
            //Account base address set as Record ID
            row.setId(account.baseAddress());
            
            //Mnemonic phrase MUST be secured at all times.
            row = addRow(row, accountMnemonicField, PluginUtil.encrypt(account.mnemonic()));
            row = addRow(row, accountOwnerField, accountOwnerValue);
            row = addRow(row, isTestAccountField, String.valueOf(isTest));
            row = addRow(row, accountEAddressField, account.enterpriseAddress());

            rowSet.add(row);

            if (rowSet.size() > 0) {
                appService.storeFormData(appDef.getId(), appDef.getVersion().toString(), formDefId, rowSet, null);
            }
        }
    }
    
    private FormRow addRow(FormRow row, String field, String value) {
        if (row != null && !field.isEmpty()) {
            row.put(field, value);
        }
        return row;
    }
    
    protected void storeToWorkflowVariable(String activityId, Map properties, boolean isTest, final Account account) {
        String isTestAccountVar = getPropertyString("wfIsTestAccount");

        storeValuetoActivityVar(activityId, isTestAccountVar, String.valueOf(isTest));
    }
    
    private void storeValuetoActivityVar(String activityId, String variable, String value) {
        if (!variable.isEmpty()) {
            workflowManager.activityVariable(activityId, variable, value);
        }
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
        return AppUtil.readPluginResource(getClass().getName(), "/properties/CardanoGenerateAccountTool.json", null, true, "messages/CardanoMessages");
    }
}
