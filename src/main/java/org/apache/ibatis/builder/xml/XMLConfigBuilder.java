/**
 * Copyright 2009-2019 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.ibatis.builder.xml;

import java.io.InputStream;
import java.io.Reader;
import java.util.Properties;
import javax.sql.DataSource;

import org.apache.ibatis.builder.BaseBuilder;
import org.apache.ibatis.builder.BuilderException;
import org.apache.ibatis.datasource.DataSourceFactory;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.executor.loader.ProxyFactory;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.io.VFS;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.mapping.DatabaseIdProvider;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.parsing.XPathParser;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.reflection.DefaultReflectorFactory;
import org.apache.ibatis.reflection.MetaClass;
import org.apache.ibatis.reflection.ReflectorFactory;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.reflection.wrapper.ObjectWrapperFactory;
import org.apache.ibatis.session.AutoMappingBehavior;
import org.apache.ibatis.session.AutoMappingUnknownColumnBehavior;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.LocalCacheScope;
import org.apache.ibatis.transaction.TransactionFactory;
import org.apache.ibatis.type.JdbcType;

/**
 * MapperConfig.xml配置文件解析构建器，采用建造者模式
 *
 * @author Clinton Begin
 * @author Kazuki Shimizu
 */
public class XMLConfigBuilder extends BaseBuilder {

  /**
   * xml配置文件是否已解析，因为默认只会解析一次, 默认为false
   */
  private boolean parsed;
  private final XPathParser parser;
  private String environment;
  /**
   * 本地反射工厂
   */
  private final ReflectorFactory localReflectorFactory = new DefaultReflectorFactory();

  public XMLConfigBuilder(Reader reader) {
    this(reader, null, null);
  }

  public XMLConfigBuilder(Reader reader, String environment) {
    this(reader, environment, null);
  }

  public XMLConfigBuilder(Reader reader, String environment, Properties props) {
    this(new XPathParser(reader, true, props, new XMLMapperEntityResolver()), environment, props);
  }

  public XMLConfigBuilder(InputStream inputStream) {
    this(inputStream, null, null);
  }

  public XMLConfigBuilder(InputStream inputStream, String environment) {
    this(inputStream, environment, null);
  }

  public XMLConfigBuilder(InputStream inputStream, String environment, Properties props) {
    this(new XPathParser(inputStream, true, props, new XMLMapperEntityResolver()), environment, props);
  }

  private XMLConfigBuilder(XPathParser parser, String environment, Properties props) {
    super(new Configuration());
    ErrorContext.instance().resource("SQL Mapper Configuration");
    this.configuration.setVariables(props);
    this.parsed = false;
    this.environment = environment;
    this.parser = parser;
  }

  /**
   * 解析xml配置文件，仅执行一次
   *
   * @author shixiongfei
   * @date 2020-03-18
   * @updateDate 2020-03-18
   * @updatedBy shixiongfei
   * @param
   * @return
   */
  public Configuration parse() {
    if (parsed) {
      throw new BuilderException("Each XMLConfigBuilder can only be used once.");
    }
    // 第一次执行后，设为true.下次不会再次执行
    parsed = true;
    // 从根节点<configuration>开始解析
    parseConfiguration(parser.evalNode("/configuration"));
    return configuration;
  }

