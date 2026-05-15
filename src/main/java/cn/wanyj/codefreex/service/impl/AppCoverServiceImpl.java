package cn.wanyj.codefreex.service.impl;

import cn.wanyj.codefreex.common.AppConstant;
import cn.wanyj.codefreex.config.AppRuntimeConfig;
import cn.wanyj.codefreex.service.AppCoverService;
import cn.wanyj.codefreex.service.AppStorageService;
import cn.wanyj.codefreex.service.OssService;
import cn.wanyj.codefreex.service.ScreenshotExecutor;
import com.mybatisflex.core.update.UpdateChain;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.file.Path;

import static cn.wanyj.codefreex.model.entity.table.AppTableDef.APP;

/**
 * 应用封面服务实现
 *
 * @author BanXia
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppCoverServiceImpl implements AppCoverService {

    private static final String COVER_FILE_NAME = "cover.png";

    private final AppStorageService appStorageService;
    private final ScreenshotExecutor screenshotExecutor;
    private final OssService ossService;
    private final AppRuntimeConfig.ScreenshotProperties screenshotProperties;
    private final AppRuntimeConfig.DeployAccessProperties deployAccessProperties;

    @Override
    @Async
    public void generateCoverAsync(Long appId, String deployKey, String appName, String description) {
        try {
            Path coverPath;
            if (screenshotProperties.isEnabled()) {
                coverPath = Path.of(AppConstant.CODE_DEPLOY_ROOT_DIR, deployKey, COVER_FILE_NAME);
                String deployUrl = deployAccessProperties.buildDeployUrl(deployKey);
                screenshotExecutor.capture(deployUrl, coverPath, screenshotProperties.getWidth(), screenshotProperties.getHeight());
            } else {
                appStorageService.generateCover(deployKey, appName, description);
                coverPath = Path.of(AppConstant.CODE_DEPLOY_ROOT_DIR, deployKey, COVER_FILE_NAME);
            }
            String objectKey = "cover/" + deployKey + "/" + COVER_FILE_NAME;
            String coverUrl = ossService.upload(objectKey, coverPath);

            UpdateChain.of(cn.wanyj.codefreex.model.entity.App.class)
                    .where(APP.ID.eq(appId))
                    .set(APP.COVER, coverUrl)
                    .update();
        } catch (Exception e) {
            log.warn("生成应用封面失败: appId={}", appId, e);
        }
    }
}
