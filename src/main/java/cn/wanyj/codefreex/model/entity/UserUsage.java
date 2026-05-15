package cn.wanyj.codefreex.model.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.keygen.KeyGenerators;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户用量统计实体
 *
 * @author wanyj
 */
@Data
@Table("user_usage")
public class UserUsage {

    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private Long id;

    private Long userId;

    private Long appId;

    private String modelId;

    private Integer inputTokens;

    private Integer outputTokens;

    private Integer totalTokens;

    private Integer latency;

    private String status;

    private String errorInfo;

    @Column(onInsertValue = "now()")
    private LocalDateTime createTime;
}