  /**
   * 解析Configuration
   *
   * 以下解析顺序必须保持一致，不然解析会失败，服务无法启动
   *
   * @author shixiongfei
   * @date 2020-03-18
   * @updateDate 2020-03-18
   * @updatedBy shixiongfei
   * @param
   * @return
   */
  private void parseConfiguration(XNode root) {
    try {
      //issue #117 read properties first
      // 获取spi属性配置
      propertiesElement(root.evalNode("properties"));
      // 1.获取全局配置
      Properties settings = settingsAsProperties(root.evalNode("settings"));
      loadCustomVfs(settings);
      loadCustomLogImpl(settings);
      // 2.获取类型别名
      typeAliasesElement(root.evalNode("typeAliases"));
      // 3.获取插件，比如自定义的拦截器或者分页拦截器
      pluginElement(root.evalNode("plugins"));
      // 4.获取对象工厂
      objectFactoryElement(root.evalNode("objectFactory"));
      // 5.对象包装工厂
      objectWrapperFactoryElement(root.evalNode("objectWrapperFactory"));
      // 反射工厂
      reflectorFactoryElement(root.evalNode("reflectorFactory"));
      settingsElement(settings);
      // read it after objectFactory and objectWrapperFactory issue #631
      // 环境变量
      environmentsElement(root.evalNode("environments"));
      // 数据库连接配置
      databaseIdProviderElement(root.evalNode("databaseIdProvider"));
      // 获取自定义类型转换处理器
      typeHandlerElement(root.evalNode("typeHandlers"));
      // 映射mapper xml文件
      mapperElement(root.evalNode("mappers"));
    } catch (Exception e) {
      throw new BuilderException("Error parsing SQL Mapper Configuration. Cause: " + e, e);
    }
  }

  private Properties settingsAsProperties(XNode context) {
    if (context == null) {
      return new Properties();
    }
    Properties props = context.getChildrenAsProperties();
    // Check that all settings are known to the configuration class
    MetaClass metaConfig = MetaClass.forClass(Configuration.class, localReflectorFactory);
    for (Object key : props.keySet()) {
      if (!metaConfig.hasSetter(String.valueOf(key))) {
        throw new BuilderException("The setting " + key + " is not known.  Make sure you spelled it correctly (case sensitive).");
      }
    }
    return props;
  }

  private void loadCustomVfs(Properties props) throws ClassNotFoundException {
    String value = props.getProperty("vfsImpl");
    if (value != null) {
      String[] clazzes = value.split(",");
      for (String clazz : clazzes) {
        if (!clazz.isEmpty()) {
          @SuppressWarnings("unchecked")
          Class<? extends VFS> vfsImpl = (Class<? extends VFS>) Resources.classForName(clazz);
          configuration.setVfsImpl(vfsImpl);
        }
      }
    }
  }

  private void loadCustomLogImpl(Properties props) {
    Class<? extends Log> logImpl = resolveClass(props.getProperty("logImpl"));
    configuration.setLogImpl(logImpl);
  }

  private void typeAliasesElement(XNode parent) {
    if (parent != null) {
      for (XNode child : parent.getChildren()) {
        if ("package".equals(child.getName())) {
          String typeAliasPackage = child.getStringAttribute("name");
          configuration.getTypeAliasRegistry().registerAliases(typeAliasPackage);
        } else {
          String alias = child.getStringAttribute("alias");
          String type = child.getStringAttribute("type");
          try {
            Class<?> clazz = Resources.classForName(type);
            if (alias == null) {
              typeAliasRegistry.registerAlias(clazz);
            } else {
              typeAliasRegistry.registerAlias(alias, clazz);
            }
          } catch (ClassNotFoundException e) {
            throw new BuilderException("Error registering typeAlias for '" + alias + "'. Cause: " + e, e);
          }
        }
      }
    }
  }

  /**
   *
   * 3.插件
   * MyBatis 允许你在某一点拦截已映射语句执行的调用。默认情况下,MyBatis 允许使用插件来拦截方法调用
   * <plugins>
   *   <plugin interceptor="org.mybatis.example.ExamplePlugin">
   *     <property name="someProperty" value="100"/>
   *   </plugin>
   * </plugins>
   * @author shixiongfei
   * @date 2020-03-18
   * @updateDate 2020-03-18
   * @updatedBy shixiongfei
   * @param
   * @return
   */
  private void pluginElement(XNode parent) throws Exception {
    if (parent != null) {
      for (XNode child : parent.getChildren()) {
        String interceptor = child.getStringAttribute("interceptor");
        Properties properties = child.getChildrenAsProperties();
        Interceptor interceptorInstance = (Interceptor) resolveClass(interceptor).newInstance();
        // 添加必要的属性值
        interceptorInstance.setProperties(properties);
        // 添加到拦截链集合中
        configuration.addInterceptor(interceptorInstance);
      }
    }
  }

