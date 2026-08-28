# Populife Android App — 在用 API 文档

> 自动整理自源码，生成日期 2026-07-22。
> 来源：`common/Urls.java`（主后端）、`pplock` 曼哈顿 SDK `LockUrls.java`（锁加密认证后端）、H5/WebView 外链。
> 出入参、返回字段均从各调用点源码提取。

## ⚠️ 关于返回字段的重要说明

项目网络层用 `ScalarsConverter`，后端返回**原始字符串**，客户端 `JSON.parseObject(response)` 后**只手动读取自己用得到的字段**。因此本文档「返回字段」列 = **App 实际读取的字段，不等于后端完整返回契约**。要权威的返回结构，请以后端接口文档为准。

约定：
- `readUserId` = `PeachPreference.readUserId()`（当前登录用户 id）
- `timeZone` = `DateUtil.getTimeZone()`（设备时区）
- 方法（GET/POST）以调用点实际 `.get()/.post()` 为准，个别与 `Urls.java` 注释不一致，已注明。
- 「仅判断 code」= 成功回调只用返回码判断结果，未读取业务字段。

---

## 一、基础信息

| 项 | 值 |
| --- | --- |
| 主后端 BASE_URL | `https://v2.server.populife.co` |
| 备用地址（注释保留） | `https://server.populife.szmuen.cn` |
| 网络框架 | Retrofit2 + OkHttp，`ScalarsConverterFactory` |
| 超时 | 连接 60s |
| 鉴权 | Cookie（`AddCookiesInterceptor` / `ReceivedCookiesInterceptor`） |
| 核心类 | `net/RestCreator.java`、`net/RestClient.java`、`net/RestService.java` |
| 通用返回壳 | `{ code, msg/success, data }`，`code==200` 为成功 |

接口常量集中定义在 [Urls.java](app/src/main/java/com/populstay/populife/common/Urls.java)，实际 URL = `BASE_URL` + 常量值。

---

## 二、用户 · 注册登录

#### user/register `POST` — 用户注册
- 入参：`username`, `password`, `code`, `type`（手机/邮箱）, `deviceId`（设备值）
- 返回：`code`；`data`（成功时读为字符串 userId）

#### user/login/bypass `POST` — 用户登录（当前启用，`user/login` 已注释）
- 入参：`username`, `password`, `deviceId`
- 返回：`code`, `data.phone`/`data.email`（containsKey 判断异地登录）, `data.userId`

#### user/relogin/push/notice `POST` — 重登录推送下线通知
- 入参：`userId`, `deviceId`
- 返回：无（仅打印日志）

#### user/get/deviceId `GET` — 查询最新登录设备 id（判断异地登录）
- 入参：`userId`
- 返回：`code`, `data`（字符串，最新设备 id）

#### user/send/code `POST` — 注册/绑定手机/绑定邮箱 发送验证码
- 入参：`type`, `username`, `country`（仅手机类型传）
- 返回：仅判断 `code`（200/951/952）

#### user/retrieve/password/send/code `POST` — 验证码（重置密码/删账号/异地登录）
- 入参：`username`；`country`（仅登录场景手机类型传）
- 返回：`code`, `msg`（955 频繁获取提示）

#### user/validate/verifycode `GET` — 校验验证码
- 入参：`phone`, `code`
- 返回：`code`, `data`（boolean 是否通过）

#### user/login/bycode/send/code `POST` — 验证码登录前发码
- 入参：`username`, `country`（仅手机类型传）
- 返回：`code`, `msg`（955 提示）

#### user/login/bycode `GET`/`POST` — 验证码登录 / 异地登录验证（两调用点方法不同）
- 入参：GET（登录验证页）`username`, `code`；POST（登录页）`username`, `code`, `deviceId`
- 返回：GET 读 `code`, `success`；POST 读 `code`, `data.phone`/`data.email`, `data.userId`

---

## 三、用户 · 资料与账号

#### user/nickname/modify `POST` — 修改昵称
- 入参：`nickname`, `userId` · 返回：仅判断 `code`

