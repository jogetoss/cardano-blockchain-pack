package org.joget.cardano.lib.processformmodifier;

import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.transaction.util.TransactionUtil;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.joget.apps.app.dao.FormDefinitionDao;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.model.FormDefinition;
import org.joget.apps.app.model.ProcessFormModifier;
import org.joget.apps.app.model.StartProcessFormModifier;
import org.joget.apps.app.service.AppService;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.model.Element;
import org.joget.apps.form.model.Form;
import org.joget.apps.form.model.FormAction;
import org.joget.apps.form.model.FormData;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.apps.form.model.FormValidator;
import org.joget.apps.form.service.FormService;
import org.joget.apps.form.service.FormUtil;
import org.joget.apps.workflow.lib.AssignmentCompleteButton;
import org.joget.cardano.lib.processformmodifier.components.CustomCompleteButton;
import org.joget.cardano.model.explorer.Explorer;
import org.joget.cardano.model.explorer.ExplorerFactory;
import static org.joget.cardano.model.explorer.ExplorerFactory.DEFAULT_EXPLORER;
import org.joget.cardano.lib.processformmodifier.components.TransactionHandler;
import org.joget.cardano.util.BackendUtil;
import org.joget.cardano.util.PluginUtil;
import org.joget.commons.util.LogUtil;
import org.joget.commons.util.PluginThread;
import org.joget.commons.util.SecurityUtil;
import org.joget.plugin.base.ExtDefaultPlugin;
import org.joget.plugin.base.PluginWebSupport;
import org.joget.workflow.model.WorkflowAssignment;
import org.joget.workflow.model.WorkflowProcessResult;
import org.joget.workflow.util.WorkflowUtil;
import org.springframework.context.ApplicationContext;

public class CardanoTransactionExecutor extends ExtDefaultPlugin implements ProcessFormModifier, StartProcessFormModifier, PluginWebSupport {

    @Override
    public String getName() {
        return "Cardano Transaction Executor";
    }
    
    @Override
    public String getDescription() {
        return "Adds form submission enhancements to perform various transactions on the Cardano blockchain.";
    }

    @Override
    public String getPropertyOptions() {
        String backendConfigs = PluginUtil.readGenericBackendConfigs(getClassName());
        return AppUtil.readPluginResource(
                getClassName(), 
                "/properties/modifier/CardanoTransactionExecutor.json", 
                new String[]{backendConfigs}, 
                true, 
                PluginUtil.MESSAGE_PATH
        );
    }

    protected void modifyForm(Form form, FormData formData) {
        //Replace with custom complete assignment button
        Collection<FormAction> actions = new ArrayList<FormAction>();
        FormAction completeButton = null;
        for (FormAction b : form.getActions()) {
            if ((AssignmentCompleteButton.DEFAULT_ID).equals(b.getPropertyString("id"))) {
                completeButton = b;
                break;
            }
        }
        if (completeButton != null) {
            form.getActions().remove(completeButton);
            
            Element customCompleteButton = (Element) new CustomCompleteButton(this, form);
            customCompleteButton.setProperty("id", AssignmentCompleteButton.DEFAULT_ID);
            customCompleteButton.setProperty("label", "Loading Service...");
            customCompleteButton.setProperty("disabled", "true"); //Disable first, until frontend JS finish init web service
            actions.add((FormAction) customCompleteButton);
        }
        if (!actions.isEmpty()) {
            actions.addAll(form.getActions());
            form.getActions().clear();
            form.getActions().addAll(actions);
        }
        
        form.setValidator(
                new FormValidator() {
                    @Override
                    public boolean validate(Element element, FormData formData, String[] values) {
                        final String isValidScriptFormSubmit = formData.getRequestParameter("CARDANO_VALID_SUBMISSION");
                        if (isValidScriptFormSubmit == null || !"true".equalsIgnoreCase(isValidScriptFormSubmit)) {
                            formData.addFormError(FormUtil.getElementParameterName(form), "Improper form submission blocked");
                            return false;
                        }
                        
                        return true;
                    }

                    @Override
                    public String getPropertyOptions() {
                        return "";
                    }
                    
                    @Override
                    public String getName() {
                        return "(INTERNAL) Cardano Form Submission Validator";
                    }

                    @Override
                    public String getDescription() {
                        return "Used to validate form submission in conjuction with frontend script. For internal use only.";
                    }

                    @Override
                    public String getLabel() {
                        return getName();
                    }

                    @Override
                    public String getVersion() {
                        return PluginUtil.getProjectVersion(this.getClass());
                    }
                    
                    @Override
                    public String getClassName() {
                        return getClass().getName();
                    }
                }
        );
    }
    
