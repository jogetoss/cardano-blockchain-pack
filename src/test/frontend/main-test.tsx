import { renderComponent as renderExplorerComponent } from "../../main/frontend/CardanoExplorerButton";
import { bind as walletHandlerBind } from "../../main/frontend/wallet/CardanoWalletHandler";
// import { renderComponent as renderLoginComponent } from "../../main/frontend/CardanoWalletLoginButton";

walletHandlerBind();
renderExplorerComponent();
// renderLoginComponent(); //wasm not supported by dev server