#### /user/avatar/upload `POST` — 上传头像（OkHttp multipart，非 RestClient）
- 入参：`file`（图片文件）, `userId` · 返回：`success`（boolean）, `data`（头像 URL）

#### user/get `GET` — 获取用户信息
- 入参：`userId`
- 返回：`code`, `data`→UserInfo：`id`, `phone`, `email`, `nickname`, `avatar`, `username`, `isDeleted`, `registeredDate`, `accountType`, `openid`

#### user/email/bind `POST` — 绑定邮箱
- 入参：`email`, `code`, `userId` · 返回：仅判断 `code`（200/951/953）

#### user/phone/bind `POST` — 绑定手机
- 入参：`phone`（含国家码）, `code`, `userId` · 返回：仅判断 `code`（200/951/953）

#### user/retrieve/password `POST` — 忘记密码后重置
- 入参：`username`, `password`, `code` · 返回：仅判断 `code`（200/953/954）

#### user/modify/password `POST` — 修改密码
- 入参：`password`（旧）, `newPassword`, `userId` · 返回：仅判断 `code`（200/910）

#### user/validate/password `POST` — 校验登录密码
- 入参：`password`, `userId` · 返回：`code`, `data`（boolean 是否正确）

#### user/delete `POST` — 删除用户
- 入参：`userId` · 返回：仅判断 `code`

#### user/language/switch `GET` — 切换语言
- 入参：`locale`（如 en_US/zh_CN/ja_JP） · 返回：无（onSuccess 为空）

---

## 四、消息

#### user/message/list `GET` — 消息列表
- 入参：`userId`, `start`(0), `limit`(1000)
- 返回：`code`, `data`（按日期分组数组；消息项 `id`, `userId`, `title`, `content`, `hasRead`（"Y"=已读））

#### user/message/empty `POST` — 清空全部消息
- 入参：`userId` · 返回：仅判断 `code`

#### user/message/get `GET` — 单条详情并置已读
- 入参：`id` · 返回：`code`, `data.createDate`（long）, `data.content`

#### user/message/delete `POST` — 删除单条
- 入参：`id` · 返回：仅判断 `code`

---

## 五、意见反馈

#### feedback/get `GET` — 反馈列表
- 入参：`userId` · 返回：`code`, `data[]`.{`id`, `userId`, `content`, `createDate`}

#### feedback/add `POST` — 提交反馈
- 入参：`userId`, `content`, `contact` · 返回：仅判断 `code`

#### feedback/delete `POST` — 删除反馈
- 入参：`id` · 返回：仅判断 `code`

---

## 六、家庭 / 分组

#### home/get `GET` — 家庭列表及分组下锁数量
- 入参：`userId`
- 返回：`code`, `data[]`（Gson→Home：`id`, `name`, `createDate`, `deviceCount`/`lockCount`）

#### home/my-own/get `GET` — 本人创建的空间
- 入参：`userId` · 返回：`code`, `data[]`（Home，读 `id`, `name`）

#### home/add `POST` — 添加家庭/分组
- 入参：`userId`, `name` · 返回：仅判断 `code`（200/910 重名）

#### home/modify `POST` — 修改家庭/分组
- 入参：`id`, `userId`, `name` · 返回：仅判断 `code`（200/910/951）

#### home/delete `POST` — 删除家庭/分组
- 入参：`id` · 返回：仅判断 `code`（200/500）

#### home/device/get `GET` — 家庭中的设备
- 入参：`userId`, `homeId` · 返回：`code`, `data[]`（HomeDevice：`name`, `deviceId`）

#### setting/lock/home/bind `GET` — 锁绑定分组（注释标 post，实际 .get()）
- 入参：`userId`, `lockId`, `homeId` · 返回：仅判断 `code`

---

## 七、锁 · 管理