    private void storeTxDataToForm(FormData formData) {
        final String formDefId = getPropertyString("formDefIdStoreTxData");
        if (formDefId == null || formDefId.isBlank()) {
            LogUtil.warn(getClassName(), "Unable to store tx data to form. Encountered blank form ID.");
            return;
        }
        
        Map pluginProps = getProperties();
        String primaryKey = formData.getPrimaryKeyValue();
        
        AppService appService = (AppService) AppUtil.getApplicationContext().getBean("appService");
        AppDefinition appDef = AppUtil.getCurrentAppDefinition();
        Explorer explorer = new ExplorerFactory(BackendUtil.getNetworkType(pluginProps)).createExplorer(DEFAULT_EXPLORER);
        
        FormRowSet rowSet = appService.loadFormData(appDef.getAppId(), appDef.getVersion().toString(), formDefId, primaryKey);
        FormRow row = new FormRow();
        if (!rowSet.isEmpty()) {
            row = rowSet.get(0);
        } else {
            primaryKey = null;
        }
        
        row = addValue(
                row, 
                getPropertyString("varTransactionValidated"), 
                "pending"
        );
        row = addValue(
                row, 
                getPropertyString("varTransactionId"), 
                formData.getRequestParameter("CARDANO_TX_HASH")
        );
        row = addValue(
                row, 
                getPropertyString("varTransactionExplorerUrl"), 
                explorer.getTransactionUrl(formData.getRequestParameter("CARDANO_TX_HASH"))
        );
        
        if (!rowSet.isEmpty()) {
            rowSet.set(0, row);
        } else {
            rowSet.add(0, row);
        }
        
        final FormRowSet storedFormRowSet = appService.storeFormData(appDef.getId(), appDef.getVersion().toString(), formDefId, rowSet, primaryKey);
        
        //Use separate thread to wait for tx confirmation
        if (!getPropertyString("varTransactionValidated").isBlank()) {
            final TransactionHandler txHandler = new TransactionHandler(pluginProps, null);
            
            Thread waitTransactionThread = new PluginThread(() -> {
                FormRow storedRow = storedFormRowSet.get(0);
                
                final boolean txConfirmed = txHandler.waitForTx(formData.getRequestParameter("CARDANO_TX_HASH"));
                
                storedRow = addValue(
                        storedRow,
                        getPropertyString("varTransactionValidated"),
                        String.valueOf(txConfirmed)
                );

                storedFormRowSet.set(0, storedRow);

                appService.storeFormData(appDef.getId(), appDef.getVersion().toString(), formDefId, storedFormRowSet, storedRow.getId());
            });
            waitTransactionThread.start();
        }
    }
    
    private FormRow addValue(FormRow row, String field, String value) {
        if (row != null && !field.isEmpty()) {
            row.setProperty(field, value);
        }
        return row;
    }
    
