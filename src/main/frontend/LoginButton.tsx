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

const domNode = document.getElementById("login-button-component")!;
const root = createRoot(domNode);
root.render(<LoginButton />);

export default LoginButton;