#### lock/init/v3 `POST` — 初始化锁（初始化者=管理员）
- 入参（两套字段集）：
  - **TTLock 完整集**：`userId`, `name`, `lockName`, `alias`, `mac`, `lockData`, `key`, `flagPos`, `aesKey`, `adminPwd`, `noKeyPwd`, `deletePwd`, `pwdInfo`, `timestamp`, `specialValue`, `electricQuantity`, `timezoneRawOffSet`, `modelNum`, `hardwareRevision`, `firmwareRevision`, `protocolType`, `protocolVersion`, `scene`, `groupId`, `orgId`
  - **KJX 精简集**：`userId`, `name`, `mac`, `lockData`, `lockAlias`
- 返回：`code`, `data.lockId`（+ `data.noKeyPwd`）

#### lock/mhd_init `POST` — 曼哈顿锁初始化
- 入参：`userId`, `name`, `lockName`, `alias`, `mac`, `aesKey`, `timestamp`, `specialValue`(固定1230), `electricQuantity`, `timezoneRawOffSet`, `modelNum`, `hardwareRevision`, `firmwareRevision`, `protocolVersion`(固定1)
- 返回：`code`, `data.lockId`, `data.keyId`, `data.noKeyPwd`, `data.k1`, `data.k2`

#### lock/del `POST` — 删除锁（仅管理员）
- 入参：`userId`, `lockId`, `password` · 返回：`code`, `success`（需 200 且 success==true）

#### lock/rename `POST` — 修改锁名称
- 入参：`lockId`, `lockAlias` · 返回：仅判断 `code`

#### lock/bind-home `POST` — 绑定家庭
- 入参：`lockId`, `homeId` · 返回：仅判断 `code`

#### setting/lock/version `GET` — 检查固件版本
- 入参：`lockId`
- 返回：`code`, `data`.{`needUpgrade`, `modelNum`, `hardwareRevision`, `firmwareRevision`, `version`}

#### lock/transfer `POST` — 转移锁
- 入参：`userId`, `lockId`, `recUser`（含区号） · 返回：仅判断 `code`（200/951/952/920）

#### lock/updateElectricQuantity `POST` — 上传锁电量
- 入参：`lockId`, `electricQuantity` · 返回：仅判断 `code`

#### lock/queryElectricQuantity `POST` — 查询锁电量
- 入参：`lockId` · 返回：`code`, `data`（int 电量）

#### lock/baseInfo/get `GET` — 获取锁信息
- 入参：`userId`, `lockId`（可选）
- 返回：`code`（200/920无锁）, `data`→parseKJX/parseMHT：`lockId`, `keyId`, `userKeyId`, `keyType`, `keyRight`, `keyStatus`, `status`, `isAdmin`, `name`, `alias`, `mac`, `lockData`, `aesKey`, `noKeyPwd`, `modelNum`, `hardwareRevision`, `firmwareRevision`, `electricQuantity`, `specialValue`, `protocolVersion`, `timezoneRawOffSet`, `timestamp`, `initDate`, `startDate`, `endDate`, `home`

#### lock/user/check `POST` — 校验用户（是否管理员+校验密码）
- 入参：`password`, `userId`, `lockId` · 返回：`code`, `success`（boolean）

#### lock/user/list `GET` — 锁用户管理列表
- 入参：`userId`, `lockId`, `pageNo`(1), `pageSize`(50)
- 返回：`code`, `data[]`.{`userId`, `avatar`, `nickname`, `userName`, `alias`, `type`}

#### lock/admin-password/modify `POST` — 修改锁管理员键盘密码
- 入参：`password`（新键盘密码）, `lockId`, `userId` · 返回：仅判断 `code`

#### lock/specialValue/set `POST` — 修改锁特征值
- 入参：`specialValue`（int）, `lockId` · 返回：仅判断 `code`

#### lock/updateLockData `POST` — 修改锁特征值（新版）
- 入参：`lockData`, `lockId`, `type`(固定1) · 返回：仅判断 `code`

#### lock/unlock_type/save `POST` — 保存开锁类型
- 入参：`userId`, `lockId`（可选）, `unlockType`（1或2） · 返回：仅判断 `code`（920/200）

#### lock/setting/v5/notification/get `POST` — 获取通知状态
- 入参：`lockId` · 返回：`code`, `data`（String，判断 "true"）, `msg`

