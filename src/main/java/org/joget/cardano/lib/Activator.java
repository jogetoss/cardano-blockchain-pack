package org.joget.cardano.lib;

import java.util.ArrayList;
import java.util.Collection;
import org.joget.cardano.lib.plugindefaultproperties.CardanoDefaultBackendPlugin;
import org.joget.cardano.lib.processformmodifier.CardanoTransactionExecutor;
import org.joget.cardano.lib.processformmodifier.actions.TokenBurnAction;
import org.joget.cardano.lib.processformmodifier.actions.TokenMintAction;
import org.joget.cardano.lib.processformmodifier.actions.TokenTransferAction;
import org.joget.cardano.lib.processformmodifier.components.CustomCompleteButton;
import org.joget.cardano.lib.webservice.internal.HelperWebService;
import org.joget.cardano.model.transaction.CardanoTransactionPlugin;
import static org.joget.cardano.util.PluginUtil.MESSAGE_PATH;
import org.joget.plugin.base.CustomPluginInterface;
import org.joget.plugin.base.PluginManager;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

public class Activator implements BundleActivator {

    protected Collection<ServiceRegistration> registrationList;

    @Override
    public void start(BundleContext context) {registrationList = new ArrayList<ServiceRegistration>();

        // Process Tool plugins
        registrationList.add(context.registerService(CardanoGenerateAccountTool.class.getName(),new CardanoGenerateAccountTool(), null));
        registrationList.add(context.registerService(CardanoSendTransactionTool.class.getName(),new CardanoSendTransactionTool(), null));
        registrationList.add(context.registerService(CardanoMintTokenTool.class.getName(), new CardanoMintTokenTool(), null));
        registrationList.add(context.registerService(CardanoBurnTokenTool.class.getName(), new CardanoBurnTokenTool(), null));

        // Form Binder plugins
        registrationList.add(context.registerService(CardanoAccountLoadBinder.class.getName(),new CardanoAccountLoadBinder(), null));
        registrationList.add(context.registerService(CardanoMetadataLoadBinder.class.getName(),new CardanoMetadataLoadBinder(), null));
        registrationList.add(context.registerService(CardanoTokenLoadBinder.class.getName(),new CardanoMetadataLoadBinder(), null));


        // Form Element plugins
        registrationList.add(context.registerService(CardanoExplorerLinkFormElement.class.getName(),new CardanoExplorerLinkFormElement(), null));

        // Default Properties plugins
        registrationList.add(context.registerService(CardanoDefaultBackendPlugin.class.getName(),
                new CardanoDefaultBackendPlugin(), null));

        // Web Service plugins
        registrationList.add(context.registerService(HelperWebService.class.getName(), new HelperWebService(), null));

        // Process Form Modifier plugins
        registrationList.add(context.registerService(CardanoTransactionExecutor.class.getName(),
                new CardanoTransactionExecutor(), null));
        registrationList
                .add(context.registerService(CustomCompleteButton.class.getName(), new CustomCompleteButton(), null));

        // Transaction Action plugins (Custom)
        registrationList
                .add(context.registerService(TokenTransferAction.class.getName(), new TokenTransferAction(), null));
        registrationList.add(context.registerService(TokenMintAction.class.getName(), new TokenMintAction(), null));
        registrationList.add(context.registerService(TokenBurnAction.class.getName(), new TokenBurnAction(), null));

        // Custom Interfaces
        PluginManager.registerCustomPluginInterface(new CustomPluginInterface(CardanoTransactionPlugin.class,
                "cardano.plugin.transactionAction", MESSAGE_PATH));
    }

    @Override
    public void stop(BundleContext context) {
        for (ServiceRegistration registration : registrationList) {
            registration.unregister();
        }
        PluginManager.unregisterCustomPluginInterface(CardanoTransactionPlugin.class.getName());
    }
}
