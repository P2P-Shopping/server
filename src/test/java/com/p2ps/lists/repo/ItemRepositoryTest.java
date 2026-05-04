package com.p2ps.lists.repo;

import com.p2ps.auth.model.Users;
import com.p2ps.catalog.model.ProductCatalog;
import com.p2ps.lists.model.Item;
import com.p2ps.lists.model.ShoppingList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration,org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration",
        "telemetry.api.key=test-telemetry-key-for-tests",
        "app.scheduling.enabled=false",
        "ai.api.key=test-key",
        "ai.api.url=https://api.test.com"
})
@Transactional
public class ItemRepositoryTest {

    static DockerImageName postgisImage = DockerImageName.parse("postgis/postgis:16-3.4")
            .asCompatibleSubstituteFor("postgres");

    @Container
    static PostgreSQLContainer<?> postgresContainer = new PostgreSQLContainer<>(postgisImage)
            .withDatabaseName("testdb")
            .withUsername("testuser")
            .withPassword("testpass");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgresContainer::getJdbcUrl);
        registry.add("spring.datasource.username", postgresContainer::getUsername);
        registry.add("spring.datasource.password", postgresContainer::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
    }

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    private Users user;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS pg_trgm");
        jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS postgis");

        user = new Users("test@user.com", "pass", "Test", "User");
        entityManager.persist(user);
        entityManager.flush();

        ShoppingList list1 = new ShoppingList("List 1", user);
        entityManager.persist(list1);

        ShoppingList list2 = new ShoppingList("List 2", user);
        entityManager.persist(list2);

        ProductCatalog ouaCatalog = new ProductCatalog();
        ouaCatalog.setGenericName("oua");
        ouaCatalog.setSpecificName("Oua de gaina M");
        ouaCatalog.setBrand("Ferma");
        ouaCatalog.setCategory("Lactate");
        entityManager.persist(ouaCatalog);

        Item item1 = new Item("oua proaspete", list1);
        item1.setCatalogItem(ouaCatalog);
        entityManager.persist(item1);

        Item item2 = new Item("lapte", list1);
        entityManager.persist(item2);

        Item item3 = new Item("paine", list2);
        entityManager.persist(item3);
        
        entityManager.flush();
    }

    @Test
    void findUserProductHistoryMatches_fuzzyMatchOnItemName_returnsMatch() {
        // Search for "ou", which should match "oua proaspete"
        List<ItemRepository.UserProductHistoryMatch> results = itemRepository.findUserProductHistoryMatches(user.getId(), "ou");

        assertThat(results).hasSize(1);
        ItemRepository.UserProductHistoryMatch match = results.get(0);
        assertThat(match.getItemName()).isEqualTo("oua proaspete");
        assertThat(match.getCatalogGenericName()).isEqualTo("oua");
        assertThat(match.getCatalogSpecificName()).isEqualTo("Oua de gaina M");
    }

    @Test
    void findUserProductHistoryMatches_exactMatch_returnsMatch() {
        List<ItemRepository.UserProductHistoryMatch> results = itemRepository.findUserProductHistoryMatches(user.getId(), "lapte");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getItemName()).isEqualTo("lapte");
    }

    @Test
    void findUserProductHistoryMatches_noMatch_returnsEmptyList() {
        List<ItemRepository.UserProductHistoryMatch> results = itemRepository.findUserProductHistoryMatches(user.getId(), "branza");

        assertThat(results).isEmpty();
    }

    @Test
    void findUserProductHistoryMatches_forDifferentUser_returnsEmptyList() {
        Users otherUser = new Users("other@user.com", "pass", "Other", "User");
        entityManager.persist(otherUser);
        entityManager.flush();

        List<ItemRepository.UserProductHistoryMatch> results = itemRepository.findUserProductHistoryMatches(otherUser.getId(), "ou");

        assertThat(results).isEmpty();
    }
}
