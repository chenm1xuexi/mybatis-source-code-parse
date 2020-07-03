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
package org.apache.ibatis.reflection;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.ReflectPermission;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.ibatis.reflection.invoker.GetFieldInvoker;
import org.apache.ibatis.reflection.invoker.Invoker;
import org.apache.ibatis.reflection.invoker.MethodInvoker;
import org.apache.ibatis.reflection.invoker.SetFieldInvoker;
import org.apache.ibatis.reflection.property.PropertyNamer;

/**
 * This class represents a cached set of class definition information that
 * allows for easy mapping between property names and getter/setter methods.
 *
 * 反射器 用于缓存类的字段名和getter setter方法的元信息
 *
 *
 *
 * @author Clinton Begin
 */
public class Reflector {

  /**
   * 类对象
   */
  private final Class<?> type;

  /**
   * 可读的属性名称数组，也就是存在getter方法
   */
  private final String[] readablePropertyNames;

  /**
   * 可写的属性名称数组，也就是存在setter方法
   */
  private final String[] writablePropertyNames;

  /**
   * setter方法集合
   */
  private final Map<String, Invoker> setMethods = new HashMap<>();

  /**
   * getter方法集合
   */
  private final Map<String, Invoker> getMethods = new HashMap<>();

  /**
   * setter
   */
  private final Map<String, Class<?>> setTypes = new HashMap<>();

  /**
   * getter方法参数返回值集合 key 属性名 value getter方法返回值类型
   */
  private final Map<String, Class<?>> getTypes = new HashMap<>();

  /**
   * 默认的构造器 就是空构造器，不存在则此属性为null
   */
  private Constructor<?> defaultConstructor;

  /**
   * 大小写不敏感属性映射集合 key -> 全大写的属性名称 value -> 真实的属性名称
   */
  private Map<String, String> caseInsensitivePropertyMap = new HashMap<>();

  public Reflector(Class<?> clazz) {
    type = clazz;
    // 缓存无参的构造方法
    addDefaultConstructor(clazz);
    // 记录字段名和get方法、get方法返回值的映射关系
    addGetMethods(clazz);
    // 记录字段名与set方法、get方法返回值的映射关系
    addSetMethods(clazz);
    // 对于没有getter setter方法的字段，这里统一记录，用于后续对字段的值进行修改
    addFields(clazz);
    // 获取所有可读的字段名
    readablePropertyNames = getMethods.keySet().toArray(new String[getMethods.keySet().size()]);
    // 获取所有可写的字段名
    writablePropertyNames = setMethods.keySet().toArray(new String[setMethods.keySet().size()]);
    // 保存一份所有字段名大写与原始字段名的映射
    for (String propName : readablePropertyNames) {
      caseInsensitivePropertyMap.put(propName.toUpperCase(Locale.ENGLISH), propName);
    }
    for (String propName : writablePropertyNames) {
      caseInsensitivePropertyMap.put(propName.toUpperCase(Locale.ENGLISH), propName);
    }
  }

  private void addDefaultConstructor(Class<?> clazz) {
    // 获取所有已声明的构造器数组
    Constructor<?>[] consts = clazz.getDeclaredConstructors();
    // 遍历
    for (Constructor<?> constructor : consts) {
      // 如果存在空构造器(无参)，则将其设置为默认的构造器
      if (constructor.getParameterTypes().length == 0) {
        this.defaultConstructor = constructor;
      }
    }
  }

  /**
   *  添加getter方法的参数类型信息，返回值类型信息，
   *  并保存到getMethods集合中
   *
   * @author shixiongfei
   * @date 2020/7/1 11:40 上午
   * @param cls 类对象
   */
  private void addGetMethods(Class<?> cls) {
    // 创建一个map key -> 字段名 value -> getter方法 Method对象
    Map<String, List<Method>> conflictingGetters = new HashMap<>();
    // 获取当前类对象的所有方法（包括其父类的方法，私有方法也可以获取到）
    Method[] methods = getClassMethods(cls);
    // 对方法对象数组进行遍历
    for (Method method : methods) {
      // 如果方法参数 > 0则直接跳过
      if (method.getParameterTypes().length > 0) {
        continue;
      }
      // 不存在方法参数则获取其方法名
      String name = method.getName();
      // 校验方法名的前缀是否为get 且方法名的长度要大于 3 获取方法前缀是否为is 且长度要大于2
      // 满足此条件才认为是getter方法
      if ((name.startsWith("get") && name.length() > 3)
          || (name.startsWith("is") && name.length() > 2)) {
        // PropertyNamer是一个工具类 这里是通过getter方法来获取字段名称
        name = PropertyNamer.methodToProperty(name);
        // 将getter方法添加到当前方法最开始创建的map集合中
        // 最开始一直在考虑为什么conflictingGetters的value是一个list
        // 当看到 resolveGetterConflicts 方法时
        // 这里是因为存在子类复写父类的getter方法，或者出现重载方法造成getter方法冲突，在
        // 当前方法最后会进行方法冲突去重，保证一个字段只对应一个get方法
        addMethodConflict(conflictingGetters, name, method);
      }
    }

    // 处理getter方法冲突 用于保证每个字段只对应一个get方法
    resolveGetterConflicts(conflictingGetters);
  }

