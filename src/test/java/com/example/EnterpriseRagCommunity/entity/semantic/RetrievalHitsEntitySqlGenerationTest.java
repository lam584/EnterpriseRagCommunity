package com.example.EnterpriseRagCommunity.entity.semantic;

import org.hibernate.dialect.Dialect;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.persister.entity.AbstractEntityPersister;
import org.hibernate.persister.entity.EntityPersister;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import jakarta.persistence.EntityManagerFactory;

@SpringBootTest
class RetrievalHitsEntitySqlGenerationTest {

    @Autowired
    EntityManagerFactory entityManagerFactory;

    @Test
    void insertSql_should_quote_rank_column() {
        SessionFactoryImplementor sfi = entityManagerFactory.unwrap(SessionFactoryImplementor.class);
        Dialect dialect = sfi.getJdbcServices().getDialect();

        EntityPersister persister = sfi.getMappingMetamodel()
                .getEntityDescriptor(RetrievalHitsEntity.class.getName());
        String openQuote = String.valueOf(dialect.openQuote());
        String closeQuote = String.valueOf(dialect.closeQuote());
        String expected = openQuote + "rank" + closeQuote;

        Assertions.assertTrue(persister instanceof AbstractEntityPersister);
        String[] cols = ((AbstractEntityPersister) persister).getPropertyColumnNames("rank");
        Assertions.assertNotNull(cols);
        Assertions.assertTrue(cols.length > 0);
        Assertions.assertTrue(expected.equalsIgnoreCase(cols[0]), "Expected quoted rank column. col=" + cols[0]);
    }
}
