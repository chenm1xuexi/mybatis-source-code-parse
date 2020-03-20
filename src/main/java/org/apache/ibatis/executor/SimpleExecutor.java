/**
 *    Copyright 2009-2019 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.executor;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collections;
import java.util.List;

import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.transaction.Transaction;

/**
 * @author Clinton Begin
 */
public class SimpleExecutor extends BaseExecutor {

  public SimpleExecutor(Configuration configuration, Transaction transaction) {
    super(configuration, transaction);
  }

  @Override
  public int doUpdate(MappedStatement ms, Object parameter) throws SQLException {
    Statement stmt = null;
    try {
      Configuration configuration = ms.getConfiguration();
      StatementHandler handler = configuration.newStatementHandler(this, ms, parameter, RowBounds.DEFAULT, null, null);
      stmt = prepareStatement(handler, ms.getStatementLog());
      return handler.update(stmt);
    } finally {
      closeStatement(stmt);
    }
  }

  @Override
  public <E> List<E> doQuery(MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql) throws SQLException {
    Statement stmt = null;
    try {
      Configuration configuration = ms.getConfiguration();
      // executor在此处构造语句处理器，返回的是RoutingStatementHandler, RoutingStatementHandler中持有子类的语句处理器，一般为PrepareStatementHandler
      StatementHandler handler = configuration.newStatementHandler(wrapper, ms, parameter, rowBounds, resultHandler, boundSql);
      // 预处理语句，获取jdbc的数据库操作对象，一般为PrepareStatement
      // ms.getStatementLog()就是我们指定的日志对象，我一般指定为StdOutImpl,在控制台打印sql
      // 这里做的操作还包含获取数据库的连接对象
      // 这里对参数也进行了填充，也就是说现在是一个完整的sql了，接下来就是进行sql执行的操作了
      stmt = prepareStatement(handler, ms.getStatementLog());
      // 实际的查询交由语句处理器进行执行
      return handler.query(stmt, resultHandler);
    } finally {
      // 语句执行完毕后，关闭io流
      closeStatement(stmt);
    }
  }

  @Override
  protected <E> Cursor<E> doQueryCursor(MappedStatement ms, Object parameter, RowBounds rowBounds, BoundSql boundSql) throws SQLException {
    Configuration configuration = ms.getConfiguration();
    StatementHandler handler = configuration.newStatementHandler(wrapper, ms, parameter, rowBounds, null, boundSql);
    Statement stmt = prepareStatement(handler, ms.getStatementLog());
    stmt.closeOnCompletion();
    return handler.queryCursor(stmt);
  }

  @Override
  public List<BatchResult> doFlushStatements(boolean isRollback) {
    return Collections.emptyList();
  }

  /**
   * 语句处理的准备节点
   *
   * @author shixiongfei
   * @date 2020-03-20
   * @updateDate 2020-03-20
   * @updatedBy shixiongfei
   * @param
   * @return
   */
  private Statement prepareStatement(StatementHandler handler, Log statementLog) throws SQLException {
    Statement stmt;
    // 获取数据库的连接对象，这个连接对象实际是日志连接动态代理对象，方便执行语句过程中进行sqk日志记录
    Connection connection = getConnection(statementLog);

    // 交由具体的语句处理器获取数据库sql操作对象
    stmt = handler.prepare(connection, transaction.getTimeout());
    // 语句处理器的参数配置，PrepareStatementHandler就是调用参数处理器进行参数配置
    handler.parameterize(stmt);
    return stmt;
  }

}
