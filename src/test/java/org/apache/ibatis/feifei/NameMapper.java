package org.apache.ibatis.feifei;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * @author shixiongfei
 * @date 2020-03-19
 * @since
 */
public interface NameMapper {

  @Select("select * from name where id = #{id}")
  Name getById(@Param("id") Long id);

  @Select("<script> " +
    "select * from name where id = #{id} " +
    "<if test='name != null'> " +
    "and name = #{name} " +
    "</if> " +
    "</script>")
  Name getById1(@Param("id") Long id, @Param("name") String name);

  @Update("update name a set a.name = #{name} where a.id = #{id}")
  void updateName(@Param("id") Long id, @Param("name") String name);

  @Insert("insert into name(name) values(#{name.name})")
  void add(Name name);
}
