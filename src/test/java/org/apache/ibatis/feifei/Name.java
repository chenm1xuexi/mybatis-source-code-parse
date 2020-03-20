package org.apache.ibatis.feifei;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import lombok.experimental.FieldDefaults;

/**
 * @author shixiongfei
 * @date 2020-03-19
 * @since
 */
@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
@Accessors(chain = true)
public class Name {

  Long id;

  String name;
}
