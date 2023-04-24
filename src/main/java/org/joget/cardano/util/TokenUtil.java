package org.joget.cardano.util;

import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.backend.api.AssetService;
import com.bloxbean.cardano.client.crypto.SecretKey;
import com.bloxbean.cardano.client.util.AssetUtil;
import com.bloxbean.cardano.client.util.HexUtil;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.joget.apps.form.service.FormUtil;

public class TokenUtil {
    
    private TokenUtil() {}
    
    public static String getAssetId(String policyId, String assetName) {
        return policyId + HexUtil.encodeHexString(assetName.getBytes(StandardCharsets.UTF_8));
    }
    
    public static String getAssetFingerprintFromAssetId(String assetId) {
        final String derivedPolicyId = AssetUtil.getPolicyIdAndAssetName(assetId)._1;
        final String tokenNameInHex = AssetUtil.getPolicyIdAndAssetName(assetId)._2;
        
        return AssetUtil.calculateFingerPrint(derivedPolicyId, tokenNameInHex);
    }
    
    public static boolean isAssetIdExist(AssetService assetService, String assetId) throws ApiException {
        //If within a Form Builder, don't make useless API calls
        if (assetId == null || assetId.isBlank() || FormUtil.isFormBuilderActive()) {
            return false;
        }
        
        try {
            return assetService.getAsset(assetId).isSuccessful();
        } catch (Exception ex) {
            //Ignore if not successful.
        }
        
        return false;
    }
    
    public static boolean isPolicyIdExist(AssetService assetService, String policyId) throws ApiException {
        //If within a Form Builder, don't make useless API calls
        if (policyId == null || policyId.isBlank() || FormUtil.isFormBuilderActive()) {
            return false;
        }
        
        try {
            return assetService.getPolicyAssets(policyId, 1, 1).isSuccessful();
        } catch (Exception ex) {
            //Ignore if not successful.
        }
        
        return false;
    }
    
    //Perhaps allow fully-customizable policy name?
    public static String getFormattedPolicyName(String policyId) {        
        return "mintPolicy-joget-" + policyId.substring(0, 8);
    }
    
    // Combine all secret key(s) into string delimited by semicolon (e.g.: skey1;skey2;skey3)
    public static String getSecretKeysAsCborHexStringList(List<SecretKey> skeys) {
        if (skeys == null || skeys.isEmpty()) {
            return null;
        }
        
        return skeys.stream().map(skey -> skey.getCborHex())
				.collect(Collectors.joining(";"));
    }
    
    public static List<SecretKey> getSecretKeysStringAsList(String skeysStringList) {
        if (skeysStringList == null || skeysStringList.trim().isEmpty()) {
            return null;
        }
        
        List<SecretKey> skeys = new ArrayList<>();
        
        for (String skey : skeysStringList.split(";")) {
            skeys.add(new SecretKey(skey.trim()));
        }
        
        return skeys;
    }
}
