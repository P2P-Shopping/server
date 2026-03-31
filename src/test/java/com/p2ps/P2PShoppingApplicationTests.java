package com.p2ps;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration,org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration,org.springframework.boot.autoconfigure.data.mongo.MongoRepositoriesAutoConfiguration"
})
class P2PShoppingApplicationTests {

    @Test
    void contextLoads() {
    }

    @Test
    void shouldStartApplicationMainWithoutThrowing() {
        assertDoesNotThrow(() -> P2PShoppingApplication.main(new String[]{"--spring.main.web-application-type=none"}));
    }

}
