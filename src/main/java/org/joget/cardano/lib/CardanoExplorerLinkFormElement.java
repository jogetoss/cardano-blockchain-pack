package org.joget.cardano.lib;

import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.backend.api.BackendService;
import java.util.Map;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.model.Element;
import org.joget.apps.form.model.Form;
import org.joget.apps.form.model.FormContainer;
import org.joget.apps.form.model.FormData;
import org.joget.apps.form.service.FormUtil;
import org.joget.cardano.model.CardanoFormElementAbstract;
import org.joget.cardano.service.AccountUtil;
import org.joget.cardano.service.BackendUtil;
import org.joget.cardano.service.ExplorerLinkUtil;
import org.joget.cardano.service.PluginUtil;
import org.joget.cardano.service.TokenUtil;
import org.joget.cardano.service.TransactionUtil;
import org.joget.commons.util.LogUtil;
import org.joget.workflow.model.WorkflowAssignment;
import org.joget.workflow.model.service.WorkflowManager;
import org.joget.workflow.util.WorkflowUtil;
import org.springframework.context.ApplicationContext;

public class CardanoExplorerLinkFormElement extends CardanoFormElementAbstract implements FormContainer {
    
    private static final String TX_ID_TYPE = "transactionId";
    private static final String ADDRESS_TYPE = "accountAddress";
    private static final String POLICY_TYPE = "tokenPolicy";
    private static final String ASSET_TYPE = "assetId";
    
    WorkflowAssignment wfAssignment;
    WorkflowManager workflowManager;

    @Override
    public String getName() {
        return "Cardano Explorer Link";
    }

    @Override
    public String getDescription() {
        return "A clickable button or link in a form to navigate to several popular Cardano explorers to verify information.";
    }
    
    @Override
    public String getPropertyOptions() {
        String backendConfigs = PluginUtil.readGenericBackendConfigs(getClassName());
        return AppUtil.readPluginResource(getClassName(), "/properties/CardanoExplorerLinkFormElement.json", new String[]{backendConfigs}, true, PluginUtil.MESSAGE_PATH);
    }
    
    protected void initUtils(Map props) {
        ApplicationContext ac = AppUtil.getApplicationContext();
        
        wfAssignment = (WorkflowAssignment) props.get("workflowAssignment");
        workflowManager = (WorkflowManager) ac.getBean("workflowManager");
    }
    
    @Override
    public void initBackendServices(BackendService backendService) {        
        transactionService = backendService.getTransactionService();
        assetService = backendService.getAssetService();
        addressService = backendService.getAddressService();
    }
    
    @Override
    public String renderElement(FormData formData, Map dataModel) {
        initUtils(getProperties());
        
        boolean isTest = BackendUtil.isTestnet(getProperties());
        
        String explorerType = getPropertyString("explorerType");
        String valueType = getPropertyString("valueType");
        String getValueMode = getPropertyString("getValueMode");
        
        String retrievedValue;
        
        if (FormUtil.isFormBuilderActive()) { // Don't need to unnecessarily retrieve value when in Form Builder
            retrievedValue = "";
        } else {
            switch (getValueMode) {
                case "fieldId" :
                    String fieldId = getPropertyString("getFieldId");

                    Form form = FormUtil.findRootForm(this);
                    Element fieldElement = FormUtil.findElement(fieldId, form, formData);

                    retrievedValue = FormUtil.getElementPropertyValue(fieldElement, formData);
                    break;
                case "hashVariable" :
                    String textHashVariable = getPropertyString("textHashVariable");
                    retrievedValue = WorkflowUtil.processVariable(textHashVariable, "", wfAssignment);
                    break;
                default:
                    String workflowVariable = getPropertyString("workflowVariable");
                    retrievedValue = workflowManager.getProcessVariable(wfAssignment.getProcessId(), workflowVariable);
                    break;
            }
        }
        
        dataModel.put("element", this);
        dataModel.put("isValidValue", checkValueExist(valueType, retrievedValue));
        dataModel.put("explorerUrl", getExplorerUrl(valueType, isTest, retrievedValue, explorerType));
        
        return FormUtil.generateElementHtml(this, formData, "CardanoExplorerLinkFormElement.ftl", dataModel);
    }
    
    public boolean checkValueExist(String valueType, String retrievedValue) {
        if (retrievedValue == null || retrievedValue.isBlank() || FormUtil.isFormBuilderActive()) {
            return false;
        }
        
        try {
            switch (valueType) {
                case ADDRESS_TYPE :
                    return AccountUtil.isAddressExist(addressService, retrievedValue);
                case POLICY_TYPE :
                    return TokenUtil.isPolicyIdExist(assetService, retrievedValue);
                case ASSET_TYPE :
                    return TokenUtil.isAssetIdExist(assetService, retrievedValue);
                case TX_ID_TYPE:
                default:
                    return TransactionUtil.isTransactionIdExist(transactionService, retrievedValue);
            }
        } catch (ApiException ex) {
            /* 
                Since retrieved value can be pretty much anything, simply ignore any errors thrown from API
                See if can differentiate between legit API call problem VS simply no valid result returned
            */
            //LogUtil.error(getClassName(), ex, "Error retrieving on-chain data from backend.");
        } catch (Exception ex) {
            LogUtil.error(getClassName(), ex, "Error retrieving on-chain data from backend.");
        }
        
        return false;
    }
    
    public String getExplorerUrl(String valueType, boolean isTest, String retrievedValue, String explorerType) {
        if (FormUtil.isFormBuilderActive()) {
            return "";
        }
        
        switch (valueType) {
            case ADDRESS_TYPE :
                return ExplorerLinkUtil.getAddressExplorerUrl(isTest, retrievedValue, explorerType);
            case POLICY_TYPE :
                return ExplorerLinkUtil.getPolicyExplorerUrl(isTest, retrievedValue, explorerType);
            case ASSET_TYPE :
                return ExplorerLinkUtil.getTokenExplorerUrl(isTest, retrievedValue, explorerType);
            case TX_ID_TYPE:
            default:
                return ExplorerLinkUtil.getTransactionExplorerUrl(isTest, retrievedValue, explorerType);
        }
    }

    @Override
    public int getFormBuilderPosition() {
        return 1;
    }

    @Override
    public String getFormBuilderIcon() {
        return "<i class=\"fas fa-external-link-alt\"></i>";
    }

    @Override
    public String getFormBuilderTemplate() {
        return "<span class='form-floating-label'>Cardano Explorer Link</span>";
    }
}
