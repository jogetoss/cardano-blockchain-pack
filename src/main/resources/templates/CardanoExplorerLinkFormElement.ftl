<#if includeMetaData>
    <div class="form-cell explorer-react-component" ${elementMetaData!}>
        <span class='form-floating-label'>Cardano Explorer Link</span>
    </div>
<#else>
    <div class="form-cell explorer-react-component" ${elementMetaData!}
        data-element-key="${element.properties.elementUniqueKey!}"
        data-is-valid-value="${isValidValue!?c}"
        data-explorer-url="${explorerUrl!}"
        data-display-as="${element.properties.displayAs!}"
        data-link-target="${element.properties.linkTarget!}"
        data-invalid-value-behavior="${element.properties.invalidValueBehavior!}"
        data-button-label="${element.properties.buttonLabel!}"
        data-hyperlink-label="${element.properties.hyperlinkLabel!}"
    >
    </div>
    <#if !(request.getAttribute("org.joget.cardano.lib.CardanoExplorerLinkFormElement")??)>
        <script type="module">
        import { renderComponent } from "${request.contextPath}/plugin/org.joget.cardano.lib.CardanoExplorerLinkFormElement/ExplorerButton.js";
        renderComponent();
        </script>
    </#if>
</#if>