#### lock/setting/v5/notification/update `POST` — 更新通知状态
- 入参：`lockId`, `userId`, `notificationEnable`（Boolean） · 返回：`code`, `msg`

#### lock/list `POST` — 锁列表 · ❌ 唯一调用点已注释，实际未在用

---

## 八、锁 · 操作记录

#### operation/log/get `GET` — 锁操作记录（按天分组）
- 入参：`lockId`, `userId`, `start`(0), `limit`(300/500)；搜索页额外 `keyword`
- 返回：`code`, `data[]`（分组）；记录 `id`, `event`, `nickname`, `avatar`, `content`, `createDate`（event==3 额外 `password`）

#### operation/log/remove `POST` — 删除单条记录
- 入参：`id` · 返回：仅判断 `code`

#### operation/log/remove-all `POST` — 清空锁下所有记录
- 入参：`lockId`, `userId` · 返回：仅判断 `code`

#### operation/log/add `POST` — App 开/闭锁记录上报
- 入参：`userId`, `lockId`, `event`, `keyId` · 返回：无（仅打印日志）

#### operation/log/keyboard/add `POST` — 上传密码开锁记录
- 入参：`userId`, `lockId`, `records`（SDK 操作记录） · 返回：无（触发后续刷新）

#### operation/log/get/4/key `GET` — 钥匙操作记录
- 入参：`keyId`, `start`(0), `limit`(100)
- 返回：`code`, `data[]`（分组）：`id`, `nickname`, `avatar`, `content`, `createDate`

#### operation/log/get/4/password `GET` — 键盘密码操作记录
- 入参：`lockId`, `start`(0), `limit`(300), `password`
- 返回：`code`, `data[]`（分组）：`id`, `alias`, `content`, `createDate`

#### operation/log/get/4/icc `GET` — IC 卡操作记录
- 入参：`lockId`, `start`(0), `limit`(300), `id`（卡号） · 返回：同上分组结构

#### operation/log/get/4/fingerprint `GET` — 指纹操作记录
- 入参：`lockId`, `start`(0), `limit`(300), `id`（指纹号） · 返回：同上分组结构

> 后三者共用 `PasscodeRecordActivity` 同一解析逻辑，仅 URL 与区分参数不同。

---

## 九、钥匙（eKey）

#### key/receive `POST` — 接收钥匙
- 入参：`userId`, `preId` · 返回：`code`, `data`（String fromShareHomeId）；判断 951/952

#### key/v2/send `POST` — 发送钥匙（V2）
- 入参：`userId`, `recUser`（数字则拼国家码）, `lockId`, `type`, `keyAlias`；限时追加 `startDate`/`endDate`/`timeZone`, `arUnlock`, `auAdmin`, `allowAllPermissions`, `allowSyncBattery`, `allowCalibrateTime`
- 返回：`code`, `data`（分享链接 url）；判断 920/951/952/953/954

#### key/share/receive `POST` — 再次分享钥匙（指定接收者）
- 入参：`recUser`, `preId`, `timeZone` · 返回：仅判断 `code`（200/920/951/952/953/954）

#### key/list/v2 `POST` — 钥匙列表
- 入参：`userId`, `lockId`, `category`, `pageNo`, `pageSize`(20)
- 返回：`success`, `data.totalNum`, `data.dataList[]`→Gson KeyPwd：`id`, `alias`, `startDate`, `endDate`, `recUser`, `sendUser`, `sendDate`, `status`, `type`, `avatar`, `keyRight`, `keyboardPwd`, `keyboardPwdType`, `createDate`, `keyType`…

#### key/list `POST` — 钥匙列表（管理）
- 入参：`userId`, `lockId`, `pageNo`(1), `pageSize`(50)
- 返回：`code`, `data[]`.{`keyId`, `alias`, `startDate`, `endDate`, `recUser`, `sendUser`, `sendDate`, `keyStatus`, `type`, `avatar`, `keyRight`}

#### key/changeAlias `POST` — 修改钥匙别名
- 入参：`alias`, `keyId`, `userId` · 返回：仅判断 `code`

