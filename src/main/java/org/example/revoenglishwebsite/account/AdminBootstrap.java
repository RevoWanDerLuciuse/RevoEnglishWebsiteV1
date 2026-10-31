package org.example.revoenglishwebsite.account;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * One-time creation of the first administrator from environment variables:
 * ADMIN_BOOTSTRAP_LOGIN, ADMIN_BOOTSTRAP_PASSWORD and (optionally) ADMIN_BOOTSTRAP_NAME.
 * <p>
 * If the login already exists nothing is changed — the password is never reset on restart.
 * There is no default administrator: without these variables no admin account is created.
 * Remove the variables once the administrator exists (see README → Student portal).
 */
@Component
class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final AccountService accounts;
    private final Properties properties;

    AdminBootstrap(AccountService accounts, Properties properties) {
        this.accounts = accounts;
        this.properties = properties;
    }

    @ConfigurationProperties(prefix = "admin.bootstrap")
    record Properties(String login, String password, String name) {
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!StringUtils.hasText(properties.login())) {
            return;
        }
        String name = StringUtils.hasText(properties.name()) ? properties.name() : "Администратор";
        try {
            if (accounts.bootstrapAdmin(name, properties.login(), properties.password())) {
                log.warn("Administrator account created from ADMIN_BOOTSTRAP_* variables. "
                        + "Remove ADMIN_BOOTSTRAP_PASSWORD from the environment now.");
            } else {
                log.info("ADMIN_BOOTSTRAP_LOGIN refers to an existing administrator; nothing changed. "
                        + "The ADMIN_BOOTSTRAP_* variables can be removed.");
            }
        } catch (AccountException e) {
            // The message never contains the password
            throw new IllegalStateException("Administrator bootstrap failed (" + e.field() + "): " + e.getMessage());
        }
    }
}
