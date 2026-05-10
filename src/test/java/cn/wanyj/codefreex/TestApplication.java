package cn.wanyj.codefreex;

import cn.wanyj.codefreex.auth.AuthRpcClient;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

/**
 * 测试专用启动类，不含 @EnableDubbo，排除 Dubbo 相关组件避免引用检查失败。
 */
@SpringBootApplication
@MapperScan("cn.wanyj.codefreex.mapper")
@ComponentScan(
        basePackages = "cn.wanyj.codefreex",
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = CodefreexApplication.class),
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = AuthRpcClient.class)
        })
public class TestApplication {
}
