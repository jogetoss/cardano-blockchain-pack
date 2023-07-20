package org.joget.cardano.model.transaction;

import com.bloxbean.cardano.client.function.TxBuilder;
import com.bloxbean.cardano.client.function.TxSigner;
import org.joget.cardano.util.PluginUtil;
import org.joget.plugin.base.ExtDefaultPlugin;
import org.joget.plugin.property.model.PropertyEditable;

/**
 * Base class for implementations to add action(s) to transaction.
 */
public abstract class CardanoTransactionAction extends ExtDefaultPlugin implements CardanoTransactionPlugin, PropertyEditable {    
    /**
     * Option to modify the txBuilder before it is built & sent for wallet signing
     * @param txBuilder 
     * @return TxBuilder
     * 
     */
    public TxBuilder modifyTxBuilder(TxBuilder txBuilder) {
        return null;
    }
    
    public int numberOfAdditionalSigners() {
        return 0;
    }
    
    public TxSigner addSigners() {
        return null;
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