    private String getServiceUrl(Form form, WebServiceType serviceType) {
        AppDefinition appDef = AppUtil.getCurrentAppDefinition();
        
        final String appId = appDef.getAppId();
        final String appVersion = appDef.getVersion().toString();
        final String formDefId = form.getPropertyString(FormUtil.PROPERTY_ID);
        
        final String nonce = SecurityUtil.generateNonce(
                new String[]{
                    this.getClass().getCanonicalName(),
                    appId,
                    appVersion,
                    formDefId,
                    serviceType.toString()
                }
                , 1
        );
        
        try {
            return WorkflowUtil.getHttpServletRequest().getContextPath() 
                    + "/web/json/app/" 
                    + appId
                    + "/"
                    + appVersion
                    + "/plugin/"
                    + getClassName()
                    + "/service"
                    + "?_nonce="+URLEncoder.encode(nonce, "UTF-8")
                    +"&_formDefId="+URLEncoder.encode(formDefId, "UTF-8")
                    +"&_action="+URLEncoder.encode(serviceType.toString(), "UTF-8");
        } catch (Exception ex) {}
        
        return "";
    }
    
    public String getInitServiceUrl(Form form) {
        AppDefinition appDef = AppUtil.getCurrentAppDefinition();
        
        final String appId = appDef.getAppId();
        final String appVersion = appDef.getVersion().toString();
        final String formDefId = form.getPropertyString(FormUtil.PROPERTY_ID);
        
        try {
            return WorkflowUtil.getHttpServletRequest().getContextPath() 
                    + "/web/json/app/" 
                    + appId
                    + "/"
                    + appVersion
                    + "/plugin/"
                    + getClassName()
                    + "/service"
                    +"?_formDefId="+URLEncoder.encode(formDefId, "UTF-8")
                    +"&_action="+URLEncoder.encode(WebServiceType.INIT_SERVICE.toString(), "UTF-8");
        } catch (Exception ex) {}
        
        return "";
    }
    
    public Map getInitServiceNonce(Form form) {
        AppDefinition appDef = AppUtil.getCurrentAppDefinition();
        
        final String appId = appDef.getAppId();
        final String appVersion = appDef.getVersion().toString();
        final String formDefId = form.getPropertyString(FormUtil.PROPERTY_ID);
        
        final String nonce = SecurityUtil.generateNonce(
                new String[]{
                    this.getClass().getCanonicalName(),
                    appId,
                    appVersion,
                    formDefId,
                    WebServiceType.INIT_SERVICE.toString()
                }
                , 1
        );
        
        return Map.of(WebServiceType.INIT_SERVICE.toString() + "_nonce", nonce);
    }
    