  /**
   * 处理方法冲突
   * 保证每个字段只对应一个get方法
   *
   * @author shixiongfei
   * @date 2020/7/1 11:52 上午
   * @param conflictingGetters getter方法map集合（可能包含重复的getter）需要去重
   */
  private void resolveGetterConflicts(Map<String, List<Method>> conflictingGetters) {
    // 遍历map集合
    for (Entry<String, List<Method>> entry : conflictingGetters.entrySet()) {
      // 定义一个优胜者Method对象
      Method winner = null;
      // 获取字段名
      String propName = entry.getKey();
      // 对当前字段的方法集合进行遍历
      for (Method candidate : entry.getValue()) {
        if (winner == null) {
          // winner为空 则将候选者赋予winner
          winner = candidate;
          continue;
        }
        // 获取winner的返回类型
        Class<?> winnerType = winner.getReturnType();
        // 获取候选者的返回类型
        Class<?> candidateType = candidate.getReturnType();
        // 1. 判断返回类型是否相等
        if (candidateType.equals(winnerType)) {
          // 2. 返回类型相等 单返回类型不是boolean型则抛出反射异常？
          if (!boolean.class.equals(candidateType)) {
            throw new ReflectionException(
                "Illegal overloaded getter method with ambiguous type for property "
                    + propName + " in class " + winner.getDeclaringClass()
                    + ". This breaks the JavaBeans specification and can cause unpredictable results.");
            // 如果候选者开头为is 则将候选者赋值给winner 因为返回值为boolean
            // 这里是因为可能存在getIsValue 和 isValue的情况，优先去is开头的方法
          } else if (candidate.getName().startsWith("is")) {
            winner = candidate;
          }
          // 判断winnerType 是否为candidateType 的子类或自接口，如果是则优先选举winner
        } else if (candidateType.isAssignableFrom(winnerType)) {
          // OK getter type is descendant
        } else if (winnerType.isAssignableFrom(candidateType)) {
          // candidateType winnerType 的子类或自接口，如果是则优先选举candidate
          winner = candidate;
        } else {
          throw new ReflectionException(
              "Illegal overloaded getter method with ambiguous type for property "
                  + propName + " in class " + winner.getDeclaringClass()
                  + ". This breaks the JavaBeans specification and can cause unpredictable results.");
        }
      }

      // 将属性名 + method添加到getMethods map集合中
      addGetMethod(propName, winner);
    }
  }

  /**
   * 添加getter方法到 getMethods 集合中
   *
   * @author shixiongfei
   * @date 2020/7/1 12:40 下午
   * @param name 字段名
   * @param method 方法对象
   */
  private void addGetMethod(String name, Method method) {
    // 校验名称是否合法
    // 过滤$开头、serialVersionUID的get方法和getClass()方法
    if (isValidPropertyName(name)) {
      // 填充getter方法到集合中
      getMethods.put(name, new MethodInvoker(method));
      // 参数类型解析器，将返回返回值解析成runtime时期的类型
      Type returnType = TypeParameterResolver.resolveReturnType(method, type);
      // 字段名-运行时方法的真正返回类型
      getTypes.put(name, typeToClass(returnType));
    }
  }

