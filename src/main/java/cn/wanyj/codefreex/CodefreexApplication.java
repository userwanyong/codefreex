package cn.wanyj.codefreex;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * @author wanyj
 */
@SpringBootApplication
@MapperScan("cn.wanyj.codefreex.mapper")
@EnableDubbo
public class CodefreexApplication {

    public static void main(String[] args) {
        SpringApplication.run(CodefreexApplication.class, args);
    }

}
