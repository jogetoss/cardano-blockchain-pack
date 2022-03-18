package org.joget.cardano.lib;

import java.util.ArrayList;
import java.util.Collection;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

public class Activator implements BundleActivator {

    protected Collection<ServiceRegistration> registrationList;

    public void start(BundleContext context) {
        registrationList = new ArrayList<ServiceRegistration>();

        //Register plugin here
        registrationList.add(context.registerService(CardanoGenerateAccountTool.class.getName(), new CardanoGenerateAccountTool(), null));
        registrationList.add(context.registerService(CardanoSendTransactionTool.class.getName(), new CardanoSendTransactionTool(), null));
        registrationList.add(context.registerService(CardanoAccountLoadBinder.class.getName(), new CardanoAccountLoadBinder(), null));
        registrationList.add(context.registerService(CardanoMetadataLoadBinder.class.getName(), new CardanoMetadataLoadBinder(), null));
        registrationList.add(context.registerService(CardanoMintTokenTool.class.getName(), new CardanoMintTokenTool(), null));
    }

    public void stop(BundleContext context) {
        for (ServiceRegistration registration : registrationList) {
            registration.unregister();
        }
    }
}