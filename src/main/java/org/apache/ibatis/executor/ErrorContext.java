/**
 *    Copyright 2009-2018 the original author or authors.
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

/**
 * @author Clinton Begin
 *
 * 用于记录本次执行过程中相关上下文信息，当发生错误时，便可在此类上获取相关的上下文信息，易于排错
 *
 * 执行上下文信息的收集独立出来并集中到一处的做法非常值得借鉴和学习。
 */
public class ErrorContext {

  /**
   * 获取换行符
   */
  private static final String LINE_SEPARATOR = System.getProperty("line.separator","\n");

  /**
   * 本地局部线程变量来管理ErrorContext
   */
  private static final ThreadLocal<ErrorContext> LOCAL = new ThreadLocal<>();

  private ErrorContext stored;

  /**
   * 存储异常存在于哪个资源文件中
   * ps: ### The error may exist in mapper/AuthorMapper.xml
   */
  private String resource;

  /**
   * 存储异常是做什么操作时发生的
   * ps: ### The error occurred while setting parameters
   */
  private String activity;

  /**
   * 存储哪个对象操作时发生异常。
   * ps: ### The error may involve defaultParameterMap
   */
  private String object;


  /**
   * 存储异常的概览信息。
   * ps: ### Error querying database. Cause: java.sql.SQLSyntaxErrorException: Unknown column 'id2' in 'field list'
   */
  private String message;

  /**
   * 存储发生日常的 SQL 语句。
   * ps： ### SQL: select id2, name, sex, phone from author where name = ?
   */
  private String sql;

  /**
   * 存储详细的 Java 异常日志。
   * ps: ### Cause: java.sql.SQLSyntaxErrorException: Unknown column 'id2' in 'field list' at
   * org.apache.ibatis.exceptions.ExceptionFactory.wrapException(ExceptionFactory.java:30) at
   * org.apache.ibatis.session.defaults.DefaultSqlSession.selectList(DefaultSqlSession.java:150) at
   * org.apache.ibatis.session.defaults.DefaultSqlSession.selectList(DefaultSqlSession.java:141) at
   * org.apache.ibatis.binding.MapperMethod.executeForMany(MapperMethod.java:139) at org.apache.ibatis.binding.MapperMethod.execute(MapperMethod.java:76)
   */
  private Throwable cause;

  /**
   * 私有化，不允许外部构建
   */
  private ErrorContext() {
  }

  /**
   * 创建一个执行上下文，并交由TheadLocal进行管理
   * @return
   */
  public static ErrorContext instance() {
    ErrorContext context = LOCAL.get();
    if (context == null) {
      context = new ErrorContext();
      LOCAL.set(context);
    }
    return context;
  }

  public ErrorContext store() {
    ErrorContext newContext = new ErrorContext();
    newContext.stored = this;
    LOCAL.set(newContext);
    return LOCAL.get();
  }

  public ErrorContext recall() {
    if (stored != null) {
      LOCAL.set(stored);
      stored = null;
    }
    return LOCAL.get();
  }

  public ErrorContext resource(String resource) {
    this.resource = resource;
    return this;
  }

  public ErrorContext activity(String activity) {
    this.activity = activity;
    return this;
  }

  public ErrorContext object(String object) {
    this.object = object;
    return this;
  }

  public ErrorContext message(String message) {
    this.message = message;
    return this;
  }

  public ErrorContext sql(String sql) {
    this.sql = sql;
    return this;
  }

  public ErrorContext cause(Throwable cause) {
    this.cause = cause;
    return this;
  }

  /**
   * 执行上下文重置
   * 一定要确保在执行完毕后清空ThreadLocal，避免产生意料之外的问题
   *
   * @return
   */
  public ErrorContext reset() {
    resource = null;
    activity = null;
    object = null;
    message = null;
    sql = null;
    cause = null;
    LOCAL.remove();
    return this;
  }

  /**
   * 异常抛出的消息体内容
   *
   * @author shixiongfei
   * @date 2020-03-19
   * @updateDate 2020-03-19
   * @updatedBy shixiongfei
   * @param
   * @return
   */
  @Override
  public String toString() {
    StringBuilder description = new StringBuilder();

    // message
    if (this.message != null) {
      description.append(LINE_SEPARATOR);
      description.append("### ");
      description.append(this.message);
    }

    // resource
    if (resource != null) {
      description.append(LINE_SEPARATOR);
      description.append("### The error may exist in ");
      description.append(resource);
    }

    // object
    if (object != null) {
      description.append(LINE_SEPARATOR);
      description.append("### The error may involve ");
      description.append(object);
    }

    // activity
    if (activity != null) {
      description.append(LINE_SEPARATOR);
      description.append("### The error occurred while ");
      description.append(activity);
    }

    // activity
    if (sql != null) {
      description.append(LINE_SEPARATOR);
      description.append("### SQL: ");
      description.append(sql.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ').trim());
    }

    // cause
    if (cause != null) {
      description.append(LINE_SEPARATOR);
      description.append("### Cause: ");
      description.append(cause.toString());
    }

    return description.toString();
  }

}
