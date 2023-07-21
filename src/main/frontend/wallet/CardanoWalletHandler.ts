import { BrowserWallet } from "@meshsdk/core";
import { createService, type WalletWebServiceType } from "./WalletWebService";
import { WalletPwaToast } from "./WalletPwaToast";

class CardanoWalletHandler {
    private walletWebService: WalletWebServiceType | null = null;

    readonly formObj = document.querySelector(
        "#form-canvas > form"
    ) as HTMLFormElement;
    readonly completeButton = this.formObj.querySelector(
        "#assignmentComplete"
    ) as HTMLInputElement;
    readonly handleComponent = this.formObj.querySelector(
        "#cardano-transaction-data-handle"
    ) as HTMLElement;

    constructor() {
        this._initHandler();
    }

    private async _initHandler(): Promise<void> {
        //Cater to ajax_loaded pages
        $(this.formObj).off("submit");

        //Cater to browser refresh directly on page
        /* Joget custom event "page_loaded" attached to form body */
        $("body").one("page_loaded", this.formObj, function () {
            $(this).off("submit");
        });

        const installedWallets = await BrowserWallet.getInstalledWallets();
        if (!Array.isArray(installedWallets) || !installedWallets.length) {
            this.completeButton.value = "No Cardano wallet found...";
        } else {
            this.walletWebService = await createService(
                this.formObj,
                this.handleComponent.dataset.serviceurl as string,
                this.handleComponent.dataset.propjson as string
            );

            //Re-enable assignment complete button after service initialized
            if (this.walletWebService) {
                this.formObj.addEventListener(
                    "submit",
                    (event) => {
                        event.preventDefault();
                        event.stopPropagation();
                        this.handle();
                        return false;
                    },
                    { once: true } //prevent infinite loop
                );

                this.completeButton.disabled = false;
                this.completeButton.value = "Initiate Transaction";
            } else {
                this.completeButton.value = "Unable to start wallet service...";
            }
        }
    }

    private async handle(): Promise<void> {
        const isFormDataValid = await this.walletWebService!.validateFormData();

        if (isFormDataValid) {
            WalletPwaToast.showBuildingTxToast();

            // /* MOCK TEST */
            // const address =
            //     "addr_test1qrcqj305zeg034vwcmsyeq7dvspdjf4uap4wxmqrt4d26ty7yfm5444vgsdal5044pke7x2ldauqag6802wm8azkg9tqw00pc3";
            // const unsignedTxCbor = await walletWebService!.buildTxCbor(
            //     address,
            //     address
            // );

            const installedWallets = await BrowserWallet.getInstalledWallets();
            const wallet = await BrowserWallet.enable(installedWallets[0].name);
            const usedAddress = await wallet.getUsedAddresses()[0];
            const changeAddress = await wallet.getChangeAddress();
            const unsignedTxCbor = await this.walletWebService!.buildTxCbor(
                usedAddress,
                changeAddress
            );
            const signedTx = await wallet.signTx(unsignedTxCbor);
            const txHash: string = await wallet.submitTx(signedTx);
            if (this.walletWebService!.validateTxHash(txHash)) {
                //Logic to remove form data error to allow successful form submit
                console.log("tx hash did match!!!!");
            }
        }

        this.doOriginalFormModification();
        this.formObj.requestSubmit();
    }

    //See form.ftl
    private doOriginalFormModification(): void {
        const connectionManager = (window as any).ConnectionManager;
        if (connectionManager) {
            const tokenName = connectionManager.tokenName;
            const tokenValue = connectionManager.tokenValue;

            if (!!!this.formObj.querySelector("[name='" + tokenName + "']")) {
                this.formObj.append(
                    '<input type="hidden" style="display:none;" name="' +
                        tokenName +
                        '" value="' +
                        tokenValue +
                        '"/>'
                );
            }
        }
    }
}

export function bindHandler(): void {
    new CardanoWalletHandler();
}
