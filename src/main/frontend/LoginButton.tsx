import { createRoot } from "react-dom/client";
import { CardanoWallet, MeshProvider } from "@meshsdk/react";

function LoginButton() {
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
const domNode = document.getElementById("wallet-login-react-component")!;
createRoot(domNode).render(<LoginButton />);

export default LoginButton;
