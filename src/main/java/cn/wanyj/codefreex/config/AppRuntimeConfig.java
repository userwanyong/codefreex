package cn.wanyj.codefreex.config;

import lombok.Data;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 运行时配置
 *
 * @author BanXia
 */
@Configuration
public class AppRuntimeConfig {

    @Bean
    @ConfigurationProperties(prefix = "app.nginx")
    public NginxProperties nginxProperties() {
        return new NginxProperties();
    }

    @Bean
    @ConfigurationProperties(prefix = "app.screenshot")
    public ScreenshotProperties screenshotProperties() {
        return new ScreenshotProperties();
    }

    @Bean
    @ConfigurationProperties(prefix = "app.workflow")
    public WorkflowProperties workflowProperties() {
        return new WorkflowProperties();
    }

    @Bean
    public DeployAccessProperties deployAccessProperties(ServerProperties serverProperties) {
        return new DeployAccessProperties(serverProperties);
    }

    @Data
    public static class NginxProperties {
        private boolean enabled = false;
        private boolean reloadOnDeploy = false;
        private String binaryPath;
        private String confDir;
        private String serverNameSuffix = ".local";
    }

    @Data
    public static class ScreenshotProperties {
        private boolean enabled = true;
        private String browser = "edge";
        private long pageLoadTimeoutSeconds = 20;
        private long renderWaitMillis = 1500;
        private String driverPath;
        private String browserBinaryPath;
        private int width = 1440;
        private int height = 900;
    }

    @Data
    public static class WorkflowProperties {
        private int maxQualityRetry = 2;
        private boolean vueBuildEnabled = true;
        private String npmCommand = "npm";
        private boolean imageFetchEnabled = false;
    }

    public static class DeployAccessProperties {
        private final ServerProperties serverProperties;

        public DeployAccessProperties(ServerProperties serverProperties) {
            this.serverProperties = serverProperties;
        }

        public String buildDeployUrl(String deployKey) {
            int port = serverProperties.getPort() == null ? 8080 : serverProperties.getPort();
            String contextPath = serverProperties.getServlet().getContextPath();
            if (contextPath == null) {
                contextPath = "";
            }
            return "http://127.0.0.1:" + port + contextPath + "/deploy/" + deployKey + "/";
        }
    }
}
