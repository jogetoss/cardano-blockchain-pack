class WalletWebService {
    private validateFormDataUrl: string = "";
    private buildTxCborUrl: string = "";
    private renewEndpointsUrl: string = "";

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
        this.renewEndpointsUrl = endpointsMap.get("renewEndpoints")!;
    }

    async renewEndpoints() {
        let endpointsMap = await this.fetchData(this.renewEndpointsUrl);

        this.validateFormDataUrl = endpointsMap.get("validateFormData")!;
        this.buildTxCborUrl = endpointsMap.get("buildTxCbor")!;
        this.renewEndpointsUrl = endpointsMap.get("renewEndpoints")!;
    }

    async validateFormData(): Promise<boolean> {
        let result = await this.fetchData(this.validateFormDataUrl);

        return JSON.parse(result.get("isValid") as string);
    }

    async buildTxCbor(
        usedAddress: string,
        changeAddress: string
    ): Promise<string> {
        let result = await this.fetchData(
            this.buildTxCborUrl +
                "&_usedAddress=" +
                usedAddress +
                "&_changeAddress=" +
                changeAddress
        );

        this.calculatedTxHash = result.get("calculatedTxHash") as string;

        return result.get("unsignedTxCbor") as string;
    }

    validateTxHash(actualTxHash: string): boolean {
        return this.calculatedTxHash === actualTxHash;
    }

    private async fetchData(url: string): Promise<Map<string, string>> {
        const response = await fetch(url, {
            method: "POST",
            headers: {
                "plugin-props-json": this.pluginPropertiesJson,
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
