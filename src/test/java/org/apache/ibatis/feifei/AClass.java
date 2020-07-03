package org.apache.ibatis.feifei;

/**
 * 测试java交接方法
 * 编译后的字节码会生成一个桥接方法
 *
 * @Author: shixiongfei
 * @Date: 2020/7/3 11:38
 */
public class AClass implements Ainterface<String> {

  @Override
  public void func(String s) {
    System.out.println(s);
  }

  /**
   * 实际编译后生成的桥接方法
   * @param args
   */
//  public void func(Object s) {
//    this.func((String) s);
//  }


  public static void main(String[] args) {
    AClass aClass = new AClass();
    aClass.func("bibi");
  }
}