  /**
   * 解析包装对象工厂
   * 自定义对象创建的方式
   * <objectFactory type="org.mybatis.example.ExampleObjectFactory">
   *   <property name="someProperty" value="100"/>
   * </objectFactory>
   *
   * @author shixiongfei
   * @date 2020-03-18
   * @updateDate 2020-03-18
   * @updatedBy shixiongfei
   * @param
   * @return
   */
  private void objectFactoryElement(XNode context) throws Exception {
    if (context != null) {
      String type = context.getStringAttribute("type");
      Properties properties = context.getChildrenAsProperties();
      ObjectFactory factory = (ObjectFactory) resolveClass(type).newInstance();
      factory.setProperties(properties);
      configuration.setObjectFactory(factory);
    }
  }

  /**
   * 解析对象包装工厂
   *
   * // TODO  对对象进行包装？
   * @author shixiongfei
   * @date 2020-03-18
   * @updateDate 2020-03-18
   * @updatedBy shixiongfei
   * @param
   * @return
   */
  private void objectWrapperFactoryElement(XNode context) throws Exception {
    if (context != null) {
      String type = context.getStringAttribute("type");
      ObjectWrapperFactory factory = (ObjectWrapperFactory) resolveClass(type).newInstance();
      configuration.setObjectWrapperFactory(factory);
    }
  }

  /**
   * 解析反射工厂
   *
   * @author shixiongfei
   * @date 2020-03-18
   * @updateDate 2020-03-18
   * @updatedBy shixiongfei
   * @param
   * @return
   */
  private void reflectorFactoryElement(XNode context) throws Exception {
    if (context != null) {
      String type = context.getStringAttribute("type");
      ReflectorFactory factory = (ReflectorFactory) resolveClass(type).newInstance();
      configuration.setReflectorFactory(factory);
    }
  }

  /**
   * xml首个解析 <properties resource="org/apache/ibatis/databases/blog/blog-derby.properties"/> 标签
   * // 比如说数据库的用户名和密码给开发者展示的时候需要base64加密，但是实际运行的时候解密，可采用此来处理
   *  //<properties resource="org/mybatis/example/config.properties">
   *   //    <property name="username" value="dev_user"/>
   *   //    <property name="password" value="F2Fa3!33TYyg"/>
   *   //</properties>
   * @param context
   * @throws Exception
   */
  private void propertiesElement(XNode context) throws Exception {
    if (context != null) {
      Properties defaults = context.getChildrenAsProperties();
      // 获取resource
      String resource = context.getStringAttribute("resource");
      String url = context.getStringAttribute("url");
      if (resource != null && url != null) {
        throw new BuilderException("The properties element cannot specify both a URL and a resource based property file reference.  Please specify one or the other.");
      }
      if (resource != null) {
        defaults.putAll(Resources.getResourceAsProperties(resource));
      } else if (url != null) {
        defaults.putAll(Resources.getUrlAsProperties(url));
      }
      Properties vars = configuration.getVariables();
      if (vars != null) {
        defaults.putAll(vars);
      }
      parser.setVariables(defaults);
      // 将解析的属性信息填充到Configuration的Properties对象中，后面操作可随时从此取出
      configuration.setVariables(defaults);
    }
  }

