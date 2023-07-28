class WalletWebService {
    private validateFormDataUrl: string = "";
    private buildTxCborUrl: string = "";
    private internalCheckFeeUrl: string = "";
    private internalSignSubmitTxUrl: string = "";
    private renewEndpointsUrl: string = "";

    private isInternalWalletHandle: boolean = false;
    private txCbor: string = "";
    private calculatedTxHash: string = "";

    constructor(
        private formObj: HTMLFormElement,
        private initServiceUrl: string,
        private pluginPropertiesJson: string
    ) {}

    async _init(): Promise<void> {
        let endpointsMap = await this.fetchData(this.initServiceUrl);

        this.validateFormDataUrl = endpointsMap.get("validateFormData")!;
        this.buildTxCborUrl = endpointsMap.get("buildTxCbor")!;
        this.internalCheckFeeUrl = endpointsMap.get("internalCheckFee")!;
        this.internalSignSubmitTxUrl = endpointsMap.get(
            "internalSignSubmitTx"
        )!;
        this.renewEndpointsUrl = endpointsMap.get("renewEndpoints")!;

        this.isInternalWalletHandle = JSON.parse(
            endpointsMap.get("isInternalWalletHandle") as string
        );
    }

    async renewEndpoints(): Promise<void> {
        let endpointsMap = await this.fetchData(this.renewEndpointsUrl);

        this.validateFormDataUrl = endpointsMap.get("validateFormData")!;
        this.buildTxCborUrl = endpointsMap.get("buildTxCbor")!;
        this.internalCheckFeeUrl = endpointsMap.get("internalCheckFee")!;
        this.internalSignSubmitTxUrl = endpointsMap.get(
            "internalSignSubmitTx"
        )!;
        this.renewEndpointsUrl = endpointsMap.get("renewEndpoints")!;
    }

    isInternalWallet(): boolean {
        return this.isInternalWalletHandle;
    }

    async validateFormData(): Promise<boolean> {
        let result = await this.fetchData(this.validateFormDataUrl);

        return JSON.parse(result.get("isValid") as string);
    }

    async buildTxCbor(utxos: string, changeAddress: string): Promise<string> {
        let result = await this.fetchData(
            this.buildTxCborUrl + "&_changeAddress=" + changeAddress,
            { "wallet-utxos-json": utxos }
        );

        this.txCbor = result.get("unsignedTxCbor") as string;
        this.calculatedTxHash = result.get("calculatedTxHash") as string;

        return this.txCbor;
    }

    async internalBuildTxCbor(): Promise<string> {
        let result = await this.fetchData(this.buildTxCborUrl);

        this.txCbor = result.get("unsignedTxCbor") as string;
        this.calculatedTxHash = result.get("calculatedTxHash") as string;

        return this.txCbor;
    }

    async internalCheckFee(): Promise<boolean> {
        let result = await this.fetchData(this.internalCheckFeeUrl, {
            "unsigned-tx-cbor": this.txCbor,
        });

        return JSON.parse(result.get("isWithinFeeLimit") as string);
    }

    async internalSignSubmitTx(): Promise<string> {
        let result = await this.fetchData(this.internalSignSubmitTxUrl, {
            "unsigned-tx-cbor": this.txCbor,
        });

        return result.get("txHash") as string;
    }

    validateTxHash(actualTxHash: string): boolean {
        return this.calculatedTxHash === actualTxHash;
    }

    private async fetchData(
        url: string,
        customHeaders?: object
    ): Promise<Map<string, string>> {
        const response = await fetch(url, {
            method: "POST",
            headers: {
                "plugin-props-json": this.pluginPropertiesJson,
                ...customHeaders,
            },
            body: new FormData(this.formObj),
        });
        const jsonObj = await response.json();

        return new Map(Object.entries(jsonObj));
    }
}

export type WalletWebServiceType = WalletWebService;

export async function createService(
    formObj: HTMLFormElement,
    initServiceUrl: string,
    pluginPropertiesJson: string
) {
    const webService = new WalletWebService(
        formObj,
        initServiceUrl,
        pluginPropertiesJson
    );
    await webService._init();

    return webService;
}
