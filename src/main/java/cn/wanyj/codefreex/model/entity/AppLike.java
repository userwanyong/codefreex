package cn.wanyj.codefreex.model.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.keygen.KeyGenerators;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 应用点赞记录
 *
 * @author wanyj
 */
@Data
@Table("app_like")
public class AppLike {

    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private Long id;

    private Long appId;

    private Long userId;

    @Column(onInsertValue = "now()")
    private LocalDateTime createTime;
}
