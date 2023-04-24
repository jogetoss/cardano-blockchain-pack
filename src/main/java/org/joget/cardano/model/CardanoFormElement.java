package org.joget.cardano.model;

import com.bloxbean.cardano.client.api.helper.FeeCalculationService;
import com.bloxbean.cardano.client.api.helper.TransactionHelperService;
import com.bloxbean.cardano.client.api.helper.UtxoTransactionBuilder;
import com.bloxbean.cardano.client.backend.api.AccountService;
import com.bloxbean.cardano.client.backend.api.AddressService;
import com.bloxbean.cardano.client.backend.api.AssetService;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.api.BlockService;
import com.bloxbean.cardano.client.backend.api.EpochService;
import com.bloxbean.cardano.client.backend.api.MetadataService;
import com.bloxbean.cardano.client.backend.api.NetworkInfoService;
import com.bloxbean.cardano.client.backend.api.TransactionService;
import com.bloxbean.cardano.client.backend.api.UtxoService;
import java.util.Map;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.model.Element;
import org.joget.apps.form.model.FormBuilderPaletteElement;
import org.joget.apps.form.model.FormData;
import org.joget.cardano.util.BackendUtil;
import org.joget.cardano.util.PluginUtil;
import org.joget.commons.util.LogUtil;
import org.joget.workflow.model.WorkflowAssignment;
import org.joget.workflow.model.service.WorkflowManager;
import org.springframework.context.ApplicationContext;

public abstract class CardanoFormElement extends Element implements FormBuilderPaletteElement {
    
    //Joget services
    protected WorkflowManager workflowManager;
    protected WorkflowAssignment wfAssignment;
    
    //Cardano backend services
    protected AssetService assetService;
    protected BlockService blockService;
    protected NetworkInfoService networkInfoService;
    protected TransactionService transactionService;
    protected UtxoService utxoService;
    protected AddressService addressService;
    protected AccountService accountService;
    protected EpochService epochService;
    protected MetadataService metadataService;
    protected TransactionHelperService transactionHelperService;
    protected UtxoTransactionBuilder utxoTransactionBuilder;
    protected FeeCalculationService feeCalculationService;
    
    //Plugin properties
    protected Map props;
    
    /**
     * HTML template for front-end UI. This method is wrapped by renderTemplate().
     * @param formData
     * @param dataModel Model containing values to be displayed in the template.
     * @return A string representing the HTML element to render
     */
    public abstract String renderElement(FormData formData, Map dataModel);
    
    @Override
    public String renderTemplate(FormData formData, Map dataModel) {
        this.props = getProperties();
        initUtils();
        
        try {
            final BackendService backendService = BackendUtil.getBackendService(props);
            
            if (backendService != null) {
                initBackendServices(backendService);
                return renderElement(formData, dataModel);
            }
        } catch (Exception ex) {
            LogUtil.error(getClassName(), ex, "Error executing form element plugin...");
        }
        
        return "";
    }
    
    private void initUtils() {
        wfAssignment = (WorkflowAssignment) props.get("workflowAssignment");
        
        ApplicationContext ac = AppUtil.getApplicationContext();
        workflowManager = (WorkflowManager) ac.getBean("workflowManager");
    }
    
    private void initBackendServices(BackendService backendService) {
        assetService = backendService.getAssetService();
        blockService = backendService.getBlockService();
        networkInfoService = backendService.getNetworkInfoService();
        transactionService = backendService.getTransactionService();
        utxoService = backendService.getUtxoService();
        addressService = backendService.getAddressService();
        accountService = backendService.getAccountService();
        epochService = backendService.getEpochService();
        metadataService = backendService.getMetadataService();
        transactionHelperService = backendService.getTransactionHelperService();
        utxoTransactionBuilder = backendService.getUtxoTransactionBuilder();
        feeCalculationService = backendService.getFeeCalculationService();
    }
    
    @Override
    public String getFormBuilderCategory() {
        return PluginUtil.FORM_ELEMENT_CATEGORY;
    }
    
    @Override
    public String getVersion() {
        return PluginUtil.getProjectVersion(this.getClass());
    }
    
    @Override
    public String getLabel() {
        return getName();
    }

    @Override
    public String getClassName() {
        return getClass().getName();
    }
}