  /**
   * mybatis全局设置
   *   这些是极其重要的调整, 它们会修改 MyBatis 在运行时的行为方式
   * <settings>
   *   <setting name="cacheEnabled" value="true"/>
   *   <setting name="lazyLoadingEnabled" value="true"/>
   *   <setting name="multipleResultSetsEnabled" value="true"/>
   *   <setting name="useColumnLabel" value="true"/>
   *   <setting name="useGeneratedKeys" value="false"/>
   *   <setting name="enhancementEnabled" value="false"/>
   *   <setting name="defaultExecutorType" value="SIMPLE"/>
   *   <setting name="defaultStatementTimeout" value="25000"/>
   *   <setting name="safeRowBoundsEnabled" value="false"/>
   *   <setting name="mapUnderscoreToCamelCase" value="false"/>
   *   <setting name="localCacheScope" value="SESSION"/>
   *   <setting name="jdbcTypeForNull" value="OTHER"/>
   *   <setting name="lazyLoadTriggerMethods" value="equals,clone,hashCode,toString"/>
   * </settings>
   *
   * @author shixiongfei
   * @date 2020-03-18
   * @updateDate 2020-03-18
   * @updatedBy shixiongfei
   * @param
   * @return
   */
  private void settingsElement(Properties props) {
    // 指定 MyBatis 应如何自动映射列到字段或属性。 NONE 表示关闭自动映射；PARTIAL 只会自动映射没有定义嵌套结果映射（也就是<ResultMap>）的字段。 FULL 会自动映射任何复杂的结果集（无论是否嵌套）。
    configuration.setAutoMappingBehavior(AutoMappingBehavior.valueOf(props.getProperty("autoMappingBehavior", "PARTIAL")));
    // 指定发现自动映射目标未知列（或未知属性类型）的行为。
    // NONE: 不做任何反应
    // WARNING: 输出警告日志（'org.apache.ibatis.session.AutoMappingUnknownColumnBehavior' 的日志等级必须设置为 WARN）
    // FAILING: 映射失败 (抛出 SqlSessionException)
    configuration.setAutoMappingUnknownColumnBehavior(AutoMappingUnknownColumnBehavior.valueOf(props.getProperty("autoMappingUnknownColumnBehavior", "NONE")));
    // 全局性地开启或关闭所有映射器配置文件中已配置的任何缓存
    configuration.setCacheEnabled(booleanValueOf(props.getProperty("cacheEnabled"), true));
    //proxyFactory (CGLIB | JAVASSIST)
    //延迟加载的核心技术就是用代理模式，CGLIB/JAVASSIST两者选一
    configuration.setProxyFactory((ProxyFactory) createInstance(props.getProperty("proxyFactory")));
    // 延迟加载的全局开关。当开启时，所有关联对象都会延迟加载。 特定关联关系中可通过设置 fetchType 属性来覆盖该项的开关状态。
    configuration.setLazyLoadingEnabled(booleanValueOf(props.getProperty("lazyLoadingEnabled"), false));
    // 开启时，任一方法的调用都会加载该对象的所有延迟加载属性。 否则，每个延迟加载属性会按需加载（参考 lazyLoadTriggerMethods),默认为false
    configuration.setAggressiveLazyLoading(booleanValueOf(props.getProperty("aggressiveLazyLoading"), false));
    // 是否允许单个语句返回多结果集（需要数据库驱动支持） 这个一般都必须是允许的，比如说查询分页数据
    configuration.setMultipleResultSetsEnabled(booleanValueOf(props.getProperty("multipleResultSetsEnabled"), true));
    // 使用列标签代替列名。实际表现依赖于数据库驱动，具体可参考数据库驱动的相关文档，或通过对比测试来观察。
    configuration.setUseColumnLabel(booleanValueOf(props.getProperty("useColumnLabel"), true));
    // 允许 JDBC 支持自动生成主键，需要数据库驱动支持。如果设置为 true，将强制使用自动生成主键。尽管一些数据库驱动不支持此特性，但仍可正常工作（如 Derby）。
    configuration.setUseGeneratedKeys(booleanValueOf(props.getProperty("useGeneratedKeys"), false));
    // 配置默认的执行器。
    // SIMPLE 就是普通的执行器；
    // REUSE 执行器会重用预处理语句（PreparedStatement）；
    // BATCH 执行器不仅重用语句还会执行批量更新
    configuration.setDefaultExecutorType(ExecutorType.valueOf(props.getProperty("defaultExecutorType", "SIMPLE")));
    // 设置超时时间，它决定数据库驱动等待数据库响应的秒数。
    configuration.setDefaultStatementTimeout(integerValueOf(props.getProperty("defaultStatementTimeout"), null));
    // 为驱动的结果集获取数量（fetchSize）设置一个建议值。此参数只可以在查询设置中被覆盖
    configuration.setDefaultFetchSize(integerValueOf(props.getProperty("defaultFetchSize"), null));
    //是否将DB字段自动映射到驼峰式Java属性（A_COLUMN-->aColumn）
    configuration.setMapUnderscoreToCamelCase(booleanValueOf(props.getProperty("mapUnderscoreToCamelCase"), false));
    //嵌套语句上使用RowBounds 默认为false
    configuration.setSafeRowBoundsEnabled(booleanValueOf(props.getProperty("safeRowBoundsEnabled"), false));
    // 默认用session级别的缓存
    configuration.setLocalCacheScope(LocalCacheScope.valueOf(props.getProperty("localCacheScope", "SESSION")));
    // 当没有为参数指定特定的 JDBC 类型时，空值的默认 JDBC 类型。
    // 某些数据库驱动需要指定列的 JDBC 类型，多数情况直接用一般类型即可，
    // 比如 NULL、VARCHAR 或 OTHER。
    configuration.setJdbcTypeForNull(JdbcType.valueOf(props.getProperty("jdbcTypeForNull", "OTHER")));
    // 指定对象的哪些方法触发一次延迟加载。默认有equals,clone,hashCode,toString，这里后面再解析用到了，号分隔的方法
    configuration.setLazyLoadTriggerMethods(stringSetValueOf(props.getProperty("lazyLoadTriggerMethods"), "equals,clone,hashCode,toString"));
    // 是否允许在嵌套语句中使用结果处理器（ResultHandler）。如果允许使用则设置为 false。
    configuration.setSafeResultHandlerEnabled(booleanValueOf(props.getProperty("safeResultHandlerEnabled"), true));
    // 配置动态SQL生成语言所使用的脚本语言
    configuration.setDefaultScriptingLanguage(resolveClass(props.getProperty("defaultScriptingLanguage")));
    // 配置默认的枚举类型处理器
    configuration.setDefaultEnumTypeHandler(resolveClass(props.getProperty("defaultEnumTypeHandler")));
    // 指定当结果集中值为 null 的时候是否调用映射对象的 setter（map 对象时为 put）方法，
    // 这在依赖于 Map.keySet() 或 null 值进行初始化时比较有用。
    // 注意基本类型（int、boolean 等）是不能设置成 null 的。默认未开启
    configuration.setCallSettersOnNulls(booleanValueOf(props.getProperty("callSettersOnNulls"), false));
    // 允许使用方法签名中的名称作为语句参数名称。
    // 为了使用该特性，你的项目必须采用 Java 8 编译，并且加上 -parameters 选项。
    // （新增于 3.4.1
    configuration.setUseActualParamName(booleanValueOf(props.getProperty("useActualParamName"), true));
    // 当返回行的所有列都是空时，MyBatis默认返回 null。
    // 当开启这个设置时，MyBatis会返回一个空实例。
    // 请注意，它也适用于嵌套的结果集（如集合或关联）。（新增于 3.4.2）
    // 默认是不开启，这个在做聚合查询的时候可能会遇到,默认返回null比较好
    configuration.setReturnInstanceForEmptyRow(booleanValueOf(props.getProperty("returnInstanceForEmptyRow"), false));
    // 指定 MyBatis 增加到日志名称的前缀。
    configuration.setLogPrefix(props.getProperty("logPrefix"));
    // 指定一个提供 Configuration 实例的类。
    // 这个被返回的 Configuration 实例用来加载被反序列化对象的延迟加载属性值。
    // 这个类必须包含一个签名为static Configuration getConfiguration() 的方法。
    // （新增于 3.2.3）
    configuration.setConfigurationFactory(resolveClass(props.getProperty("configurationFactory")));
  }

