package properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "communication")
public class CommunicationProperties {
    private String accountService;
    private String frontendCallbackUrl;

    public String getAccountService() {
        return accountService;
    }

    public void setAccountService(String accountService) {
        this.accountService = accountService;
    }

    public String getFrontendCallbackUrl() {
        return frontendCallbackUrl;
    }

    public void setFrontendCallbackUrl(String frontendCallbackUrl) {
        this.frontendCallbackUrl = frontendCallbackUrl;
    }
}
