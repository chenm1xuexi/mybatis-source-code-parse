package org.apache.ibatis.feifei;

import com.zaxxer.hikari.HikariDataSource;
import org.apache.ibatis.logging.log4j2.Log4j2Impl;
import org.apache.ibatis.logging.stdout.StdOutImpl;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.TransactionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.Before;
import org.junit.Test;

import javax.transaction.Transactional;
import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * @author shixiongfei
 * @date 2020-03-19
 * @since
 */
public class SqlSessionTest {

  public static SqlSessionFactory sqlSessionFactory;

  @Before
  public void setUp() throws IOException, SQLException {

    TransactionFactory transactionFactory = new JdbcTransactionFactory();

    HikariDataSource dataSource = new HikariDataSource();
    dataSource.setUsername("root");
    dataSource.setPassword("123456");
    dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
    dataSource.setJdbcUrl("jdbc:mysql://localhost:3306/test_user?serverTimezone=UTC&useUnicode=true&characterEncoding=utf-8&useSSL=true");

    Environment environment = new Environment("development", transactionFactory, dataSource);

    Configuration configuration = new Configuration(environment);
    configuration.setLogImpl(StdOutImpl.class);
    configuration.setLazyLoadingEnabled(true);
    configuration.getTypeAliasRegistry().registerAlias(Name.class);
    configuration.addMapper(NameMapper.class);
    configuration.setCacheEnabled(false);

    SqlSessionFactoryBuilder builder = new SqlSessionFactoryBuilder();
    sqlSessionFactory = builder.build(configuration);
  }

  @Transactional
  @Test
  public void selectOne1() {
    final SqlSession sqlSession = sqlSessionFactory.openSession(ExecutorType.SIMPLE);
    String namespace = "org.apache.ibatis.feifei.NameMapper.getById1";

    // 1
    Name name = sqlSession.selectOne(namespace, 1L);
    Name name1 = sqlSession.selectOne(namespace, 2L);
    System.out.println("name = " + name);
    System.out.println("name = " + name1);
  }

  @Transactional
  @Test
  public void selectOne() {
    final SqlSession sqlSession = sqlSessionFactory.openSession(ExecutorType.SIMPLE);
    String namespace = "org.apache.ibatis.feifei.NameMapper.getById1";
    String namespace1 = "org.apache.ibatis.feifei.NameMapper.updateName";

    // 1
    Name name = sqlSession.selectOne(namespace, 1L);
    System.out.println("name = " + name);

    // 2
  //  final SqlSession sqlSession1 = sqlSessionFactory.openSession(ExecutorType.SIMPLE);
//    Name name1 = sqlSession.selectOne(namespace, 1L);
//    System.out.println("name1 = " + name1);

    // 3
//    Map<String, Object> params = new HashMap<>();
//    params.put("id", 1L);
//    params.put("name", "hehe");
//    sqlSession1.update(namespace1, params);
//    System.out.println("name = " + params);
//
//    // 4
//    sqlSession.selectOne(namespace, 1L);
//    System.out.println("name = " + name);


//    Map<String, Object> params = new HashMap<>();
//    params.put("id", 1L);
//    params.put("name", "bibi");
//    Name name1 = sqlSession.selectOne("org.apache.ibatis.feifei.NameMapper.getById1", params);
//    System.out.println("name1 = " + name1);


  }
}
