package cn.wanyj.codefreex.auth.annotation;

import java.lang.annotation.*;

/**
 * 权限校验注解
 * 标注在 Controller 方法上，支持角色校验
 * @author wanyj
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AuthCheck {

    /**
     * 是否必须登录（默认 true）
     */
    boolean mustLogin() default true;

    /**
     * 需要的角色（为空表示仅需登录）
     */
    String[] roles() default {};
}