  /**
   * 解析环境变量，比如说事务，数据源
   * 尽管可以配置多个环境，但每个 SqlSessionFactory 实例只能选择一种环境。
   * 所以，如果你想连接两个数据库，就需要创建两个 SqlSessionFactory 实例，每个数据库对应一个。
   * 而如果是三个数据库，就需要三个实例，依此类推，记起来很简单：
   * 每个数据库对应一个 SqlSessionFactory 实例
   *
   * @author shixiongfei
   * @date 2020-03-18
   * @updateDate 2020-03-18
   * @updatedBy shixiongfei
   * @param
   * @return
   */
  private void environmentsElement(XNode context) throws Exception {
    if (context != null) {
      if (environment == null) {
        environment = context.getStringAttribute("default");
      }
      for (XNode child : context.getChildren()) {
        String id = child.getStringAttribute("id");
        if (isSpecifiedEnvironment(id)) {
          TransactionFactory txFactory = transactionManagerElement(child.evalNode("transactionManager"));
          DataSourceFactory dsFactory = dataSourceElement(child.evalNode("dataSource"));
          DataSource dataSource = dsFactory.getDataSource();
          Environment.Builder environmentBuilder = new Environment.Builder(id)
            .transactionFactory(txFactory)
            .dataSource(dataSource);
          configuration.setEnvironment(environmentBuilder.build());
        }
      }
    }
  }

