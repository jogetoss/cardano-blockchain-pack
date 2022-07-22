<#if includeMetaData>
    <div class="form-cell" ${elementMetaData!}>
        <span class='form-floating-label'>Cardano Explorer Link</span>
    </div>
<#else>
    <#assign displayAs = element.properties.displayAs>
    <#assign linkTarget = element.properties.linkTarget>
    <#assign invalidValueBehavior = element.properties.invalidValueBehavior>

    <#if !isValidValue && invalidValueBehavior == "hideLink">
        <#-- Don't show anything -->
    <#else>
        <#if displayAs == "button">
            <#assign buttonLabel = element.properties.buttonLabel>
            
            <#if linkTarget == "newTab">
                <#assign onclickFunction = "window.open('${explorerUrl!}','_blank'); return false;">
            <#else>
                <#assign onclickFunction = "window.location.href='${explorerUrl!}'; return false;">
            </#if>
            
            <#if !isValidValue && invalidValueBehavior == "disableLink">
                <#assign disableAttr = "disabled">
            </#if>

            <button id="explorer_link_${element.properties.elementUniqueKey!}" ${elementMetaData!} class="explorer-link-button" onclick="${onclickFunction}" ${disableAttr!}>${buttonLabel!}</button>
        <#else>
            <#assign hyperlinkLabel = element.properties.hyperlinkLabel>

            <#if !hyperlinkLabel?has_content>
                <#assign hyperlinkLabel = explorerUrl>
            </#if>
            
            <#if linkTarget == "newTab">
                <#assign clickTarget = "_blank">
            <#else>
                <#assign clickTarget = "_self">
            </#if>

            <#if !isValidValue && invalidValueBehavior == "disableLink">
                <#assign url = "javascript:void(0)">
                <#assign clickTarget = "_self">
            <#else>
                <#assign url = explorerUrl>
            </#if>

            <a id="explorer_link_${element.properties.elementUniqueKey!}" ${elementMetaData!} class="explorer-link-hyperlink" href="${url!}" target="${clickTarget!}">${hyperlinkLabel!}</a>
        </#if>
    </#if>
</#if>
