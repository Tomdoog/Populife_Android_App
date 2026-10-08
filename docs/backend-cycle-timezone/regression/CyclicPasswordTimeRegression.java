import com.igloo.smarthome.utils.CyclicPasswordTime;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.TimeZone;

/** 无第三方依赖的 Java 8 回归用例；不调用服务器、不生成真实密码。 */
public final class CyclicPasswordTimeRegression {
    private static int assertions;

    public static void main(String[] args) {
        TimeZone original = TimeZone.getDefault();
        try {
            long observedNow = 1791437434000L;
            // 复现静态代码的旧行为：取服务器“今天”，再解释成锁的当地时间。
            String serverToday = Instant.ofEpochMilli(observedNow)
                    .atZone(ZoneId.of("Asia/Shanghai")).toLocalDate().toString();
            equal(1791522000000L, CyclicPasswordTime.toEpochMillis(serverToday + " 22:00", -25200000L));
            // 修复后的窗口必须与已成功的首条限时密码完全相同。
            for (String serverZone : new String[]{"Asia/Shanghai", "UTC", "America/Los_Angeles", "Pacific/Kiritimati"}) {
                TimeZone.setDefault(TimeZone.getTimeZone(serverZone));
                String prefix = CyclicPasswordTime.datePrefix(6, observedNow, -25200000L);
                equal("2026-10-07 ", prefix);
                equal(1791435600000L, CyclicPasswordTime.toEpochMillis(prefix + "22:00", -25200000L));
                equal(1791439200000L, CyclicPasswordTime.toEpochMillis(prefix + "23:00", -25200000L));
                check(CyclicPasswordTime.toEpochMillis(prefix + "22:00", -25200000L) < observedNow);
                check(CyclicPasswordTime.toEpochMillis(prefix + "23:00", -25200000L) > observedNow);
            }
            // 同一瞬间的当地星期不同，按锁的星期选择指定日、工作日和周末。
            equal("2026-10-07 ", CyclicPasswordTime.datePrefix(10, observedNow, -25200000L));
            equal("2026-10-14 ", CyclicPasswordTime.datePrefix(10, observedNow, 28800000L));
            equal("2026-10-10 ", CyclicPasswordTime.datePrefix(5, observedNow, -25200000L));
            equal("2026-10-07 ", CyclicPasswordTime.datePrefix(7, observedNow, -25200000L));
            long sundayUtc = Instant.parse("2026-10-11T01:00:00Z").toEpochMilli();
            equal("2026-10-10 ", CyclicPasswordTime.datePrefix(5, sundayUtc, -25200000L));
            equal("2026-10-12 ", CyclicPasswordTime.datePrefix(7, sundayUtc, -25200000L));
            // 所有指定星期类型均命中正确的当地星期，且不早于当地今天。
            for (int type = 8; type <= 14; type++) {
                LocalDate day = LocalDate.parse(CyclicPasswordTime.datePrefix(type, observedNow, -25200000L).trim());
                equal(type - 7, day.getDayOfWeek().getValue());
                check(!day.isBefore(LocalDate.of(2026, 10, 7)));
                check(day.isBefore(LocalDate.of(2026, 10, 14)));
            }
            // 正偏移跨日、UTC 与非整小时时区。
            long boundary = Instant.parse("2026-10-07T23:30:00Z").toEpochMilli();
            equal("2026-10-08 ", CyclicPasswordTime.datePrefix(6, boundary, 20700000L));
            equal("2026-10-07 ", CyclicPasswordTime.datePrefix(6, boundary, 0L));
            equal(Instant.parse("2026-10-08T03:15:00Z").toEpochMilli(),
                    CyclicPasswordTime.toEpochMillis("2026-10-08 09:00", 20700000L));
            // 绑定的固定偏移不随服务器冬令时变化。
            equal(Instant.parse("2026-12-01T16:00:00Z").toEpochMilli(),
                    CyclicPasswordTime.toEpochMillis("2026-12-01 09:00", -25200000L));
            rejects(() -> CyclicPasswordTime.datePrefix(6, observedNow, null));
            rejects(() -> CyclicPasswordTime.datePrefix(6, observedNow, 1L));
            rejects(() -> CyclicPasswordTime.datePrefix(6, observedNow, Long.MAX_VALUE));
            rejects(() -> CyclicPasswordTime.datePrefix(3, observedNow, 0L));
            rejects(() -> CyclicPasswordTime.toEpochMillis("2026-02-30 09:00", 0L));
            check(!CyclicPasswordTime.isCyclic(null));
            check(!CyclicPasswordTime.isCyclic(3));
            System.out.println("PASS: " + assertions + " assertions; old +1 day failure reproduced, fixed window matches live limited-password window.");
        } finally {
            TimeZone.setDefault(original);
        }
    }

    private static void equal(Object expected, Object actual) {
        assertions++;
        if (!expected.equals(actual)) throw new AssertionError("Expected " + expected + ", got " + actual);
    }

    private static void check(boolean condition) {
        assertions++;
        if (!condition) throw new AssertionError("Condition failed");
    }

    private static void rejects(Runnable operation) {
        assertions++;
        try { operation.run(); }
        catch (RuntimeException expected) { return; }
        throw new AssertionError("Expected rejection");
    }
}
