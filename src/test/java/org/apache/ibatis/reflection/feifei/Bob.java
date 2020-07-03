package org.apache.ibatis.reflection.feifei;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

/**
 * TODO description
 *
 * @Author: shixiongfei
 * @Date: 2020/7/1 12:28
 */
@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Bob {

  String name;

  String address;

  boolean success;

  public boolean getIsSuccess(boolean success) {
    return success;
  }

  public String getName(String name, String tel) {
    System.out.println(tel);
    return name;
  }
}
