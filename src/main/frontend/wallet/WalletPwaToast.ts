export class WalletPwaToast {
    static showBuildingTxToast(): void {
        $.toast({
            heading: "Cardano - Building Your Transaction",
            text: "Please wait...",
            bgColor: "#0046b5",
            loaderBg: "#f87c2d",
            textColor: "white",
            position: "bottom-right",
            showHideTransition: "slide",
            allowToastClose: false,
            loader: true,
            hideAfter: 6000,
        });
    }
}
