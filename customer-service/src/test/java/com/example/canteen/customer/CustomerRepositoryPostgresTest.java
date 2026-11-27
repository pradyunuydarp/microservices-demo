package com.example.canteen.customer;

import com.example.canteen.customer.domain.Customer;
import com.example.canteen.customer.repository.CustomerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration test: CustomerRepository against a real PostgreSQL 17, the database we run in
 * Docker Compose. The other tests use H2, which is not PostgreSQL. This one checks that our
 * mapping, the unique email column and the case-insensitive query work on the real thing.
 *
 * Testcontainers starts PostgreSQL in a throwaway container. Without Docker the test is
 * skipped, not failed, so ./mvnw verify still works on a laptop without Docker.
 */
// The property hides Hibernate's SQL warnings: the duplicate email below is expected, and so
// is its "constraint ... does not exist, skipping" note when it creates the tables.
@DataJpaTest(properties = "logging.level.org.hibernate.orm.jdbc=off")
@Testcontainers(disabledWithoutDocker = true)
class CustomerRepositoryPostgresTest {

    // Setup (once for the class): start PostgreSQL before the first test. @ServiceConnection
    // points Spring's DataSource at it. Teardown: Testcontainers removes it after the last test.
    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    CustomerRepository repository;

    @Autowired
    DataSource dataSource;

    @Test
    void findsEmailIgnoringCaseOnPostgres() throws SQLException {
        // Setup: one customer
        repository.save(new Customer("Asha", "asha@campus.edu"));

        // Execute: the query Spring Data builds from the method name
        boolean exists = repository.existsByEmailIgnoreCase("ASHA@Campus.EDU");

        // Verify: it worked, and on PostgreSQL, not on H2
        assertThat(exists).isTrue();
        try (Connection connection = dataSource.getConnection()) {
            assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("PostgreSQL");
        }
        // Teardown: @DataJpaTest rolls back each test's transaction, so the next test starts empty.
    }

    @Test
    void databaseRejectsADuplicateEmail() {
        repository.saveAndFlush(new Customer("Asha", "asha@campus.edu"));

        // The service checks for duplicates first, but the unique column is the real guard
        // (two requests at the same time could both pass the check).
        assertThatThrownBy(() -> repository.saveAndFlush(new Customer("Asha again", "asha@campus.edu")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