#### key/changePeriod `POST` — 修改钥匙有效期
- 入参：`userId`, `keyId`, `type`(1), `startDate`, `endDate`, `timeZone` · 返回：仅判断 `code`

#### lock/resetKey `POST` — 重置普通钥匙（仅管理员）
- 入参：`userId`, `lockId` · 返回：仅判断 `code`

#### lock/cleanKey `POST` — 清空钥匙
- 入参：`userId`, `lockId` · 返回：仅判断 `code`

#### key/freeze `POST` — 冻结钥匙
- 入参：`keyId`, `userId` · 返回：仅判断 `code`（200/951）

#### key/unfreeze `POST` — 解冻钥匙
- 入参：`keyId`, `userId` · 返回：仅判断 `code`

#### key/authorize `POST` — 授予管理权限
- 入参：`keyId`, `userId` · 返回：仅判断 `code`

#### key/unauthorize `POST` — 解除管理权限
- 入参：`keyId`, `userId` · 返回：仅判断 `code`

#### key/del `POST` — 删除钥匙
- 入参：`userId`, `keyId`, `delType`("Y"，仅授权用户/管理员传) · 返回：仅判断 `code`

#### lock/expire/key/list `GET` — 即将到期钥匙列表
- 入参：`userId`, `lockId`, `pageNo`(1), `pageSize`(50)
- 返回：`code`, `data[]`.{`avatar`, `nickname`, `startDate`, `endDate`, `lockAlias`, `dayNum`, `keyStatus`}

#### key/permission/get `GET` — 获取钥匙权限
- 入参：`keyId`
- 返回：`code`, `msg`, `data`.{`allowAllPermissions`, `allowRemoteUnlock`, `allowSyncBattery`, `allowCalibrateTime`}（boolean）

#### key/permission/update `POST` — 更新钥匙权限
- 入参：`keyId`, `userId`, `allowRemoteUnlock`；授权用户追加 `allowAllPermissions`/`allowSyncBattery`/`allowCalibrateTime`
- 返回：`code`, `msg`

---

## 十、键盘密码（Passcode）

#### key/keyboard/list/v2 `POST` — 密码列表（V2）
- 入参：`userId`, `lockId`, `category`, `pageNo`, `pageSize`(20)
- 返回：`success`, `data.totalNum`, `data.dataList[]`→Gson KeyPwd（同钥匙列表字段集）

#### keyboardPwd/get `POST` — 生成键盘密码
- 入参：`userId`, `lockId`, `keyboardPwdVersion`(4), `keyboardPwdType`, `keyId`, `alias`（非空时）；限时/自定义/循环追加 `startDate`/`endDate`, `timeZone`
- 返回：`code`, `data.keyboardPwd`

#### keyboardPwd/add `POST` — 添加自定义键盘密码
- 入参：`userId`, `lockId`, `keyboardPwd`, `keyboardPwdType`, `timeZone`, `keyId`, `mediumType`(1蓝牙/2网关), `alias`（非空时）, `startDate`/`endDate`
- 返回：仅判断 `code`（200/951 均成功）

#### keyboardPwd/delete `POST` — 删除键盘密码
- 入参：`lockId`, `userId`, `keyboardPwdId`, `mediumType` · 返回：仅判断 `code`

#### keyboardPwd/change `POST` — 修改键盘密码（仅三代锁 pwd v4）
- 入参：`userId`, `lockId`, `keyboardPwdId`, `changeType`(1), `timeZone`；改密码分支 `newKeyboardPwd`(+可能 `mediumType`)；改有效期分支 `startDate`/`endDate`
- 返回：仅判断 `code`

#### keyboardPwd/changeAlias `POST` — 修改密码别名
- 入参：`alias`, `keyboardPwdId`, `userId` · 返回：仅判断 `code`

#### keyboardPwd/list `POST` — 键盘密码列表
- 入参：`userId`, `lockId`, `pageNo`(1), `pageSize`(100)
- 返回：`code`, `data[]`.{`keyboardPwdId`, `keyboardPwd`, `alias`, `sendUser`, `keyboardPwdType`, `startDate`, `endDate`, `createDate`, `status`}