  /**
   * 解析数据库厂商标识
   *
   * @author shixiongfei
   * @date 2020-03-18
   * @updateDate 2020-03-18
   * @updatedBy shixiongfei
   * @param
   * @return
   */
  private void databaseIdProviderElement(XNode context) throws Exception {
    DatabaseIdProvider databaseIdProvider = null;
    if (context != null) {
      String type = context.getStringAttribute("type");
      // awful patch to keep backward compatibility
      if ("VENDOR".equals(type)) {
        type = "DB_VENDOR";
      }
      Properties properties = context.getChildrenAsProperties();
      databaseIdProvider = (DatabaseIdProvider) resolveClass(type).newInstance();
      databaseIdProvider.setProperties(properties);
    }
    Environment environment = configuration.getEnvironment();
    if (environment != null && databaseIdProvider != null) {
      String databaseId = databaseIdProvider.getDatabaseId(environment.getDataSource());
      configuration.setDatabaseId(databaseId);
    }
  }

  /**
   * 解析事务工厂
   *
   * @author shixiongfei
   * @date 2020-03-18
   * @updateDate 2020-03-18
   * @updatedBy shixiongfei
   * @param
   * @return
   */
  private TransactionFactory transactionManagerElement(XNode context) throws Exception {
    if (context != null) {
      String type = context.getStringAttribute("type");
      Properties props = context.getChildrenAsProperties();
      TransactionFactory factory = (TransactionFactory) resolveClass(type).newInstance();
      factory.setProperties(props);
      return factory;
    }
    throw new BuilderException("Environment declaration requires a TransactionFactory.");
  }

  /**
   * 解析数据源工厂
   *
   * @author shixiongfei
   * @date 2020-03-18
   * @updateDate 2020-03-18
   * @updatedBy shixiongfei
   * @param 
   * @return 
   */
  private DataSourceFactory dataSourceElement(XNode context) throws Exception {
    if (context != null) {
      String type = context.getStringAttribute("type");
      Properties props = context.getChildrenAsProperties();
      DataSourceFactory factory = (DataSourceFactory) resolveClass(type).newInstance();
      factory.setProperties(props);
      return factory;
    }
    throw new BuilderException("Environment declaration requires a DataSourceFactory.");
  }