  private void addSetMethods(Class<?> cls) {
    Map<String, List<Method>> conflictingSetters = new HashMap<>();
    // 获取父子类所有的方法
    Method[] methods = getClassMethods(cls);
    for (Method method : methods) {
      String name = method.getName();
      if (name.startsWith("set") && name.length() > 3) {
        if (method.getParameterTypes().length == 1) {
          name = PropertyNamer.methodToProperty(name);
          addMethodConflict(conflictingSetters, name, method);
        }
      }
    }
    resolveSetterConflicts(conflictingSetters);
  }

  /**
   *  添加方法到map集合中
   *
   * @author shixiongfei
   * @date 2020/7/1 11:48 上午
   * @param conflictingMethods map集合
   * @param name 字段名
   * @param method 方法名称
   */
  private void addMethodConflict(Map<String, List<Method>> conflictingMethods, String name, Method method) {
    // 这里是用到了jdk1.8的新方法，如果获取的value不存在，则创建一个list集合
    List<Method> list = conflictingMethods.computeIfAbsent(name, k -> new ArrayList<>());
    // 将方法填充进去
    list.add(method);
  }

  /**
   * 解决setter方法的冲突，去重
   *
   * @author shixiongfei
   * @date 2020/7/3 11:21 上午
   */
  private void resolveSetterConflicts(Map<String, List<Method>> conflictingSetters) {
    for (String propName : conflictingSetters.keySet()) {
      // 通过属性名称获取所有的setter方法
      List<Method> setters = conflictingSetters.get(propName);
      // 获取getter方法的返回值class对象
      Class<?> getterType = getTypes.get(propName);
      Method match = null;
      ReflectionException exception = null;
      for (Method setter : setters) {
        Class<?> paramType = setter.getParameterTypes()[0];
        // 如果setter入参类型等于getter返回值类型,则直接负责给match返回
        if (paramType.equals(getterType)) {
          // should be the best match
          match = setter;
          break;
        }
        if (exception == null) {
          try {
            // 异常为空时，查询更加适配的setter方法
            match = pickBetterSetter(match, setter, propName);
          } catch (ReflectionException e) {
            // there could still be the 'best match'
            match = null;
            exception = e;
          }
        }
      }
      if (match == null) {
        throw exception;
      } else {
        addSetMethod(propName, match);
      }
    }
  }

  /**
   * 查询更加的setter方法
   *
   * @author shixiongfei
   * @date 2020/7/3 11:25 上午
   */
  private Method pickBetterSetter(Method setter1, Method setter2, String property) {
    if (setter1 == null) {
      return setter2;
    }
    Class<?> paramType1 = setter1.getParameterTypes()[0];
    Class<?> paramType2 = setter2.getParameterTypes()[0];
    if (paramType1.isAssignableFrom(paramType2)) {
      return setter2;
    } else if (paramType2.isAssignableFrom(paramType1)) {
      return setter1;
    }
    throw new ReflectionException("Ambiguous setters defined for property '" + property + "' in class '"
        + setter2.getDeclaringClass() + "' with types '" + paramType1.getName() + "' and '"
        + paramType2.getName() + "'.");
  }

  private void addSetMethod(String name, Method method) {
    if (isValidPropertyName(name)) {
      setMethods.put(name, new MethodInvoker(method));
      Type[] paramTypes = TypeParameterResolver.resolveParamTypes(method, type);
      setTypes.put(name, typeToClass(paramTypes[0]));
    }
  }

  private Class<?> typeToClass(Type src) {
    Class<?> result = null;
    if (src instanceof Class) {
      result = (Class<?>) src;
    } else if (src instanceof ParameterizedType) {
      result = (Class<?>) ((ParameterizedType) src).getRawType();
    } else if (src instanceof GenericArrayType) {
      Type componentType = ((GenericArrayType) src).getGenericComponentType();
      if (componentType instanceof Class) {
        result = Array.newInstance((Class<?>) componentType, 0).getClass();
      } else {
        Class<?> componentClass = typeToClass(componentType);
        result = Array.newInstance(componentClass, 0).getClass();
      }
    }
    if (result == null) {
      result = Object.class;
    }
    return result;
  }

