import { BrowserWallet } from "@meshsdk/core";
import { createService, type WalletWebServiceType } from "./WalletWebService";
import { WalletPwaHelper } from "./WalletPwaHelper";
import { Buffer } from "buffer";

class CardanoWalletHandler {
    private walletWebService: WalletWebServiceType | null = null;
    private installedWallets: any;

    readonly formObj = document.querySelector(
        "#form-canvas > form"
    ) as HTMLFormElement;
    readonly completeButton = this.formObj.querySelector(
        "input#assignmentComplete.form-button"
    ) as HTMLInputElement;
    readonly handleComponent = this.formObj.querySelector(
        "#section-actions div#cardano-transaction-data-handle"
    ) as HTMLElement;

    constructor() {
        window.Buffer = Buffer;
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

        this.installedWallets = await BrowserWallet.getInstalledWallets();

        //If no wallet found, block form submission
        if (
            !Array.isArray(this.installedWallets) ||
            !this.installedWallets.length
        ) {
            this.formObj.addEventListener("submit", (event) => {
                WalletPwaHelper.unblockUI();
                event.preventDefault();
                event.stopPropagation();
                return false;
            });
            this.completeButton.disabled = true;
            this.completeButton.value = "No Cardano wallet found...";

            return;
        }

        this.walletWebService = await createService(
            this.formObj,
            this.handleComponent.dataset.serviceurl as string,
            this.handleComponent.dataset.propjson as string
        );

        if (this.walletWebService) {
            this.bindSubmitFormListener();
            this.completeButton.disabled = false;
            this.completeButton.value = "Initiate Transaction";
        } else {
            WalletPwaHelper.genericError();
            this.completeButton.value = "Unable to start wallet service...";
        }
    }

    private async handle(): Promise<void> {
        const isFormDataValid = await this.walletWebService!.validateFormData();
        if (!isFormDataValid) {
            //Submit form as usual to display form errors to end user
            this.submitAssignment();
            return;
        }

        try {
            let wallet;
            try {
                WalletPwaHelper.requestingWalletPermission();

                //TODO: If user has multiple wallets installed, allow user to select their preferred wallet
                wallet = await BrowserWallet.enable(
                    this.installedWallets[0].name
                );
            } catch (e) {
                await this.renewService();
                WalletPwaHelper.walletConnectCancelled();
                return;
            }

            const utxos = await wallet.getUtxos();
            const changeAddress = await wallet.getChangeAddress();

            WalletPwaHelper.buildingTx();
            const unsignedTxCbor = await this.walletWebService!.buildTxCbor(
                JSON.stringify(utxos),
                changeAddress
            );

            WalletPwaHelper.signingTx();
            let signedTx;
            try {
                signedTx = await wallet.signTx(unsignedTxCbor);
            } catch (e) {
                await this.renewService();
                WalletPwaHelper.txSigningCancelled();
                return;
            }

            WalletPwaHelper.submittingTx();
            let txHash;
            try {
                txHash = await wallet.submitTx(signedTx);
            } catch (e) {
                await this.renewService();
                WalletPwaHelper.submitTxError();
                throw e;
            }

            if (this.walletWebService!.validateTxHash(txHash)) {
                //Logic to remove form data error to allow successful form submit
                console.log("LOG --> tx hash did match!!!!");
                this.submitAssignment();
            } else {
                console.log("LOG --> tx hash does NOT match!!");
            }
        } catch (e) {
            await this.renewService();
            WalletPwaHelper.genericError();
            throw e;
        }
    }

    private async renewService(): Promise<void> {
        await this.walletWebService?.renewEndpoints();
        this.bindSubmitFormListener();
        WalletPwaHelper.unblockUI();
    }

    private bindSubmitFormListener(): void {
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
    }

    //See form.ftl
    private doOriginalFormModification(): void {
        const connectionManager = (window as any).ConnectionManager;
        if (connectionManager) {
            const tokenName = connectionManager.tokenName;
            const tokenValue = connectionManager.tokenValue;

            if (!!!this.formObj.querySelector("[name='" + tokenName + "']")) {
                this.formObj.insertAdjacentHTML(
                    "beforeend",
                    '<input type="hidden" name="' +
                        tokenName +
                        '" value="' +
                        tokenValue +
                        '"/>'
                );
            }
        }
    }

    private submitAssignment() {
        this.doOriginalFormModification();
        this.formObj.insertAdjacentHTML(
            "beforeend",
            '<input type="hidden" name="CARDANO_VALID_SUBMISSION" value="true"/>'
        );
        this.completeButton.click();
    }
}

export function bindHandler(): void {
    new CardanoWalletHandler();
}
