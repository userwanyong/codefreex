package cn.wanyj.codefreex.auth.aspect;

import cn.wanyj.codefreex.auth.UserContext;
import cn.wanyj.codefreex.auth.annotation.AuthCheck;
import cn.wanyj.codefreex.exception.BusinessException;
import cn.wanyj.codefreex.exception.ResponseCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * 权限校验 AOP 切面
 * @author wanyj
 */
@Aspect
@Component
public class AuthCheckAspect {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Around("@annotation(authCheck)")
    public Object doAuthCheck(ProceedingJoinPoint joinPoint, AuthCheck authCheck) throws Throwable {
        // 1. 校验是否登录
        if (authCheck.mustLogin() && !UserContext.isLoggedIn()) {
            throw new BusinessException(ResponseCode.NOT_LOGIN_ERROR);
        }

        // 2. 校验角色
        String[] requiredRoles = authCheck.roles();
        if (requiredRoles.length > 0) {
            List<String> userRoles = UserContext.getLoginUser().getRoles();
            if (userRoles == null || Arrays.stream(requiredRoles).noneMatch(userRoles::contains)) {
                throw new BusinessException(ResponseCode.NO_AUTH_ERROR);
            }
        }

        return joinPoint.proceed();
    }
}
