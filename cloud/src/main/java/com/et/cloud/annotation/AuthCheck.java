package com.et.cloud.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

//AuthCheck.java 文件定义的是一个注解,它本身不做任何事，只是用来“标记”某些方法，告诉程序的其他部分：“嘿，注意了！这个方法很特殊，需要权限检查！
//一旦发现任何方法被贴上了 @AuthCheck 这张‘便利贴’，就在这个方法运行之前（@Before 或 @Around 通知），拦截下来，并执行我预设的权限检查代码。
//这个注解用来回答一个问题：“我这个自定义的‘便利贴’可以贴在哪里？
@Target(ElementType.METHOD)
//“我这个‘便利贴’需要保留到什么时候？请把 @AuthCheck 这个注解以及它的信息，一直保留到程序实际运行的时候。
@Retention(RetentionPolicy.RUNTIME)
public @interface AuthCheck {

    /**
     * 必须有某个角色
     */
    String mustRole() default "";
}
