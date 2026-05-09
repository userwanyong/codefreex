package cn.wanyj.codefreex.testutil;

import cn.wanyj.codefreex.auth.AuthRpcClient;
import dev.langchain4j.model.chat.StreamingChatModel;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;

/**
 * 集成测试共用配置，提供被排除的 Redis、Dubbo 组件和 prototype StreamingChatModel 的 Mock。
 */
@TestConfiguration
public class IntegrationTestConfig {

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        return Mockito.mock(RedisConnectionFactory.class);
    }

    @Bean
    @Primary
    public AuthRpcClient authRpcClient() {
        return Mockito.mock(AuthRpcClient.class);
    }

    @Bean
    @Primary
    public StreamingChatModel streamingChatModelMock() {
        return Mockito.mock(StreamingChatModel.class);
    }
}
