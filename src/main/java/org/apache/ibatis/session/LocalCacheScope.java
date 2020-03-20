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
package org.apache.ibatis.session;

/**
 * @author Eduardo Macarron
 * 本地缓存区域
 * 默认是当前的sqlSession会话级别
 * 如果是Statement级别则直接移除一级缓存
 */
public enum LocalCacheScope {

  /**
   * session级别
   */
  SESSION,
  /**
   * 语句级别，也就是namespace?
   */
  STATEMENT
}
