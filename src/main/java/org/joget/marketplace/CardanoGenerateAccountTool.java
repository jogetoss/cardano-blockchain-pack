package org.joget.marketplace;

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

    @Override
    public String getName() {
        return "Cardano Generate Account Tool";
    }

    @Override
    public String getVersion() {
        return "7.0.0";
    }

    @Override
    public String getDescription() {
        return "Generates a new account on the Cardano blockchain.";
    }

    @Override
    public Object execute(Map props) {
        boolean isTest = false;
        String networkType = getPropertyString("networkType");
        Network.ByReference network = null;
        
        if ("testnet".equals(networkType)) {
            isTest = true;
        }
        
        WorkflowAssignment wfAssignment = (WorkflowAssignment) props.get("workflowAssignment");
        
        network = CardanoUtil.getNetwork(isTest);
        
        final Account account = new Account(network);
        
        storeToForm(wfAssignment, props, isTest, account);
        storeToWorkflowVariable(wfAssignment, props, isTest, account);
        
        return null;
    }
    
    private void fundTestAccount(String faucetUrl, String testBaseAddress) {
        //Not supported. Must be manually done at https://testnets.cardano.org/en/testnets/cardano/tools/faucet/
        throw new UnsupportedOperationException("Not supported yet.");
    }
    
    protected void storeToForm(WorkflowAssignment wfAssignment, Map properties, boolean isTest, final Account account) {
        String formDefId = getPropertyString("formDefId");
        
        if (formDefId != null && formDefId.trim().length() > 0) {
            ApplicationContext ac = AppUtil.getApplicationContext();
            AppService appService = (AppService) ac.getBean("appService");
            AppDefinition appDef = (AppDefinition) properties.get("appDef");

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
            /* 
                See CardanoUtil encrypt & decrypt method to implement your preferred algo. Current way of encrypt/decrypt is just for POC.
            */
            row = addRow(row, accountMnemonicField, CardanoUtil.encrypt(account.mnemonic()));
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
    
    protected void storeToWorkflowVariable(WorkflowAssignment wfAssignment, Map properties, boolean isTest, final Account account) {
        String isTestAccountVar = getPropertyString("wfIsTestAccount");
        
        ApplicationContext ac = AppUtil.getApplicationContext();
        WorkflowManager workflowManager = (WorkflowManager) ac.getBean("workflowManager");

        storeValuetoActivityVar(workflowManager, wfAssignment.getActivityId(), isTestAccountVar, String.valueOf(isTest));
    }
    
    private void storeValuetoActivityVar(WorkflowManager workflowManager, String activityId, String variable, String value) {
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
