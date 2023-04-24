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
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.service.AppService;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.datalist.service.DataListService;
import org.joget.cardano.util.BackendUtil;
import org.joget.cardano.util.PluginUtil;
import org.joget.commons.util.LogUtil;
import org.joget.plugin.base.DefaultApplicationPlugin;
import org.joget.workflow.model.WorkflowAssignment;
import org.joget.workflow.model.service.WorkflowManager;
import org.springframework.context.ApplicationContext;

public abstract class CardanoProcessTool extends DefaultApplicationPlugin {
    
    //Joget services
    protected AppDefinition appDef;
    protected AppService appService;
    protected DataListService dataListService;
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
     * Used to validate necessary input values prior to executing API calls. This method is wrapped by execute().
     * 
     * @return A boolean value to continue or skip plugin execution. Default value is true.
     */
    public boolean isInputDataValid() {
        return true;
    }
    
    /**
     * Used to indicate if backend service is required. This method is wrapped by execute().
     * 
     * @return A boolean value to call backend service or not. Default value is true.
     */
    public boolean requiresBackend() {
        return true;
    }
    
    /**
     * To execute logic in a process tool. This method is wrapped by execute().
     * 
     * @return is not used for now
     */
    public abstract Object runTool();
    
    @Override
    public Object execute(Map properties) {
        this.props = properties;
        initUtils();
        
        if (!isInputDataValid()) {
            LogUtil.debug(getClassName(), "Invalid input(s) detected. Aborting plugin execution.");
            return null;
        }
        
        Object result = null;
        
        try {
            if (requiresBackend()) {
                final BackendService backendService = BackendUtil.getBackendService(props);
                initBackendServices(backendService);
            }

            result = runTool();
            
        } catch (Exception ex) {
            LogUtil.error(getClassName(), ex, "Error executing process tool plugin...");
        }
        
        return result;
    }
    
    private void initUtils() {
        appDef = (AppDefinition) props.get("appDef");
        wfAssignment = (WorkflowAssignment) props.get("workflowAssignment");
        
        ApplicationContext ac = AppUtil.getApplicationContext();
        appService = (AppService) ac.getBean("appService");
        dataListService = (DataListService) ac.getBean("dataListService");
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
