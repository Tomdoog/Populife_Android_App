package com.populstay.populife;

import androidx.test.runner.AndroidJUnit4;
import com.ttlock.bl.sdk.entity.LockData;
import com.ttlock.bl.sdk.util.EncryptionUtil;
import com.populstay.populife.entity.Key;
import android.os.Parcel;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class BindingTimeDataTest {
    @Test public void sdkEncodingPreservesBindingOffsetAndPasswordEpoch() {
        for (int offset : new int[]{28800000, -25200000, 19800000, 20700000, 0}) {
            LockData data = new LockData();
            data.setLockMac("00:11:22:33:44:55");
            // 初始化返回的是管理员凭据；缺少该字段会被 SDK 当成需要签名的分享钥匙。
            data.setAdminPwd("test-admin-credential");
            data.setTimestamp(1791424800000L);
            data.setTimezoneRawOffset(offset);
            data.setPwdInfo("test-password-epoch");
            LockData restored = EncryptionUtil.parseLockData(data.encodeLockData());
            assertNotNull(restored);
            assertEquals(offset, restored.getTimezoneRawOffset());
            assertEquals(1791424800000L, restored.getTimestamp());
            assertEquals("test-password-epoch", restored.getPwdInfo());
        }
    }

    @Test public void keyParcelPreservesTimezoneWhenOpeningPasswordScreen() {
        Key key = new Key();
        key.setTimezoneRawOffset(-25200000);
        Parcel parcel = Parcel.obtain();
        try {
            key.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);
            Key restored = Key.CREATOR.createFromParcel(parcel);
            assertEquals(-25200000, restored.getTimezoneRawOffset());
        } finally { parcel.recycle(); }
    }
}
