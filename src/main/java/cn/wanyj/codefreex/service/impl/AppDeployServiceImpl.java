package cn.wanyj.codefreex.service.impl;

import cn.wanyj.codefreex.exception.BusinessException;
import cn.wanyj.codefreex.exception.ResponseCode;
import cn.wanyj.codefreex.mapper.AppMapper;
import cn.wanyj.codefreex.model.dto.response.AppDeployResponse;
import cn.wanyj.codefreex.model.entity.App;
import cn.wanyj.codefreex.model.enums.AppStatus;
import cn.wanyj.codefreex.service.AppCoverService;
import cn.wanyj.codefreex.service.AppDeployService;
import cn.wanyj.codefreex.service.AppNginxService;
import cn.wanyj.codefreex.service.AppStorageService;
import com.mybatisflex.core.update.UpdateChain;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.LocalDateTime;

import static cn.wanyj.codefreex.model.entity.table.AppTableDef.APP;

/**
 * 应用部署服务实现
 *
 * @author BanXia
 */
@Service
@RequiredArgsConstructor
public class AppDeployServiceImpl implements AppDeployService {

    private final AppMapper appMapper;
    private final AppStorageService appStorageService;
    private final AppCoverService appCoverService;
    private final AppNginxService appNginxService;

    @Override
    public AppDeployResponse deployApp(Long userId, Long appId) {
        App app = getOwnedApp(userId, appId);
        String status = app.getStatus();
        if (!AppStatus.GENERATED.getValue().equals(status) && !AppStatus.DEPLOYED.getValue().equals(status)) {
            throw new BusinessException(ResponseCode.PARAMS_ERROR, "仅已生成或已部署应用可部署");
        }
        if (app.getIsPublic() == null || app.getIsPublic() != 1) {
            throw new BusinessException(ResponseCode.PARAMS_ERROR, "仅公开应用可部署");
        }

        appStorageService.copyGeneratedToDeployed(app.getDeployKey());
        appNginxService.publish(app.getDeployKey());
        LocalDateTime deployedTime = LocalDateTime.now();
        UpdateChain.of(App.class)
                .where(APP.ID.eq(appId))
                .set(APP.STATUS, AppStatus.DEPLOYED.getValue())
                .set(APP.DEPLOYED_TIME, deployedTime)
                .update();

        appCoverService.generateCoverAsync(appId, app.getDeployKey(), app.getAppName(), app.getDescription());

        AppDeployResponse response = new AppDeployResponse();
        response.setAppId(appId);
        response.setDeployKey(app.getDeployKey());
        response.setStatus(AppStatus.DEPLOYED.getValue());
        response.setPreviewUrl("/api/static/" + app.getDeployKey() + "/");
        response.setDeployedUrl("/api/deploy/" + app.getDeployKey() + "/");
        response.setCoverUrl("/api/deploy/" + app.getDeployKey() + "/cover.png");
        response.setDeployedTime(deployedTime);
        return response;
    }

    @Override
    public void cancelDeploy(Long userId, Long appId) {
        App app = getOwnedApp(userId, appId);
        appStorageService.deleteDeployedFiles(app.getDeployKey());
        appNginxService.remove(app.getDeployKey());

        UpdateChain.of(App.class)
                .where(APP.ID.eq(appId))
                .set(APP.STATUS, AppStatus.GENERATED.getValue())
                .set(APP.DEPLOYED_TIME, null)
                .update();
    }

    @Override
    public DownloadArchive prepareDownloadArchive(Long userId, Long appId) {
        App app = getOwnedApp(userId, appId);
        String archiveName = app.getDeployKey() + ".zip";
        Path archivePath = appStorageService.createDownloadArchive(app.getDeployKey(), archiveName);
        return new DownloadArchive(archivePath, archiveName);
    }

    private App getOwnedApp(Long userId, Long appId) {
        App app = appMapper.selectOneById(appId);
        if (app == null) {
            throw new BusinessException(ResponseCode.NOT_FOUND_ERROR, "应用不存在");
        }
        if (!app.getUserId().equals(userId)) {
            throw new BusinessException(ResponseCode.NO_AUTH_ERROR, "无权限操作该应用");
        }
        return app;
    }
}
