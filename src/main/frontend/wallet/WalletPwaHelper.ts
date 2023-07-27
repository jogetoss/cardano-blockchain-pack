interface ToastProps {
    heading: string;
    text: string;
    bgColor?: string;
    loaderBg?: string;
    textColor?: string;
    position?: string;
    showHideTransition?: string;
    allowToastClose?: boolean;
    loader?: boolean;
    hideAfter?: number | boolean;
    stack?: boolean;
}

export class WalletPwaHelper {
    //Notices
    static requestingWalletPermission(): void {
        toast({
            heading: "Cardano - dApp Wallet Bridge",
            text: "Connecting wallet...",
            hideAfter: false,
        });
    }
    static buildingTx(): void {
        toast({
            heading: "Cardano - Building Your Transaction",
            text: "Please wait...",
            hideAfter: false,
        });
    }
    static signingTx(): void {
        toast({
            heading: "Cardano - Transaction Signing",
            text: "Waiting for wallet to sign the transaction...",
            hideAfter: false,
        });
    }
    static submittingTx(): void {
        toast({
            heading: "Cardano - Submitting Transaction",
            text: "Please wait...",
            hideAfter: false,
        });
    }

    //Warnings
    static walletConnectCancelled(): void {
        toast({
            heading: "Cardano - dApp Wallet Bridge",
            text: "Wallet connection cancelled.<br>No further action performed.",
            bgColor: "#d47603",
        });
    }
    static txSigningCancelled(): void {
        toast({
            heading: "Cardano - Transaction Signing",
            text: "Wallet cancelled signing the transaction.<br>No further action performed.",
            bgColor: "#d47603",
        });
    }

    //Errors
    static submitTxError(): void {
        toast({
            heading: "Cardano - Transaction Error",
            text: "Unable to submit transaction. Please contact the server administrator.",
            bgColor: "#a90b1b",
            hideAfter: 12000,
        });
    }
    static genericError(): void {
        toast({
            heading: "Cardano - Server Error",
            text: "Something went wrong. Please contact the server administrator.",
            bgColor: "#a90b1b",
            hideAfter: 12000,
        });
    }

    static unblockUI(): void {
        $.unblockUI();
    }
}

const defaultProps: ToastProps = {
    heading: "",
    text: "",
    bgColor: "#0045b5",
    loaderBg: "#f87c2d",
    textColor: "white",
    position: "bottom-right",
    showHideTransition: "slide",
    allowToastClose: false,
    loader: false,
    hideAfter: 6000,
    stack: false,
};

function toast(definedProps: ToastProps) {
    $.toast({
        ...defaultProps,
        ...definedProps,
    });
}
