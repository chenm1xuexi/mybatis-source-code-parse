package org.apache.ibatis.reflection.feifei;

import org.apache.ibatis.reflection.Reflector;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

/**
 * 测试reflector
 *
 * @Author: shixiongfei
 * @Date: 2020/7/1 12:27
 */
public class FeifeiTest {

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

  @Test
  public void test1() {
    Reflector reflector = new Reflector(Bob.class);
  }

  /**
   * 测试方法签名
   *
   * @author shixiongfei
   * @date 2020/7/3 11:46 上午
   */
  @Test
  public void testSignature() {
    Method[] methods = Bob.class.getDeclaredMethods();
    for (Method method : methods) {
      if (method.getName().equals("getIsSuccess")) {
        System.out.println(getSignature(method));
      }
    }
  }
}
