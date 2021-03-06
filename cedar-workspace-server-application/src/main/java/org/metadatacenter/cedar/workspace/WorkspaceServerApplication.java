package org.metadatacenter.cedar.workspace;

import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import org.metadatacenter.bridge.CedarDataServices;
import org.metadatacenter.cedar.util.dw.CedarMicroserviceApplication;
import org.metadatacenter.cedar.workspace.health.FolderServerHealthCheck;
import org.metadatacenter.cedar.workspace.resources.*;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.model.ServerName;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.rest.context.CedarRequestContextFactory;
import org.metadatacenter.server.AdminServiceSession;
import org.metadatacenter.server.service.UserService;

public class WorkspaceServerApplication extends CedarMicroserviceApplication<WorkspaceServerConfiguration> {

  public static void main(String[] args) throws Exception {
    new WorkspaceServerApplication().run(args);
  }

  @Override
  protected ServerName getServerName() {
    return ServerName.WORKSPACE;
  }

  @Override
  protected void initializeWithBootstrap(Bootstrap<WorkspaceServerConfiguration> bootstrap, CedarConfig cedarConfig) {
  }

  @Override
  public void initializeApp() {
    CedarDataServices.initializeWorkspaceServices(cedarConfig);

    // Create Workspace global objects, if needed
    UserService userService = CedarDataServices.getUserService();
    CedarRequestContext cedarRequestContext = CedarRequestContextFactory.fromAdminUser(cedarConfig, userService);
    AdminServiceSession adminSession = CedarDataServices.getAdminServiceSession(cedarRequestContext);
    adminSession.ensureGlobalObjectsExists();
  }

  @Override
  public void runApp(WorkspaceServerConfiguration configuration, Environment environment) {
    environment.jersey().register(new IndexResource());
    environment.jersey().register(new CommandResource(cedarConfig));
    environment.jersey().register(new FolderContentsResource(cedarConfig));
    environment.jersey().register(new FoldersResource(cedarConfig));
    environment.jersey().register(new NodesResource(cedarConfig));
    environment.jersey().register(new ResourcesResource(cedarConfig));
    environment.jersey().register(new SearchResource(cedarConfig));
    environment.jersey().register(new UsersResource(cedarConfig));

    final FolderServerHealthCheck healthCheck = new FolderServerHealthCheck();
    environment.healthChecks().register("message", healthCheck);
  }
}
