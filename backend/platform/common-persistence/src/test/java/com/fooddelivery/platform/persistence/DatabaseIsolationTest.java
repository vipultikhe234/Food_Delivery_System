package com.fooddelivery.platform.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fooddelivery.platform.testsupport.Containers;
import com.fooddelivery.platform.testsupport.RequiresDocker;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

/**
 * Database-per-service isolation (REQ-PLAT-008 AC1, docs/06 §1): runs the same init script as the
 * local Compose stack and checks what each role can and cannot do.
 */
@RequiresDocker
@Testcontainers
class DatabaseIsolationTest {

  private static final Path INIT_SCRIPT =
      Path.of("../../../infrastructure/docker/postgres/init/10-service-databases.sh");
  private static final String OWNER_PASSWORD = UUID.randomUUID().toString();
  private static final String APP_PASSWORD = UUID.randomUUID().toString();
  private static final String PERMISSION_DENIED = "42501";

  @Container
  static final PostgreSQLContainer DB =
      Containers.postgres()
          .withEnv("LOCAL_DB_OWNER_PASSWORD", OWNER_PASSWORD)
          .withEnv("LOCAL_DB_APP_PASSWORD", APP_PASSWORD)
          .withCopyFileToContainer(
              MountableFile.forHostPath(INIT_SCRIPT, 0755),
              "/docker-entrypoint-initdb.d/10-service-databases.sh")
          .withStartupTimeout(Duration.ofMinutes(3));

  @Test
  void theAppRoleCannotConnectToAnotherServicesDatabase() throws SQLException {
    try (Connection own = connect("identity_db", "identity_app", APP_PASSWORD)) {
      assertThat(own.isValid(2)).isTrue();
    }

    assertDenied(() -> connect("order_db", "identity_app", APP_PASSWORD).close());
  }

  @Test
  void theOwnerRoleCannotConnectToAnotherServicesDatabaseEither() {
    assertDenied(() -> connect("payment_db", "order_owner", OWNER_PASSWORD).close());
  }

  @Test
  void onlyTheOwnerRoleCanChangeTheSchemaAndTheAppRoleGetsDataAccess() throws SQLException {
    try (Connection owner = connect("catalog_db", "catalog_owner", OWNER_PASSWORD);
        Statement ddl = owner.createStatement()) {
      ddl.execute("CREATE TABLE isolation_probe (id int PRIMARY KEY, label text)");
    }

    try (Connection app = connect("catalog_db", "catalog_app", APP_PASSWORD);
        Statement sql = app.createStatement()) {
      sql.executeUpdate("INSERT INTO isolation_probe VALUES (1, 'a')");
      sql.executeUpdate("UPDATE isolation_probe SET label = 'b' WHERE id = 1");
      try (ResultSet rows = sql.executeQuery("SELECT label FROM isolation_probe")) {
        assertThat(rows.next()).isTrue();
        assertThat(rows.getString(1)).isEqualTo("b");
      }
      sql.executeUpdate("DELETE FROM isolation_probe");

      assertDenied(() -> sql.execute("CREATE TABLE app_owned (id int)"));
      assertDenied(() -> sql.execute("DROP TABLE isolation_probe"));
      assertDenied(() -> sql.execute("ALTER TABLE isolation_probe ADD COLUMN extra text"));
    }
  }

  @Test
  void geoDatabasesHavePostgis() throws SQLException {
    try (Connection app = connect("delivery_db", "delivery_app", APP_PASSWORD);
        Statement sql = app.createStatement();
        ResultSet rows = sql.executeQuery("SELECT extname FROM pg_extension")) {
      boolean postgis = false;
      while (rows.next()) {
        postgis |= "postgis".equals(rows.getString(1));
      }
      assertThat(postgis).isTrue();
    }
  }

  private static Connection connect(String database, String user, String password)
      throws SQLException {
    String url =
        "jdbc:postgresql://"
            + DB.getHost()
            + ":"
            + DB.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT)
            + "/"
            + database;
    return DriverManager.getConnection(url, user, password);
  }

  private static void assertDenied(SqlAction action) {
    assertThatThrownBy(action::run)
        .isInstanceOf(SQLException.class)
        .extracting(e -> ((SQLException) e).getSQLState())
        .isEqualTo(PERMISSION_DENIED);
  }

  @FunctionalInterface
  private interface SqlAction {
    void run() throws SQLException;
  }
}
