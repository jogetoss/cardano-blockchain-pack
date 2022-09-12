package com.bloxbean.cardano.client.common.model;

/* Temporary overridde library's Networks class */
public class Networks {
    public static Network mainnet() {
        Network mainnet = new Network(0b0001, 764824073);
        return mainnet;
    }

    //Override to preview testnet. Can't do preprod testnet yet to due to method signature.
    public static Network testnet() {
        Network testnet = new Network(0b0000, 2);
        return testnet;
    }
    
    public static Network preprodTestnet() {
        Network testnet = new Network(0b0000, 1);
        return testnet;
    }
}
