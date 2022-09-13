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
import org.joget.apps.form.model.Element;
import org.joget.apps.form.model.FormBuilderPaletteElement;
import org.joget.apps.form.model.FormData;
import org.joget.cardano.service.BackendUtil;
import org.joget.cardano.service.PluginUtil;
import org.joget.commons.util.LogUtil;

public abstract class CardanoFormElementAbstract extends Element implements FormBuilderPaletteElement {
    
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
     * Used to initiatize required backend services prior to executing logic. This method is wrapped by renderTemplate().
     */
    public abstract void initBackendServices(BackendService backendService);
    
    /**
     * HTML template for front-end UI. This method is wrapped by renderTemplate().
     * @param formData
     * @param dataModel Model containing values to be displayed in the template.
     * @return A string representing the HTML element to render
     */
    public abstract String renderElement(FormData formData, Map dataModel);
    
    @Override
    public String renderTemplate(FormData formData, Map dataModel) {
        String result = "";
        
        try {
            final BackendService backendService = BackendUtil.getBackendService(getProperties());
            
            if (backendService != null) {
                initBackendServices(backendService);
                result = renderElement(formData, dataModel);
            }
        } catch (Exception ex) {
            LogUtil.error(getClassName(), ex, "Error executing form element plugin...");
        }
        
        return result;
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
