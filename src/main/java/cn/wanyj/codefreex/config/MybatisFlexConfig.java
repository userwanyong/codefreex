package cn.wanyj.codefreex.config;

import com.mybatisflex.core.audit.AuditManager;
import com.mybatisflex.spring.boot.MyBatisFlexCustomizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Flex 配置
 *
 * @author wanyj
 */
@Slf4j
@Configuration
public class MybatisFlexConfig {

    /**
     * MyBatis-Flex 自定义配置
     */
    @Bean
    public MyBatisFlexCustomizer myBatisFlexCustomizer() {
        return settings -> {
            // 开启审计日志（开发环境便于调试 SQL）
            AuditManager.setAuditEnable(true);
            AuditManager.setMessageCollector(auditMessage ->
                    log.debug("SQL Audit -> {}ms: {}", auditMessage.getElapsedTime(), auditMessage.getFullSql())
            );
        };
    }
}
