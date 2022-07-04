package org.joget.cardano.lib;

import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.api.TransactionService;
import java.util.Map;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.model.Element;
import org.joget.apps.form.model.Form;
import org.joget.apps.form.model.FormBuilderPaletteElement;
import org.joget.apps.form.model.FormContainer;
import org.joget.apps.form.model.FormData;
import org.joget.apps.form.service.FormUtil;
import org.joget.cardano.service.BackendUtil;
import org.joget.cardano.service.PluginUtil;
import org.joget.cardano.service.TransactionUtil;
import org.joget.commons.util.LogUtil;
import org.joget.workflow.model.WorkflowAssignment;
import org.joget.workflow.model.service.WorkflowManager;
import org.joget.workflow.util.WorkflowUtil;
import org.springframework.context.ApplicationContext;

public class CardanoExplorerLinkFormElement extends Element implements FormBuilderPaletteElement, FormContainer {
    
    BackendService backendService;
    TransactionService transactionService;
    
    WorkflowAssignment wfAssignment;
    WorkflowManager workflowManager;
    
    protected void initBackend() {
        backendService = BackendUtil.getBackendService(getProperties());
        
        transactionService = backendService.getTransactionService();
    }
    
    protected void initUtils(Map props) {
        ApplicationContext ac = AppUtil.getApplicationContext();
        
        wfAssignment = (WorkflowAssignment) props.get("workflowAssignment");
        workflowManager = (WorkflowManager) ac.getBean("workflowManager");
    }
    
    @Override
    public String getName() {
        return "Cardano Explorer Link";
    }

    @Override
    public String getVersion() {
        return PluginUtil.getProjectVersion(this.getClass());
    }

    @Override
    public String getDescription() {
        return "A simple clickable link form element to view transaction information on several popular Cardano explorers.";
    }
    
    @Override
    public String renderTemplate(FormData formData, Map dataModel) {
        initUtils(getProperties());
        
        boolean isTest = BackendUtil.isTestnet(getProperties());
        
        String explorerType = getPropertyString("explorerType");
        String getValueMode = getPropertyString("getValueMode");
        
        String transactionId;
        
        switch (getValueMode) {
            case "fieldId" :
                String fieldId = getPropertyString("getFieldId");
                
                Form form = FormUtil.findRootForm(this);
                Element fieldElement = FormUtil.findElement(fieldId, form, formData);
                
                transactionId = FormUtil.getElementPropertyValue(fieldElement, formData);
                break;
            case "hashVariable" :
                String textHashVariable = getPropertyString("textHashVariable");
                transactionId = WorkflowUtil.processVariable(textHashVariable, "", wfAssignment);
                break;
            default:
                String workflowVariable = getPropertyString("workflowVariable");
                transactionId = workflowManager.getProcessVariable(wfAssignment.getProcessId(), workflowVariable);
                break;
        }
        
        initBackend();
        
        boolean isValidTxId = false;
        
        try {
            //Check if transaction ID is valid
            isValidTxId = TransactionUtil.validateTransactionId(transactionService, transactionId);
        } catch (ApiException ex) {
            /* 
                Since tx ID can be pretty much anything, simply ignore any errors thrown from API
                See if can differentiate between legit API call problem VS simply no valid result returned
            */
//            LogUtil.error(getClass().getName(), ex, "Error retrieving transaction information from backend.");
        } catch (Exception ex) {
            LogUtil.error(getClass().getName(), ex, "Error retrieving transaction information from backend.");
        }
        
        dataModel.put("element", this);
        dataModel.put("isValidTxId", isValidTxId);
        dataModel.put("txExplorerUrl", TransactionUtil.getTransactionExplorerUrl(isTest, transactionId, explorerType));
        return FormUtil.generateElementHtml(this, formData, "CardanoExplorerLinkFormElement.ftl", dataModel);
    }
    
    @Override
    public String getFormBuilderCategory() {
        return PluginUtil.FORM_ELEMENT_CATEGORY;
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
        String backendConfigs = PluginUtil.readGenericBackendConfigs(getClass().getName());
        return AppUtil.readPluginResource(getClass().getName(), "/properties/CardanoExplorerLinkFormElement.json", new String[]{backendConfigs}, true, PluginUtil.MESSAGE_PATH);
    }
}
