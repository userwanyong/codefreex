package cn.wanyj.codefreex.model.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.keygen.KeyGenerators;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 应用实体
 *
 * @author wanyj
 */
@Data
@Table("app")
public class App {

    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private Long id;

    private String appName;

    private String description;

    private String cover;

    private String initPrompt;

    private String codeGenType;

    private String status;

    private String deployKey;

    private LocalDateTime deployedTime;

    /**
     * 部署计费最近一次扣费时间（用于按周期扣减部署码点）
     */
    private LocalDateTime deployBilledTime;

    private Integer isPublic;

    private Integer isFeatured;

    private Integer priority;

    private Integer viewCount;

    private Integer likeCount;

    /**
     * 应用标签（展示用快照）。标签唯一数据源为 app_tag 关联表，
     * 此字段不落库，忽略生成 SQL，避免 SELECT 携带不存在的 tags 列。
     */
    @Column(ignore = true)
    private List<String> tags;

    private Long userId;

    @Column(onInsertValue = "now()")
    private LocalDateTime editTime;

    @Column(onInsertValue = "now()")
    private LocalDateTime createTime;

    @Column(onInsertValue = "now()", onUpdateValue = "now()")
    private LocalDateTime updateTime;

    private Integer isDelete;
}
