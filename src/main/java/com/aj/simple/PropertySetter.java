package com.aj.simple;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

//注解 将下划线属性名  加上set驼峰方法   或者将驼峰属性名 加上set下划线方法
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.SOURCE)
public @interface PropertySetter {
}
