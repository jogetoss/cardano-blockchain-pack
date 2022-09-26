package org.joget.cardano.model;

import com.bloxbean.cardano.client.api.exception.ApiException;
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
import com.bloxbean.cardano.client.exception.AddressExcepion;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.Map;
import org.joget.cardano.service.BackendUtil;
import org.joget.cardano.service.PluginUtil;
import org.joget.commons.util.LogUtil;
import org.joget.plugin.base.DefaultApplicationPlugin;
import org.joget.workflow.model.WorkflowAssignment;

public abstract class CardanoProcessToolAbstract extends DefaultApplicationPlugin {
    
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
    
    /**
     * Used to validate necessary input values prior to executing API calls. This method is wrapped by execute().
     * 
     * @return A boolean value to continue or skip plugin execution. Default value is true.
     */
    public boolean isInputDataValid(Map props, WorkflowAssignment wfAssignment) {
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
     * Used to initiatize required backend services prior to executing logic. This method is wrapped by execute().
     * 
     * @param backendService The backend service to execute queries and actions with the blockchain
     */
    public abstract void initBackendServices(BackendService backendService);
    
    /**
     * To execute logic in a process tool. This method is wrapped by execute().
     * 
     * A org.joget.workflow.model.WorkflowAssignment object is passed as "workflowAssignment" property when it is available.
     * 
     * @param props
     * @param wfAssignment
     * 
     * @return is not used for now
     */
    public abstract Object runTool(Map props, WorkflowAssignment wfAssignment)
            throws ApiException, CborSerializationException, CborDeserializationException, JsonProcessingException, AddressExcepion;
    
    @Override
    public Object execute(Map props) {
        WorkflowAssignment wfAssignment = (WorkflowAssignment) props.get("workflowAssignment");
        
        if (!isInputDataValid(props, wfAssignment)) {
            LogUtil.debug(getClassName(), "Invalid input(s) detected. Aborting plugin execution.");
            return null;
        }
        
        Object result = null;
        
        try {
            if (requiresBackend()) {
                final BackendService backendService = BackendUtil.getBackendService(props);
                initBackendServices(backendService);
            }

            result = runTool(props, wfAssignment);
            
        } catch (Exception ex) {
            LogUtil.error(getClassName(), ex, "Error executing process tool plugin...");
        }
        
        return result;
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