#### keyboardPwd/resetKeyboardPwd `POST` — 重置键盘密码（旧密码全失效）
- 入参：`userId`, `lockId`, `pwdInfo`(SDK；PP 锁 lockId<0 传""), `timestamp`(SDK；PP 锁不传)
- 返回：仅判断 `code`

---

## 十一、网关（Gateway）

#### gateway/list `GET` — 网关列表
- 入参：`userId`, `pageNo`(1), `pageSize`(50)
- 返回：`code`, `data[]`.{`gatewayId`, `gatewayMac`, `gatewayName`, `lockNum`, `isOnline`, `name`, `alias`}

#### gateway/userId/get `GET` — 获取 sciener userKeyId
- 入参：`userId` · 返回：`code`, `data`（int userKeyId）

#### gateway/add `POST` — SDK 添加网关后绑定名称
- 入参：`userId`, `name`, `gatewayMac`, `gatewayId`, `gatewayVersion`, `networkName`（WifiSSid）, `hardwareRevision`, `firmwareRevision`
- 返回：仅判断 `code`

#### gateway/init/success `GET` — 查询网关是否添加成功
- 入参：`userId`, `gatewayNetMac` · 返回：`code`, `data`.{`errcode`, `gatewayId`}

#### gateway/delete `POST` — 删除网关
- 入参：`userId`, `gatewayId` · 返回：`code`（额外判断 951）

#### gateway/lock/list `GET` — 网关下锁列表
- 入参：`userId`, `gatewayId`
- 返回：`code`, `data[]`.{`lockId`, `lockMac`, `lockName`, `rssi`, `updateDate`, `alias`, `specialValue`}

#### gateway/v5/lock/list `GET` — 网关锁列表 V5（唯一 Gson 整体反序列化）
- 入参：`userId`, `lockId`
- 返回：Gson→GatewayInfoResp：`success`, `data.hasGateway`, `data.dataList[]`（Gateway，用 `alias`）

#### lock/queryDate `POST` — 网关读取锁时间
- 入参：`lockId` · 返回：`code`, `data`.{`date`}（锁时间毫秒）

#### lock/updateDate `POST` — 网关校准锁时间
- 入参：`lockId` · 返回：仅判断 `code`（额外 920）

#### gateway/unlock `POST` — 网关远程开锁
- 入参：`userId`, `lockId` · 返回：`code`（额外 951）

#### gateway/lock `POST` — 网关远程闭锁
- 入参：`userId`, `lockId` · 返回：`code`（额外 951）

#### gateway/v2/lock/queryOpenState `GET` — 查询锁开关状态
- 入参：`userId`, `lockId` · 返回：`code`, `data`（int 0关/1开/2未知；额外 951）

#### gateway/freeze `POST` — 网关远程冻结锁
- 入参：`userId`, `lockId` · 返回：仅判断 `code`

#### gateway/unfreeze `POST` — 网关远程解冻锁
- 入参：`userId`, `lockId` · 返回：仅判断 `code`

#### gateway/modify-name `POST` — 修改网关名称
- 入参：`gatewayId`, `name` · 返回：仅判断 `code`

#### gateway/set-home `POST` — 设置家庭
- 入参：`gatewayId`, `homeId` · 返回：仅判断 `code`

---

## 十二、IC 卡

#### icc/get `POST` — IC 卡列表 / 搜索
- 入参（调用点差异）：完整版 `userId`, `lockId`, `category`, `start`, `limit`, `keyword`；简版 `lockId`, `start`, `limit`, `keyword`
- 返回：`code`/`success`, `data[]`.{`lockId`, `cardNumber`, `remark`, `type`, `startDate`/`endDate`（type==2）, `cardId`, `createDate`, `expire`}

#### icc/add `POST` — 蓝牙添加 IC 卡后调用
- 入参：`cardNumber`, `userId`, `lockId`, `alias`；限时卡额外 `timeZone`, `startDate`, `endDate`
- 返回：`code`, `data`（String，判断是否 =="REPEAT"）

