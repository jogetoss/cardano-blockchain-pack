package org.joget.cardano.lib.processformmodifier.components;

import com.google.gson.Gson;
import java.util.Map;
import org.joget.apps.form.model.Form;
import org.joget.apps.form.model.FormButton;
import org.joget.apps.form.model.FormData;
import org.joget.apps.form.service.FormUtil;
import org.joget.apps.workflow.lib.AssignmentCompleteButton;
import org.joget.cardano.lib.processformmodifier.CardanoTransactionExecutor;
import org.joget.commons.util.SecurityUtil;
import org.joget.plugin.base.HiddenPlugin;

public class CustomCompleteButton extends AssignmentCompleteButton implements HiddenPlugin {
    
    private CardanoTransactionExecutor plugin;
    private Form form;
    
    public CustomCompleteButton() {}
    
    public CustomCompleteButton(CardanoTransactionExecutor plugin, Form form) {
        this.plugin = plugin;
        this.form = form;
    }
    
    @Override
    public String renderTemplate(FormData formData, Map dataModel) {
        FormButton button = new AssignmentCompleteButton();
        button.setProperties(getProperties());
        
        Map txExecutorPluginProps = plugin.getProperties();
        txExecutorPluginProps.putAll(plugin.getInitServiceNonce(form));
        dataModel.put("propJson", SecurityUtil.encrypt(new Gson().toJson(txExecutorPluginProps)));
        dataModel.put("initService", plugin.getInitServiceUrl(form));
        
        //JS entry point
        String extraHtml = FormUtil.generateElementHtml(this, formData, "CustomCompleteButton.ftl", dataModel);
        
        return button.renderTemplate(formData, dataModel) + extraHtml;
    }
    
    @Override
    public FormData actionPerformed(Form form, FormData formData) {
        return super.actionPerformed(form, formData);
    }
}
