/*
 * Copyright 2017-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.data.jdbc.mybatis;

import static org.assertj.core.api.Assertions.assertThat;

import junit.framework.AssertionFailedError;

import java.io.IOException;

import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.data.jdbc.core.convert.BasicJdbcConverter;
import org.springframework.data.jdbc.core.convert.JdbcConverter;
import org.springframework.data.jdbc.core.mapping.JdbcMappingContext;
import org.springframework.data.jdbc.repository.config.EnableJdbcRepositories;
import org.springframework.data.jdbc.testing.TestConfiguration;
import org.springframework.data.relational.core.mapping.RelationalMappingContext;
import org.springframework.data.repository.CrudRepository;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.rules.SpringClassRule;
import org.springframework.test.context.junit4.rules.SpringMethodRule;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tests the integration for customizing the namespace with Mybatis.
 *
 * @author Kazuki Shimizu
 * @author Jens Schauder
 * @author Tyler Van Gorder
 */
@ContextConfiguration
@ActiveProfiles("hsql")
@Transactional
public class MyBatisCustomizingNamespaceHsqlIntegrationTests {

	@ClassRule public static final SpringClassRule classRule = new SpringClassRule();
	@Rule public SpringMethodRule methodRule = new SpringMethodRule();

	@Autowired SqlSessionFactory sqlSessionFactory;
	@Autowired DummyEntityRepository repository;

	@Test // DATAJDBC-178
	public void myBatisGetsUsedForInsertAndSelect() {

		DummyEntity entity = new DummyEntity(null, "some name");
		DummyEntity saved = repository.save(entity);

		assertThat(saved.id).isNotNull();

		DummyEntity reloaded = repository.findById(saved.id).orElseThrow(AssertionFailedError::new);

		assertThat(reloaded.name).isEqualTo("name " + saved.id);
	}

	@org.springframework.context.annotation.Configuration
	@Import(TestConfiguration.class)
	@EnableJdbcRepositories(considerNestedRepositories = true)
	static class Config {

		@Bean
		Class<?> testClass() {
			return MyBatisCustomizingNamespaceHsqlIntegrationTests.class;
		}

		@Bean
		SqlSessionFactoryBean createSessionFactory(EmbeddedDatabase db) throws IOException {

			Configuration configuration = new Configuration();
			configuration.getTypeAliasRegistry().registerAlias("MyBatisContext", MyBatisContext.class);
			configuration.getTypeAliasRegistry().registerAlias(DummyEntity.class);

			SqlSessionFactoryBean sqlSessionFactoryBean = new SqlSessionFactoryBean();
			sqlSessionFactoryBean.setDataSource(db);
			sqlSessionFactoryBean.setConfiguration(configuration);
			sqlSessionFactoryBean.setMapperLocations(new PathMatchingResourcePatternResolver()
					.getResources("classpath*:org/springframework/data/jdbc/mybatis/mapper/*Mapper.xml"));

			return sqlSessionFactoryBean;
		}

		@Bean
		SqlSessionTemplate sqlSessionTemplate(SqlSessionFactory factory) {
			return new SqlSessionTemplate(factory);
		}

		@Bean
		@Primary
		MyBatisDataAccessStrategy dataAccessStrategy(SqlSession sqlSession) {

			RelationalMappingContext context = new JdbcMappingContext();
			JdbcConverter converter = new BasicJdbcConverter(context, (Identifier, path) -> null);
		
			MyBatisDataAccessStrategy strategy = new MyBatisDataAccessStrategy(sqlSession, context, converter);

			strategy.setNamespaceStrategy(new NamespaceStrategy() {
				@Override
				public String getNamespace(Class<?> domainType) {
					return domainType.getPackage().getName() + ".mapper." + domainType.getSimpleName() + "Mapper";
				}
			});

			return strategy;
		}
	}

	interface DummyEntityRepository extends CrudRepository<DummyEntity, Long> {}
}
