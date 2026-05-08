package cn.wanyj.codefreex.service;

import cn.wanyj.codefreex.model.dto.LoginUserContext;
import cn.wanyj.codefreex.model.dto.request.RegisterRequest;
import cn.wanyj.codefreex.model.dto.request.WechatCompleteRequest;
import cn.wanyj.codefreex.model.dto.response.TokenResponse;
import cn.wanyj.codefreex.model.dto.response.WechatLoginResponse;
import cn.wanyj.codefreex.model.dto.response.WechatQrCodeResponse;

/**
 * 认证服务（封装 RPC 调用 + Session 管理 + Authing 第三方登录）
 * @author wanyj
 */
public interface AuthService {

    /**
     * 发送邮箱注册验证码
     */
    void sendEmailCode(String email);

    /**
     * 邮箱注册（验证码 + 密码 + 邀请码）
     */
    TokenResponse register(RegisterRequest request);

    /**
     * 邮箱密码登录
     */
    TokenResponse loginByEmail(String email, String password);

    /**
     * 生成微信小程序登录二维码
     */
    WechatQrCodeResponse generateWechatQrCode();

    /**
     * 查询微信小程序登录二维码状态
     */
    WechatQrCodeResponse queryWechatQrCodeStatus(String qrcodeId);

    /**
     * 微信小程序扫码登录（用 ticket 换取用户信息）
     */
    WechatLoginResponse loginByWechatQrCode(String ticket);

    /**
     * 微信新用户完成注册（提交邀请码）
     */
    TokenResponse completeWechatLogin(WechatCompleteRequest request);

    /**
     * 登出
     */
    void logout();

    /**
     * 获取当前登录用户信息
     */
    LoginUserContext getLoginUser();
}
