/**
 *    Copyright 2009-2015 the original author or authors.
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
package org.apache.ibatis.mapping;

/**
 * @author Clinton Begin
 *
 * 标记操作SQL对象类型
 * 1. Statement 直接操作sql，不进行预编译，获取数据
 * 2. PrePared 预处理，参数，进行预编译（就是将#{}转换为？），获取数据 => 默认
 * 3. 执行存储过程 => 一般不会用
 */
public enum StatementType {
  STATEMENT,
  PREPARED,
  CALLABLE
}