#### icc/delete `POST` — 删除 IC 卡（蓝牙/网关）
- 入参：`lockId`, `cardNumber`, `deleteType`（1蓝牙/2网关，部分调用点无） · 返回：仅判断 `code`

#### icc/empty `POST` — 清空锁下 IC 卡（仅管理员）
- 入参：`lockId`, `userId`（部分调用点无） · 返回：仅判断 `code`

#### icc/internal/upload `POST` — 上传锁内 IC 卡（records 空则清空）
- 入参：`userId`, `lockId`, `records`（锁内 IC 卡 JSON） · 返回：多数不解析字段/仅判断 `code`

#### icc/edit `POST` — 编辑单个 IC 卡
- 入参：`cardId`, `userId`, `lockId`；改名 `alias`；改有效期 `changeType`, `startDate`, `endDate`, `timeZone`
- 返回：仅判断 `code`

---

## 十三、指纹

#### fingerprint/list `POST` — 指纹列表
- 入参：`userId`, `lockId`, `category`, `pageNo`, `pageSize`(20)
- 返回：`success`, `data[]`.{`id`, `fingerprintId`, `fingerprintNumber`, `remark`, `type`, `startDate`/`endDate`（type==1）, `createDate`, `alias`}

#### fingerprint/add `POST` — 蓝牙添加指纹后调用
- 入参：`lockId`, `userId`, `fingerprintId`, `type`, `alias`, `remark`, `fingerprintNumber`；限时额外 `startDate`, `endDate`, `timeZone`
- 返回：仅判断 `code`

#### fingerprint/delete `POST` — 删除指纹（蓝牙/网关）
- 入参：`id`, `lockId`, `userId`, `deleteType`（1蓝牙/2网关） · 返回：仅判断 `code`

#### fingerprint/clear-all `POST` — 清空指纹（仅管理员）
- 入参：`lockId`, `userId` · 返回：仅判断 `code`

#### fingerprint/add/missing `POST` — 对比补充缺失指纹
- 入参：`userId`, `lockId`, `records`（锁内指纹 JSON） · 返回：不解析字段

#### fingerprint/update `POST` — 更新单个指纹信息
- 入参：`id`, `lockId`；改名 `alias`；改备注 `remark`；改有效期 `changeType`, `startDate`, `endDate`, `timeZone`
- 返回：仅判断 `code`

---

## 十四、维修服务 & 支付（PayPal）

#### /repair-apply/submit `POST` — 提交维修申请（OkHttp MultipartBody.FORM，非 RestClient）
- 入参：`userId`, `purchasedTicketImg`（凭证图片文件）, `description`, `modelNum`, `purchasedDate`, `countryCode`（"+"+区号）, `name`, `emailAddress`, `telephone`, `province`, `city`, `streetAddress`, `postcode`
- 返回：`success`

#### repair-apply/get `GET` — 维修申请列表
- 入参：`userId`, `start`(0), `limit`(1000000)
- 返回：`success`, `data[]`（MaintRequest：`applyNo`, `userId`, `description`, `modelNum`, `purchasedTicket`, `purchasedDate`, `createDate`, `cancelled`, `status`, `lockName`, `empty`）

#### paypal/repair-service/device/list `GET` — 支付-设备列表
- 入参：`userId`
- 返回：`success`, `data[]`（MaintDevice：`id`, `name`, `alias`, `fee`, `type`, `isBuyed`）

#### paypal/repair-service/pay/order `GET` — 创建预支付订单（维修服务费）
- 入参：`userId`, `deviceId`, `type`（1:keybox 2:deadbolt 3:网关）
- 返回：`success`, `data`（MaintPayOrder：`currencyCode`, `preOrderNo`, `amount`）

#### paypal/success/repair-service/pay/order `POST` — 支付成功回调（维修服务费）
- 入参：`preOrderNo`, `paymentId`（PayPal 交易号；`payerId` 已注释不传）
- 返回：`success`

---

## 十五、曼哈顿锁 SDK 后端（pplock 模块）