    @Override
    public void webService(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AppDefinition appDef = AppUtil.getCurrentAppDefinition();
        Gson gsonObj = new Gson();
        
        this.setProperties(
                gsonObj.fromJson(
                        SecurityUtil.decrypt(request.getHeader("plugin-props-json")), 
                        new TypeToken<Map<String, Object>>() {}.getType()
                )
        );
        
        final String formDefId = request.getParameter("_formDefId");
        final String serviceType = request.getParameter("_action");
        
        final String retrievedNonce = (WebServiceType.INIT_SERVICE).equals(WebServiceType.fromString(serviceType))
                ? getPropertyString(WebServiceType.INIT_SERVICE.toString() + "_nonce")
                : request.getParameter("_nonce");

        if (!SecurityUtil.verifyNonce(
                retrievedNonce, 
                new String[]{
                    this.getClass().getCanonicalName(), 
                    appDef.getAppId(), 
                    appDef.getVersion().toString(), 
                    formDefId, 
                    serviceType
                }
            )
        ) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        
        Map<String, String> jsonMap = new HashMap<>();
        
        switch (WebServiceType.fromString(serviceType)) {
            case RENEW_ENDPOINTS:
            case INIT_SERVICE: {
                ApplicationContext appContext = AppUtil.getApplicationContext();
                FormDefinitionDao formDefinitionDao = (FormDefinitionDao) appContext.getBean("formDefinitionDao");
                FormDefinition formDef = formDefinitionDao.loadById(formDefId, appDef);
                if (formDef == null) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    return;
                }

                FormService formService = (FormService) appContext.getBean("formService");

                FormData formData = formService.retrieveFormDataFromRequest(null, request);
                Form form = formService.loadFormFromJson(formDef.getJson(), formData);

                for (WebServiceType type : WebServiceType.values()) {
                    jsonMap.put(type.toString(), getServiceUrl(form, type));
                }
                
                jsonMap.put("isInternalWalletHandle", String.valueOf("internal".equalsIgnoreCase(getPropertyString("walletHandler"))));
                
                break;
            }
            case VALIDATE_FORM_DATA: {
                ApplicationContext appContext = AppUtil.getApplicationContext();
                FormDefinitionDao formDefinitionDao = (FormDefinitionDao) appContext.getBean("formDefinitionDao");
                FormDefinition formDef = formDefinitionDao.loadById(formDefId, appDef);
                if (formDef == null) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    return;
                }

                FormService formService = (FormService) appContext.getBean("formService");

                FormData formData = formService.retrieveFormDataFromRequest(null, request);
                Form form = formService.loadFormFromJson(formDef.getJson(), formData);
                
                formData = FormUtil.executeElementFormatDataForValidation(form, formData);
                
                jsonMap.put("isValid", String.valueOf(FormUtil.executeValidators(form, formData)));
                
                break;
            }
            case BUILD_TX_CBOR: {
                try {
                    final TransactionHandler txHandler = new TransactionHandler(getProperties(), request);
                    final Transaction unsignedTx = txHandler.createUnsignedTx();
                    if (unsignedTx == null) {
                        return;
                    }
                    
                    jsonMap.put("unsignedTxCbor", unsignedTx.serializeToHex());
                    jsonMap.put("calculatedTxHash", TransactionUtil.getTxHash(unsignedTx));
                    
                    break;
                } catch (Exception ex) {
                    LogUtil.error(getClassName(), ex, "Unable to build transaction CBOR");
                    return;
                }
            }
            case INTERNAL_CHECK_FEE: {
                final TransactionHandler txHandler = new TransactionHandler(getProperties(), request);
                jsonMap.put("isWithinFeeLimit", String.valueOf(txHandler.internalIsWithinFeeLimit()));

                break;
            }
            case INTERNAL_SIGN_SUBMIT_TX: {
                final TransactionHandler txHandler = new TransactionHandler(getProperties(), request);
                jsonMap.put("txHash", txHandler.internalSignAndSubmitTx());

                break;
            }
            default:
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                return;
        }
        
        response.getWriter().write(gsonObj.toJson(jsonMap));
    }
    
    @Override
    public void modify(Form form, FormData formData, String processDefId) {
        modifyForm(form, formData);
    }

    @Override
    public WorkflowProcessResult customSubmissionHandling(Form form, FormData formData, WorkflowProcessResult result) {
        storeTxDataToForm(formData);
        return null;
    }
    
    @Override
    public void modify(Form form, FormData formData, WorkflowAssignment assignment) {
        modifyForm(form, formData);
    }

    @Override
    public boolean customSubmissionHandling(Form form, FormData formData, WorkflowAssignment assignment) {
        storeTxDataToForm(formData);
        return false;
    }
    
    private enum WebServiceType {
        INIT_SERVICE("initService"),
        VALIDATE_FORM_DATA("validateFormData"),
        BUILD_TX_CBOR("buildTxCbor"),
        INTERNAL_CHECK_FEE("internalCheckFee"),
        INTERNAL_SIGN_SUBMIT_TX("internalSignSubmitTx"),
        RENEW_ENDPOINTS("renewEndpoints");

        private final String value;

        WebServiceType(String value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return value;
        }

        public static WebServiceType fromString(String value) {
            for (WebServiceType type : WebServiceType.values()) {
                if ((type.value).equalsIgnoreCase(value)) {
                    return type;
                }
            }

            LogUtil.warn(WebServiceType.class.getName(), "Unknown web service call method found!");
            return null;
        }
    }
    
    @Override
    public String getVersion() {
        return PluginUtil.getProjectVersion(this.getClass());
    }
    
    @Override
    public String getLabel() {
        return getName();
    }

    @Override
    public String getClassName() {
        return getClass().getName();
    }
}