  /**
   * 此方法是当字段不存在getter setter方法的时候
   * 为其添加一个SetFieldInvoker GetFieldInvoker 来保证对属性的获取和修改
   *
   * @author shixiongfei
   * @date 2020/7/3 4:09 下午
   */
  private void addFields(Class<?> clazz) {
    // 获取当前类所有声明的字段数组
    Field[] fields = clazz.getDeclaredFields();
    // 遍历
    for (Field field : fields) {
      // 如果当前字段不存在setter方法时，为其新增一个为其添加一个SetFieldInvoker
      if (!setMethods.containsKey(field.getName())) {
        // issue #379 - removed the check for final because JDK 1.5 allows
        // modification of final fields through reflection (JSR-133). (JGB)
        // pr #16 - final static can only be set by the classloader
        // PUBLIC: 1
        //PRIVATE: 2
        //PROTECTED: 4
        //STATIC: 8
        //FINAL: 16
        //SYNCHRONIZED: 32
        //VOLATILE: 64
        //TRANSIENT: 128
        //NATIVE: 256
        //INTERFACE: 512
        //ABSTRACT: 1024
        //STRICT: 2048
        int modifiers = field.getModifiers();
        // 如果字段为final 且字段为静态字段则不跳过，不对这些字段进行保存
        // 因为静态常量属于常量池可直接进行修改
        // 这里是采用位运算来判断字段包含哪些关键字
        if (!(Modifier.isFinal(modifiers) && Modifier.isStatic(modifiers))) {
          addSetField(field);
        }
      }
      // 同理， 如果当前字段不存在getter方法时，为其新增一个为其添加一个GetFieldInvoker
      if (!getMethods.containsKey(field.getName())) {
        addGetField(field);
      }
    }
    // 如果类对象包含父类，则递归扫描
    if (clazz.getSuperclass() != null) {
      addFields(clazz.getSuperclass());
    }
  }

  /**
   * 当字段不存在setter方法时，采用此方法来添加 SetFieldInvoker 对象
   * 来保证通过Field 本身的set方法来设置属性值
   *
   * @author shixiongfei
   * @date 2020/7/3 4:29 下午
   * @param field 字段对象
   */
  private void addSetField(Field field) {
    if (isValidPropertyName(field.getName())) {
      setMethods.put(field.getName(), new SetFieldInvoker(field));
      Type fieldType = TypeParameterResolver.resolveFieldType(field, type);
      setTypes.put(field.getName(), typeToClass(fieldType));
    }
  }

  /**
   * getMethods中添加 GetFieldInvoker
   * 这个是因为当字段不存在getter setter方法的时候，采用Field本身的get方法来获取属性值
   *
   * @author shixiongfei
   * @date 2020/7/3 4:28 下午
   * @param field 字段对象
   * @return
   */
  private void addGetField(Field field) {
    if (isValidPropertyName(field.getName())) {
      getMethods.put(field.getName(), new GetFieldInvoker(field));
      Type fieldType = TypeParameterResolver.resolveFieldType(field, type);
      getTypes.put(field.getName(), typeToClass(fieldType));
    }
  }

  /**
   * 校验是否为合法的属性名
   * 校验规则：
   * 属性名称开头不可包含$ 且不是序列化id且名称不是class则认为其是合法的属性名称
   *
   * @author shixiongfei
   * @date 2020/7/1 11:26 上午
   * @param name 属性名称
   * @return
   */
  private boolean isValidPropertyName(String name) {
    // 属性名称开头不可包含$ 且不是序列化id且名称不是class则认为其是合法的属性名称
    return !(name.startsWith("$") || "serialVersionUID".equals(name) || "class".equals(name));
  }

  /**
   * This method returns an array containing all methods
   * declared in this class and any superclass.
   * We use this method, instead of the simpler <code>Class.getMethods()</code>,
   * because we want to look for private methods as well.
   *
   * @param cls The class
   * @return An array containing all methods in this class
   */
  private Method[] getClassMethods(Class<?> cls) {
    Map<String, Method> uniqueMethods = new HashMap<>();
    Class<?> currentClass = cls;
    while (currentClass != null && currentClass != Object.class) {
      addUniqueMethods(uniqueMethods, currentClass.getDeclaredMethods());

      // we also need to look for interface methods -
      // because the class may be abstract
      Class<?>[] interfaces = currentClass.getInterfaces();
      for (Class<?> anInterface : interfaces) {
        addUniqueMethods(uniqueMethods, anInterface.getMethods());
      }

      currentClass = currentClass.getSuperclass();
    }

    Collection<Method> methods = uniqueMethods.values();

    return methods.toArray(new Method[methods.size()]);
  }

