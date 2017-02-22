package no.obos.util.servicebuilder;

import com.google.common.base.Strings;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Wither;
import no.obos.metrics.ObosHealthCheckRegistry;
import org.apache.commons.dbcp2.BasicDataSource;

import javax.sql.DataSource;

/**
 * Knytter opp en datakilde og binder BasicDatasource og QueryRunner til hk2.
 * Ved initialisering (defaults og config) kan det legges til et navn til datakilden
 * for å støtte flere datakilder. Parametre fre properties vil da leses fra
 * navnet (databasenavn).db.url osv.
 */
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class BasicDatasourceAddon implements DataSourceAddon {

    public static final String CONFIG_KEY_DB_URL = "db.url";
    public static final String CONFIG_KEY_DB_DRIVER_CLASS_NAME = "db.driverClassName";
    public static final String CONFIG_KEY_DB_USERNAME = "db.username";
    public static final String CONFIG_KEY_DB_PASSWORD = "db.password";
    public static final String CONFIG_KEY_DB_VALIDATION_QUERY = "db.validationQuery";

    @Wither
    @Getter
    public final String name;
    @Wither
    public final String url;
    @Wither
    public final String driverClassName;
    @Wither
    public final String username;
    @Wither
    public final String password;
    @Wither
    public final String validationQuery;
    @Wither
    public final boolean monitorIntegration;
    @Wither
    @Getter
    public final DataSource dataSource;

    public static BasicDatasourceAddon defaults = new BasicDatasourceAddon(null, null, null, null, null, null, true, null);

    @Override
    public Addon finalize(ServiceConfig serviceConfig) {
        BasicDataSource dataSource = new BasicDataSource();
        dataSource.setUrl(url);
        dataSource.setDriverClassName(driverClassName);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        dataSource.setValidationQuery(validationQuery);

        return this.withDataSource(dataSource);
    }

    @Override
    public Addon withProperties(PropertyProvider properties) {
        String prefix = Strings.isNullOrEmpty(name) ? "" : name + ".";
        properties.failIfNotPresent(prefix + CONFIG_KEY_DB_URL, prefix + CONFIG_KEY_DB_USERNAME, prefix + CONFIG_KEY_DB_PASSWORD, prefix + CONFIG_KEY_DB_DRIVER_CLASS_NAME, prefix + CONFIG_KEY_DB_VALIDATION_QUERY);
        return this
                .withUrl(properties.get(prefix + CONFIG_KEY_DB_URL))
                .withUsername(properties.get(prefix + CONFIG_KEY_DB_USERNAME))
                .withPassword(properties.get(prefix + CONFIG_KEY_DB_PASSWORD))
                .withDriverClassName(properties.get(prefix + CONFIG_KEY_DB_DRIVER_CLASS_NAME))
                .withValidationQuery(properties.get(prefix + CONFIG_KEY_DB_VALIDATION_QUERY));
    }



    @Override
    public void addToJerseyConfig(JerseyConfig jerseyConfig) {
        jerseyConfig.addBinder(binder -> {
                    if (! Strings.isNullOrEmpty(name)) {
                        binder.bind(dataSource).named(name).to(DataSource.class);
                    } else {
                        binder.bind(dataSource).to(DataSource.class);
                    }
                }
        );
    }

    @Override
    public void addToJettyServer(JettyServer jettyServer) {
        if (monitorIntegration) {
            String dataSourceName = Strings.isNullOrEmpty(name)
                    ? " (" + name + ")"
                    : "";
            ObosHealthCheckRegistry.registerDataSourceCheck("Database" + dataSourceName + ": " + url, dataSource, validationQuery);
        }
    }
}