  /**
   * 解析类型转换处理器 java属性类型 -> 数据库类型 数据库类型 -> Java属性类型
   *
   * @author shixiongfei
   * @date 2020-03-18
   * @updateDate 2020-03-18
   * @updatedBy shixiongfei
   * @param 
   * @return 
   */
  private void typeHandlerElement(XNode parent) {
    if (parent != null) {
      for (XNode child : parent.getChildren()) {
        if ("package".equals(child.getName())) {
          String typeHandlerPackage = child.getStringAttribute("name");
          typeHandlerRegistry.register(typeHandlerPackage);
        } else {
          String javaTypeName = child.getStringAttribute("javaType");
          String jdbcTypeName = child.getStringAttribute("jdbcType");
          String handlerTypeName = child.getStringAttribute("handler");
          Class<?> javaTypeClass = resolveClass(javaTypeName);
          JdbcType jdbcType = resolveJdbcType(jdbcTypeName);
          Class<?> typeHandlerClass = resolveClass(handlerTypeName);
          if (javaTypeClass != null) {
            if (jdbcType == null) {
              typeHandlerRegistry.register(javaTypeClass, typeHandlerClass);
            } else {
              typeHandlerRegistry.register(javaTypeClass, jdbcType, typeHandlerClass);
            }
          } else {
            typeHandlerRegistry.register(typeHandlerClass);
          }
        }
      }
    }
  }

  /**
   * 解析映射器
   *
   * @author shixiongfei
   * @date 2020-03-18
   * @updateDate 2020-03-18
   * @updatedBy shixiongfei
   * @param 
   * @return 
   */
  private void mapperElement(XNode parent) throws Exception {
    if (parent != null) {
      for (XNode child : parent.getChildren()) {
        // 将包内的映射器接口实现全部注册为映射器
        if ("package".equals(child.getName())) {
          String mapperPackage = child.getStringAttribute("name");
          configuration.addMappers(mapperPackage);
        } else {
          // 使用相对于类路径的资源引用
          String resource = child.getStringAttribute("resource");
          // 使用完全限定资源定位符（URL）
          String url = child.getStringAttribute("url");
          // 使用映射器接口实现类的完全限定类名
          String mapperClass = child.getStringAttribute("class");
          if (resource != null && url == null && mapperClass == null) {
            // 记录执行上下文
            ErrorContext.instance().resource(resource);
            InputStream inputStream = Resources.getResourceAsStream(resource);
            // 具体的xml-Mapper实例构造器，用于解析mapper.xml文件创建XmlMapper对象
            XMLMapperBuilder mapperParser = new XMLMapperBuilder(inputStream, configuration, resource, configuration.getSqlFragments());
            mapperParser.parse();
          } else if (resource == null && url != null && mapperClass == null) {
            ErrorContext.instance().resource(url);
            InputStream inputStream = Resources.getUrlAsStream(url);
            XMLMapperBuilder mapperParser = new XMLMapperBuilder(inputStream, configuration, url, configuration.getSqlFragments());
            mapperParser.parse();
            // 如果配置的mapper映射为接口，则获取指定的mapper class类对象
          } else if (resource == null && url == null && mapperClass != null) {
            Class<?> mapperInterface = Resources.classForName(mapperClass);
            // 将mapper接口类对象添加到mapperRegistry中, 内部是通过mapperRegistry.addMapper(class)来创建MapperAnnotationBuilder，进行注解解析
            configuration.addMapper(mapperInterface);
          } else {
            throw new BuilderException("A mapper element may only specify a url, resource or class, but not more than one.");
          }
        }
      }
    }
  }

  private boolean isSpecifiedEnvironment(String id) {
    if (environment == null) {
      throw new BuilderException("No environment specified.");
    } else if (id == null) {
      throw new BuilderException("Environment requires an id attribute.");
    } else if (environment.equals(id)) {
      return true;
    }
    return false;
  }

}
