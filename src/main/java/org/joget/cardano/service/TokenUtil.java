package org.joget.cardano.service;

import com.bloxbean.cardano.client.crypto.SecretKey;
import com.bloxbean.cardano.client.util.HexUtil;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class TokenUtil {
    public static String getAssetId(String policyId, String assetName) {
        return policyId + HexUtil.encodeHexString(assetName.getBytes(StandardCharsets.UTF_8));
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
