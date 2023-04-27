package org.joget.cardano.model.explorer;

import org.joget.cardano.model.NetworkType;
import org.joget.commons.util.LogUtil;

public class ExplorerFactory {
    
    public static final String DEFAULT_EXPLORER = "cardanoscan";
    
    private final NetworkType networkType;

    public ExplorerFactory(NetworkType networkType) {
        this.networkType = networkType;
    }
    
    public Explorer getExplorer(String explorerType) {
        switch (explorerType) {
            case "cardanoscan":
                return new Cardanoscan(networkType);
            case "cexplorer" :
                return new Cexplorer(networkType);
            default:
                LogUtil.warn(getClassName(), "Unknown explorer type found!");
                return null;
        }
    }
    
    private static String getClassName() {
        return ExplorerFactory.class.getName();
    }
}
