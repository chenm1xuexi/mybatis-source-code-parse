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
package org.apache.ibatis.session;

/**
 * Specifies if and how MyBatis should automatically map columns to fields/properties.
 *
 * 指定mybatis设置数据库列映射到实体属性的规则，默认为PARTIAL
 *
 * @author Eduardo Macarron
 */
public enum AutoMappingBehavior {

  /**
   * Disables auto-mapping.
   * 不做自动映射
   */
  NONE,

  /**
   * Will only auto-map results with no nested result mappings defined inside.
   * 将仅自动映射结果，而内部没有定义嵌套的结果映射。
   */
  PARTIAL,

  /**
   * Will auto-map result mappings of any complexity (containing nested or otherwise).
   */
  FULL
}