  private void addUniqueMethods(Map<String, Method> uniqueMethods, Method[] methods) {
    for (Method currentMethod : methods) {
      // 判断当前方法是否为桥接方法，如果不是则继续执行
      // 所谓交接方法，就是一种使java范型方法生成的字节码和1.5版本前的字节码相兼容
      if (!currentMethod.isBridge()) {
        // 获取方法签名
        String signature = getSignature(currentMethod);
        // check to see if the method is already known
        // if it is known, then an extended class must have
        // overridden a method
        if (!uniqueMethods.containsKey(signature)) {
          uniqueMethods.put(signature, currentMethod);
        }
      }
    }
  }

  public static String getSignature(Method method) {
    StringBuilder sb = new StringBuilder();
    // 方法返回值class对象
    Class<?> returnType = method.getReturnType();
    if (returnType != null) {
      sb.append(returnType.getName()).append('#');
    }
    sb.append(method.getName());
    Class<?>[] parameters = method.getParameterTypes();
    for (int i = 0; i < parameters.length; i++) {
      if (i == 0) {
        sb.append(':');
      } else {
        sb.append(',');
      }
      sb.append(parameters[i].getName());
    }
    return sb.toString();
  }

  /**
   * Checks whether can control member accessible.
   * 检查是否可以控制成员访问
   *
   * @return If can control member accessible, it return {@literal true}
   * @since 3.5.0
   */
  public static boolean canControlMemberAccessible() {
    try {
      SecurityManager securityManager = System.getSecurityManager();
      if (null != securityManager) {
        securityManager.checkPermission(new ReflectPermission("suppressAccessChecks"));
      }
    } catch (SecurityException e) {
      return false;
    }
    return true;
  }

  /**
   * Gets the name of the class the instance provides information for.
   *
   * @return The class name
   */
  public Class<?> getType() {
    return type;
  }

  public Constructor<?> getDefaultConstructor() {
    if (defaultConstructor != null) {
      return defaultConstructor;
    } else {
      throw new ReflectionException("There is no default constructor for " + type);
    }
  }

  public boolean hasDefaultConstructor() {
    return defaultConstructor != null;
  }

  public Invoker getSetInvoker(String propertyName) {
    Invoker method = setMethods.get(propertyName);
    if (method == null) {
      throw new ReflectionException("There is no setter for property named '" + propertyName + "' in '" + type + "'");
    }
    return method;
  }

  public Invoker getGetInvoker(String propertyName) {
    Invoker method = getMethods.get(propertyName);
    if (method == null) {
      throw new ReflectionException("There is no getter for property named '" + propertyName + "' in '" + type + "'");
    }
    return method;
  }

  /**
   * Gets the type for a property setter.
   *
   * @param propertyName - the name of the property
   * @return The Class of the property setter
   */
  public Class<?> getSetterType(String propertyName) {
    Class<?> clazz = setTypes.get(propertyName);
    if (clazz == null) {
      throw new ReflectionException("There is no setter for property named '" + propertyName + "' in '" + type + "'");
    }
    return clazz;
  }

  /**
   * Gets the type for a property getter.
   *
   * @param propertyName - the name of the property
   * @return The Class of the property getter
   */
  public Class<?> getGetterType(String propertyName) {
    Class<?> clazz = getTypes.get(propertyName);
    if (clazz == null) {
      throw new ReflectionException("There is no getter for property named '" + propertyName + "' in '" + type + "'");
    }
    return clazz;
  }

  /**
   * Gets an array of the readable properties for an object.
   *
   * @return The array
   */
  public String[] getGetablePropertyNames() {
    return readablePropertyNames;
  }

  /**
   * Gets an array of the writable properties for an object.
   *
   * @return The array
   */
  public String[] getSetablePropertyNames() {
    return writablePropertyNames;
  }

  /**
   * Check to see if a class has a writable property by name.
   *
   * @param propertyName - the name of the property to check
   * @return True if the object has a writable property by the name
   */
  public boolean hasSetter(String propertyName) {
    return setMethods.keySet().contains(propertyName);
  }

  /**
   * Check to see if a class has a readable property by name.
   *
   * @param propertyName - the name of the property to check
   * @return True if the object has a readable property by the name
   */
  public boolean hasGetter(String propertyName) {
    return getMethods.keySet().contains(propertyName);
  }

  public String findPropertyName(String name) {
    return caseInsensitivePropertyMap.get(name.toUpperCase(Locale.ENGLISH));
  }
}
