package org.metadatacenter.cedar.workspace.config;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.config.environment.CedarEnvironmentVariable;
import org.metadatacenter.config.environment.CedarEnvironmentVariableProvider;
import org.metadatacenter.model.SystemComponent;
import org.metadatacenter.util.test.TestUtil;

import java.util.HashMap;
import java.util.Map;

public class CedarConfigWorkspaceTest {

  @Before
  public void setEnvironment() {
    Map<String, String> env = new HashMap<>();

    env.put(CedarEnvironmentVariable.CEDAR_HOST.getName(), "metadatacenter.orgx");

    env.put(CedarEnvironmentVariable.CEDAR_NET_GATEWAY.getName(), "127.0.0.1");

    env.put(CedarEnvironmentVariable.CEDAR_ADMIN_USER_API_KEY.getName(), "1234");

    env.put(CedarEnvironmentVariable.CEDAR_NEO4J_USER_NAME.getName(), "neo4j");
    env.put(CedarEnvironmentVariable.CEDAR_NEO4J_USER_PASSWORD.getName(), "password");
    env.put(CedarEnvironmentVariable.CEDAR_NEO4J_HOST.getName(), "127.0.0.1");
    env.put(CedarEnvironmentVariable.CEDAR_NEO4J_REST_PORT.getName(), "7474");
    env.put(CedarEnvironmentVariable.CEDAR_NEO4J_BOLT_PORT.getName(), "7687");

    env.put(CedarEnvironmentVariable.CEDAR_MONGO_APP_USER_NAME.getName(), "cedarUser");
    env.put(CedarEnvironmentVariable.CEDAR_MONGO_APP_USER_PASSWORD.getName(), "password");
    env.put(CedarEnvironmentVariable.CEDAR_MONGO_HOST.getName(), "localhost");
    env.put(CedarEnvironmentVariable.CEDAR_MONGO_PORT.getName(), "27017");

    env.put(CedarEnvironmentVariable.CEDAR_WORKSPACE_HTTP_PORT.getName(), "9008");
    env.put(CedarEnvironmentVariable.CEDAR_WORKSPACE_ADMIN_PORT.getName(), "9108");
    env.put(CedarEnvironmentVariable.CEDAR_WORKSPACE_STOP_PORT.getName(), "9208");

    env.put(CedarEnvironmentVariable.CEDAR_USER_HTTP_PORT.getName(), "9005");

    TestUtil.setEnv(env);
  }

  @Test
  public void testGetInstance() throws Exception {
    SystemComponent systemComponent = SystemComponent.SERVER_WORKSPACE;
    Map<String, String> environment = CedarEnvironmentVariableProvider.getFor(systemComponent);
    CedarConfig instance = CedarConfig.getInstance(environment);
    Assert.assertNotNull(instance);
  }

}