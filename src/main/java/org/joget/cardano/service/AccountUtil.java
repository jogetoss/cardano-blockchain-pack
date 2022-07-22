package org.joget.cardano.service;

import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.backend.api.AddressService;
import org.joget.apps.form.service.FormUtil;

public class AccountUtil {
    
    public static boolean isAddressExist(AddressService addressService, String accountAddress) throws ApiException {
        //If within a Form Builder, don't make useless API calls
        if (accountAddress == null || accountAddress.isBlank() || FormUtil.isFormBuilderActive()) {
            return false;
        }
        
        try {
            return addressService.getAddressInfo(accountAddress).isSuccessful();
        } catch (Exception ex) {
            //Ignore if not successful.
        }
        
        return false;
    }
    
}
