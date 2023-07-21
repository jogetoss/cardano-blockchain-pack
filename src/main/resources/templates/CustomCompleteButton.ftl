<div id="cardano-transaction-data-handle" class="form-cell" ${elementMetaData!} style="display:none;" data-serviceurl="${initService!}" data-propjson="${propJson!}"></div>
<script type="module">
    import { bindHandler } from "${request.contextPath}/plugin/org.joget.cardano.lib.processformmodifier.CardanoTransactionExecutor/CardanoWalletHandler.js";
    //TODO: hackish..? find root cause of execution sequence issue for undefined 'bindHandler'
    setTimeout(function() {
        bindHandler();
    }, 100);
</script>
