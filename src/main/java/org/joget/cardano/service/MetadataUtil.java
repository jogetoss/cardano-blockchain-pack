package org.joget.cardano.service;

import com.bloxbean.cardano.client.metadata.cbor.CBORMetadataMap;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.codec.digest.DigestUtils;
import org.joget.apps.form.model.FormRow;

public class MetadataUtil {
    
    public static final BigInteger FORMDATA_METADATUM_LABEL = BigInteger.valueOf(0); //CIP20 currently does not specify List type format
    public static final BigInteger TOKEN_INFO_METADATUM_LABEL = BigInteger.valueOf(1);
    public static final String NFT_FORMDATA_PROPERTY_LABEL = "jogetFormData";
    
    public static CBORMetadataMap generateMetadataMapFromFormData(Object[] metadataFields, FormRow row) {
        if (metadataFields == null || metadataFields.length == 0) {
            return null;
        }
        
        CBORMetadataMap metadataMap = new CBORMetadataMap();
        for (Object o : metadataFields) {
            Map mapping = (HashMap) o;
            String fieldId = mapping.get("fieldId").toString();

//            String isFile = mapping.get("isFile").toString();
//            if ("true".equalsIgnoreCase(isFile)) {
//                String appVersion = appDef.getVersion().toString();
//                String filePath = getFilePath(row.getProperty(fieldId), appDef.getAppId(), appVersion, formDefId, primaryKey);
//                metadataMap.put(fieldId, getFileHashSha256(filePath));
//            } else {
//                metadataMap.put(fieldId, row.getProperty(fieldId));
//            }
            
            metadataMap.put(fieldId, row.getProperty(fieldId));
        }
        
        return metadataMap;
    }
    
    public static Map<String, String> generateNftPropsFromFormData(Object[] propertyFields, FormRow row) {
        if (propertyFields == null || propertyFields.length == 0) {
            return null;
        }
        
        Map<String, String> nftPropsMap = new HashMap<>();
        for (Object o : propertyFields) {
            Map mapping = (HashMap) o;
            String fieldId = mapping.get("fieldId").toString();
            
            nftPropsMap.put(fieldId, row.getProperty(fieldId));
        }
        
        return nftPropsMap;
    }
    
    public static String getFilePath(String fileName, String appId, String appVersion, String formDefId, String primaryKeyValue) {
        if (fileName == null || fileName.isEmpty()) {
            return null;
        }
        
        String encodedFileName = fileName;

        try {
            encodedFileName = URLEncoder.encode(fileName, "UTF8").replaceAll("\\+", "%20");
        } catch (UnsupportedEncodingException ex) {
            // ignore
        }

        return "/web/client/app/" + appId + "/" + appVersion + "/form/download/" + formDefId + "/" + primaryKeyValue + "/" + encodedFileName + ".";
    }
    
    public static String getFileHashSha256(String filePath) throws FileNotFoundException, IOException {
        if (filePath == null || filePath.isEmpty()) {
            return null;
        }
        
        return DigestUtils.sha256Hex(new FileInputStream(filePath));
    }
}
