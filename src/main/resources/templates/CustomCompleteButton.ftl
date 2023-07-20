<div id="cardano-transaction-data-handle" class="form-cell" ${elementMetaData!} style="display:none;" data-serviceurl="${initService!}" data-propjson="${propJson!}"></div>
<script type="module">
    import * as Handler from "${request.contextPath}/plugin/org.joget.cardano.lib.processformmodifier.CardanoTransactionExecutor/CardanoWalletHandler.js";
    window._cardanoWalletHandler = Handler;
    console.log(window._cardanoWalletHandler);
    console.log(Object.keys(window._cardanoWalletHandler));
    const bindFn = window._cardanoWalletHandler.bind; //On first load, function somehow is undefined??!
    console.log(bindFn);
    await bindFn();
    //window._cardanoWalletHandler = import("${request.contextPath}/plugin/org.joget.cardano.lib.processformmodifier.CardanoTransactionExecutor/CardanoWalletHandler.js");
    /*window._cardanoWalletHandler.then((value) => {
        console.log(value);
        console.log(Object.keys(value));
        console.log(await value.bindWalletHandler);
    });*/

    //const x = await window._cardanoWalletHandler.bindWalletHandler;
    //x();
</script>
