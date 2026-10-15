package com.a501.service.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;

@Testcontainers
@DataMongoTest
class MongoConnectionIntegrationTest {

    @Container
    @ServiceConnection
    static MongoDBContainer mongodb = new MongoDBContainer("mongo:8.0.30-noble");

    @Autowired
    private MongoTemplate mongoTemplate;

    @Test
    @DisplayName("MongoDB에 문서를 저장하고 다시 읽는다")
    void saveThenFind() {
        Document saved = mongoTemplate.getCollection("connection_test")
                .findOneAndReplace(new Document("key", "ready"), new Document("key", "ready"));
        if (saved == null) {
            mongoTemplate.getCollection("connection_test").insertOne(new Document("key", "ready"));
        }

        Document found = mongoTemplate.getCollection("connection_test")
                .find(new Document("key", "ready"))
                .first();

        assertThat(found).isNotNull();
        assertThat(found.getString("key")).isEqualTo("ready");
    }
}
