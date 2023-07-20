import { createRoot } from "react-dom/client";
import { CardanoWallet, MeshProvider } from "@meshsdk/react";

function CardanoWalletLoginButton() {
    return (
        <>
            <div>
                <MeshProvider>
                    <CardanoWallet />
                </MeshProvider>
            </div>
        </>
    );
}

//Meant to bind permanently to header nav
const domNode = document.getElementById(
    "cardano-wallet-login-react-component"
)!;
createRoot(domNode).render(<CardanoWalletLoginButton />);

export default CardanoWalletLoginButton;
