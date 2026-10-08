# UTC-7 首次配对后循环密码失效：后端补丁草案

状态：基于 `/Users/wangdi/Downloads/代码` 的静态快照制作，未修改该快照，未部署远端。补丁应用检查和独立 Java 8 时间换算回归已通过；没有完成整个后端工程编译或部署后实体锁复测。

## 真机证据

测试锁 S8534_c6d9f2。先解除绑定，手机切到 America/Los_Angeles（当时 UTC-7），从未绑定状态重新配对。绑定自动校时、锁回读、服务器时区回查通过，保存偏移 `-25200000`。全程没有手动校时。

- 首条限时密码：当地 2026-10-07 22:00–23:00，`timeZone=-7`；实体锁开锁成功。
- 循环密码：当地每日 22:00–23:00，`keyboardPwdType=6,timeZone=-7`；重复输入两三次均“开锁失败”。
- 循环密码生成时刻：`1791437434000`，当地 2026-10-07 22:30:34，北京时间 2026-10-08 13:30:34。

| 字段 | 实际服务器记录 | 应有值 |
| --- | --- | --- |
| startDate | `1791522000000`：当地 10 月 8 日 22:00 | `1791435600000`：当地 10 月 7 日 22:00 |
| endDate | `1791525600000`：当地 10 月 8 日 23:00 | `1791439200000`：当地 10 月 7 日 23:00 |

实际窗口整体晚了 86400000 毫秒，列表也将它归为“待生效”。上面应有值与已成功开锁的限时密码服务器记录一致。

## 静态代码定位

`populife-app-v2/.../controller/KeyboardController.java` 原始 281–285 行：先 `new Date()`，调用 `getPeriodDay()` 按服务器默认时区决定日期，再 `DateFormatUtils.format()` 拼接 HH:mm。每日循环分支在原始 331–334 行按默认时区截断至当天。

随后 `populife-core-v2/.../service/KeyboardService.java` 原始 138、150 行，用请求的 `timeZone` 解析已拼好的完整日期，再将时间戳存库并提交科技侠。

当服务器为 10 月 8 日而 UTC-7 锁当地仍是 10 月 7 日时，上述实现确定会得到真机记录中的错误窗口。这份快照没有远端版本标识，因此结论是“快照中的缺陷与远端实测完全吻合”，仍需后端维护者核对部署版本。

SaaS 与 Open API 两个控制器存在同样的重复逻辑，补丁一并调整，避免其他入口重新生成错误日期。

## 补丁内容

`fix-cyclic-password-lock-timezone.patch` 修改 5 个现有源文件，并新增纯 Java 8 的 `CyclicPasswordTime`：

1. 日期、星期选择均以锁保存的 `timezoneRawOffSet` 为准。
2. 拼好当地日期后，转换时间戳也使用同一个保存偏移；循环密码不再依赖请求手机的时区或服务器默认时区。
3. 锁时区缺失或非法时返回明确参数错误，不默认为 UTC。
4. 保留既有循环规则：每日、工作日、周末以及下一个或当天指定星期；不扩展跨午夜时段规则。
5. 不改变非循环密码的生成逻辑，不重置现有锁或密码。

这不是对所有时间接口的全面改造。现有部分接口参数仍为 `Integer timeZone`；时间工具的非整小时测试不等于这些接口已经接受小数时区。跨夏令时自动调整也不在本补丁范围。

## 审阅与本地验证

先对照 `source-snapshot.sha256` 核实快照版本，在实际后端仓库根目录审阅并应用补丁（路径按存放位置调整）：

```sh
git apply --check /path/to/fix-cyclic-password-lock-timezone.patch
git apply /path/to/fix-cyclic-password-lock-timezone.patch
```

独立回归测试不访问网络、数据库或真实锁，只需要 JDK 8 及以上：

```sh
mkdir -p /tmp/populife-cycle-regression
javac -d /tmp/populife-cycle-regression \
  populife-core-v2/src/main/java/com/igloo/smarthome/utils/CyclicPasswordTime.java \
  /path/to/regression/CyclicPasswordTimeRegression.java
java -cp /tmp/populife-cycle-regression CyclicPasswordTimeRegression
```

已通过 59 个断言：复现实测多一天的窗口；在上海、UTC、洛杉矶、UTC+14 四种服务器默认时区下得到相同正确结果；覆盖全部指定星期、周末/工作日、跨日、非整小时偏移、无效日期与缺失时区拒绝。

仍须在真实后端工程运行完整构建、接口集成测试，检查框架异常响应，并确认所有部署入口使用补丁版本。本机没有 Maven 命令，未执行整个静态后端工程构建。

## 部署后的验收

1. 在测试环境部署，经接口回查确认这把锁偏移仍为 `-25200000`。
2. 重新生成当前当地时段内的每日循环密码。核对返回/存储的 startDate、endDate 与 UTC-7 当地日期一致。
3. 在实体锁键盘输入新密码，应直接开锁；补测一个按当地星期生效的循环规则。
4. 将手机切到上海时区后再生成，仍应以锁保存的 UTC-7 计算。
5. 保留三端时区、接口时间戳与实体锁结果后才能标记全部验收通过。

旧失败密码已经按错误窗口生成，不能只改数据库日期宣称修复；应生成新密码完成验证，再按正常流程清理旧测试密码。
