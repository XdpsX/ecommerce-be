package com.xdpsx.ecommerce.testsupport;

import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

public final class MySqlTestContainerFactory {
    private static final DockerImageName MYSQL_IMAGE = DockerImageName.parse("mysql:8.4");

    private MySqlTestContainerFactory() {}

    public static MySQLContainer create(String databaseName, String... command) {
        MySQLContainer container = new MySQLContainer(MYSQL_IMAGE);
        container.withDatabaseName(databaseName);
        container.withUsername("test");
        container.withPassword("test");
        if (command.length > 0) {
            container.withCommand(command);
        }
        return container;
    }
}
