package org.joget.cardano.model.transaction;

import com.bloxbean.cardano.client.function.TxOutputBuilder;
import javax.servlet.http.HttpServletRequest;
import org.joget.apps.form.model.FormData;

/**
 * Interface for transaction action(s) to perform.
 */
public interface CardanoTransactionPlugin {
    TxOutputBuilder buildOutputs(FormData formData, HttpServletRequest request);   
}