来源 [LockUrls.java](pplock/src/main/java/com/populock/manhattan/sdk/constant/LockUrls.java)，BASE_URL 同为 `https://v2.server.populife.co/`。调用点集中在 `sdk/service/BleService.java`，用于门锁加密认证与密文获取。

#### private-lib/c1-get `GET` — 获取门锁绑定 C1
- 入参：`aesKey`（128 长随机）, `userId` · 返回：`code`(==200), `data`（C1 密文）

#### auth/user/get `POST` — 普通用户身份认证，获取 C1
- 入参：`userId`, `lockId`, `keyId`, `randomStr`（R1+T1） · 返回：`code`, `data`（C1 密文）

#### auth/v2/k2/update `POST` — 重置钥匙，上传 K2
- 入参：`userId`, `lockId`, `ciphertext`（16 字节 K2） · 返回：`code`

#### private-lib/lockKey-update `POST` — 重置密码，上传 lockKey
- 入参：`userId`, `lockId`, `text`（16 字节 lockKey 密文；键名 `text`，非注释里的 ciphertext） · 返回：`code`

#### operation/log/keyboard/add `POST` — 锁操作记录上传（SDK）
- 入参：`userId`, `lockId`, `records`（可为空串） · 返回：无（仅打印）

#### private-lib/ciphertext-get `POST` — 获取密文（添加/删除密码、普通用户开锁验证）
- 入参：`lockId`, `text`（pwdParam） · 返回：`code`, `data`（加密后密码数据）

#### private-lib/modify-password/ciphertext-get `POST` — 获取修改密码的密文
- 入参：`lockId`, `d1`, `d2` · 返回：`code`, `data`（加密后密码数据）

#### private-lib/get-k1-admin `GET` — 管理员身份认证，获取 C1
- 入参：`userId`, `randomStr`（16 字节）, `lockId` · 返回：`code`, `data`（C1 密文）

#### auth/admin/get — 管理员认证获取 C1 · ❌ 常量 AUTH_VERIFY_ADMIN_C1_GET 未找到调用点

---

## 十六、H5 / WebView 外链

| URL | 用途 |
| --- | --- |
| `https://www.populife.co/pages/populife-app-privacy-policy` | 隐私政策（英文） |
| `https://www.populife.co/pages/populife-app-privacy-policy-cn` | 隐私政策（中文） |
| `https://www.populife.co/products/smart-deadbolt` | 智能锁产品页 |
| `https://www.populife.co/products/gateway` | 网关产品页 |
| `https://www.populife.co/products/smart-keybox` | 钥匙盒产品页 |
| `https://item.taobao.com/item.htm?id=...` | 淘宝商品页（3 个商品） |

---

## 十七、未被引用的接口常量（清理参考）

以下常量在 `Urls.java` 中定义但源码未引用，可评估是否废弃删除：

| 常量 | 路径 | 备注 |
| --- | --- | --- |
| `APP_CHECK_UPDATE` | `app/version/get` | 版本更新，未调用 |
| `USER_LOCK_INFO` | `user/login/first/get` | 登录后拉锁信息，未调用 |
| `LOCK_EKEY_SEND` | `key/send` | 注释已标废弃 |
| `KEY_KEYBOARD_LIST` | `key/keyboard/list` | 注释已标废弃 |
| `KEY_KEYBOARD_LIST_V2_TOTALNUM` | `key/keyboard/list/v2/totalnum` | 密码总数，未调用 |
| `KEY_LIST_V2_TOTALNUM` | `key/list/v2/totalnum` | 钥匙总数，未调用 |
| `PAYPAL_REPAIR_PAY_ORDER` | `paypal/repair/pay/order` | 维修费下单，未调用 |
| `PAYPAL_SUCCESS_REPAIR_PAY_ORDER` | `paypal/success/repair/pay/order` | 维修费回调，未调用 |
| `LOCK_LIST` | `lock/list` | 唯一调用点已注释 |
| `AUTH_VERIFY_ADMIN_C1_GET`（SDK） | `auth/admin/get` | SDK 内未找到调用点 |

> "未被引用"指未搜索到直接引用；个别可能通过间接方式使用，删除前需再确认。
