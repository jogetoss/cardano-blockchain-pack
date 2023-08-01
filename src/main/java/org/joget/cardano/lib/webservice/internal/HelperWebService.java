package org.joget.cardano.lib.webservice.internal;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.joget.apps.app.dao.DatalistDefinitionDao;
import org.joget.apps.app.model.DatalistDefinition;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.datalist.model.DataListColumn;
import org.joget.apps.datalist.service.DataListService;
import org.joget.cardano.util.PluginUtil;
import org.joget.commons.util.LogUtil;
import org.joget.plugin.base.ExtDefaultPlugin;
import org.joget.plugin.base.HiddenPlugin;
import org.joget.plugin.base.PluginWebSupport;
import org.joget.workflow.model.service.WorkflowUserManager;
import org.joget.workflow.util.WorkflowUtil;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.context.ApplicationContext;

public class HelperWebService extends ExtDefaultPlugin implements PluginWebSupport, HiddenPlugin {
    
    @Override
    public String getName() {
        return "Cardano Helper Web Service";
    }
    
    @Override
    public String getDescription() {
        return "Provides common functions for Cardano plugins.";
    }
    
    @Override
    public void webService(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        if (!WorkflowUtil.isCurrentUserInRole(WorkflowUserManager.ROLE_ADMIN)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        
        if ("getDatalistColumns".equalsIgnoreCase(request.getParameter("action"))) {
            try {
                ApplicationContext ac = AppUtil.getApplicationContext();
                DatalistDefinitionDao datalistDefinitionDao = (DatalistDefinitionDao) ac.getBean("datalistDefinitionDao");
                
                DatalistDefinition datalistDefinition = datalistDefinitionDao.loadById(
                        request.getParameter("id"), 
                        AppUtil.getCurrentAppDefinition()
                );
                
                JSONArray columns = new JSONArray();
                if (datalistDefinition != null) {
                    DataListService dataListService = (DataListService) ac.getBean("dataListService");
                    DataListColumn[] datalistcolumns = dataListService.fromJson(datalistDefinition.getJson()).getColumns();
                    
                    for (DataListColumn datalistcolumn : datalistcolumns) {
                        JSONObject column = new JSONObject();
                        column.put("value", datalistcolumn.getName());
                        column.put("label", datalistcolumn.getLabel());
                        columns.put(column);
                    }
                }
                columns.write(response.getWriter());
            } catch (Exception e) {
                LogUtil.error(this.getClass().getName(), e, "Unable to retrieve datalist columns for plugin properties.");
            } 
        } else {
            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
        }
    }

    @Override
    public String getVersion() {
        return PluginUtil.getProjectVersion(this.getClass());
    }
}
