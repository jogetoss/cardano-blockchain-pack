import { createRoot } from "react-dom/client";

export interface Props {
    elementKey?: string;
    isValidValue?: string;
    explorerUrl?: string;
    displayAs?: string;
    linkTarget?: string;
    invalidValueBehavior?: string;
    buttonLabel?: string;
    hyperlinkLabel?: string;
}

function ExplorerButton(props: Props) {
    if (
        props.isValidValue === "false" &&
        props.invalidValueBehavior === "hideLink"
    ) {
        return null;
    }

    return props.displayAs === "button"
        ? showAsButton(props)
        : showAsHyperlink(props);
}

function showAsButton(props: Props) {
    return (
        <button
            id={"explorer_link_" + props.elementKey}
            className="explorer-link-button btn btn-primary"
            type="button"
            onClick={() =>
                window.open(
                    props.explorerUrl,
                    props.linkTarget === "newTab" ? "_blank" : "_self"
                )
            }
            disabled={
                props.isValidValue === "false" &&
                props.invalidValueBehavior === "disableLink"
            }
        >
            {props.buttonLabel}
        </button>
    );
}

function showAsHyperlink(props: Props) {
    return (
        <a
            id={"explorer_link_" + props.elementKey}
            className="explorer-link-hyperlink"
            href={
                props.isValidValue === "false" &&
                props.invalidValueBehavior === "disableLink"
                    ? "javascript:void(0)"
                    : props.explorerUrl
            }
            target={
                props.linkTarget === "newTab" &&
                props.isValidValue === "true" &&
                props.invalidValueBehavior !== "disableLink"
                    ? "_blank"
                    : "_self"
            }
        >
            {!!props.hyperlinkLabel ? props.hyperlinkLabel : props.explorerUrl}
        </a>
    );
}

export function renderComponent() {
    const domList = document.querySelectorAll("div.explorer-react-component");
    for (let domElement of domList) {
        if (domElement instanceof HTMLElement) {
            createRoot(domElement).render(
                <ExplorerButton {...domElement.dataset} />
            );
        }
    }
}

export default ExplorerButton;
