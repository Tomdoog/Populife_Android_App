package com.populock.manhattan.sdk.entity;

import com.populock.manhattan.sdk.callback.AddCardCallback;
import com.populock.manhattan.sdk.callback.AddFingerprintCallback;
import com.populock.manhattan.sdk.callback.AddKeyboardPwdCallback;
import com.populock.manhattan.sdk.callback.AdminLockCallback;
import com.populock.manhattan.sdk.callback.AdminUnlockCallback;
import com.populock.manhattan.sdk.callback.AuthVerifyCallback;
import com.populock.manhattan.sdk.callback.ClearCardsCallback;
import com.populock.manhattan.sdk.callback.ClearFingersCallback;
import com.populock.manhattan.sdk.callback.DeleteCardCallback;
import com.populock.manhattan.sdk.callback.DeleteFingerprintCallback;
import com.populock.manhattan.sdk.callback.DeleteKeyboardPwdCallback;
import com.populock.manhattan.sdk.callback.DeleteLockCallback;
import com.populock.manhattan.sdk.callback.EnterAddFingerprintCallback;
import com.populock.manhattan.sdk.callback.FindMyDeviceCallback;
import com.populock.manhattan.sdk.callback.GetAutoLockTimeCallback;
import com.populock.manhattan.sdk.callback.GetBatteryLevelCallback;
import com.populock.manhattan.sdk.callback.GetFirmwareVersionCallback;
import com.populock.manhattan.sdk.callback.GetLockInfoCallback;
import com.populock.manhattan.sdk.callback.GetLockOperateLogCallback;
import com.populock.manhattan.sdk.callback.GetLockStatusCallback;
import com.populock.manhattan.sdk.callback.GetLockTimeCallback;
import com.populock.manhattan.sdk.callback.GetLockVersionCallback;
import com.populock.manhattan.sdk.callback.InitLockRequestCallback;
import com.populock.manhattan.sdk.callback.InitLockVerifyCallback;
import com.populock.manhattan.sdk.callback.ModifyCardPeriodCallback;
import com.populock.manhattan.sdk.callback.ModifyFingerprintPeriodCallback;
import com.populock.manhattan.sdk.callback.ModifyUserKeyboardPwdCallback;
import com.populock.manhattan.sdk.callback.ModifyUserKeyboardPwdPeriodCallback;
import com.populock.manhattan.sdk.callback.ResetEkeyCallback;
import com.populock.manhattan.sdk.callback.ResetKeyboardPwdCallback;
import com.populock.manhattan.sdk.callback.SetAdminKeyboardPwdCallback;
import com.populock.manhattan.sdk.callback.SetAutoLockTimeCallback;
import com.populock.manhattan.sdk.callback.SetLockTimeCallback;
import com.populock.manhattan.sdk.callback.UserLockCallback;
import com.populock.manhattan.sdk.callback.UserUnlockCallback;
import com.populock.manhattan.sdk.constant.LockOperation;

/**
 * Created by Jerry
 */
public class LockBleSession {
    private Integer timezoneRawOffset;
    public Integer getTimezoneRawOffset() { return timezoneRawOffset; }
    public void setTimezoneRawOffset(Integer value) { timezoneRawOffset = value; }


	private LockOperation mLockOperation;

	private InitLockRequestCallback mInitLockRequestCallback;
	private InitLockVerifyCallback mInitLockVerifyCallback;
	private GetBatteryLevelCallback mGetBatteryLevelCallback;
	private GetLockVersionCallback mGetLockVersionCallback;
	private GetLockInfoCallback mGetLockInfoCallback;
	private GetLockTimeCallback mGetLockTimeCallback;
	private GetLockOperateLogCallback mGetLockOperateLogCallback;
	private AuthVerifyCallback mAuthVerifyCallback;
	private AdminUnlockCallback mAdminUnlockCallback;
	private AdminLockCallback mAdminLockCallback;
	private UserUnlockCallback mUserUnlockCallback;
	private UserLockCallback mUserLockCallback;
	private DeleteLockCallback mDeleteLockCallback;
	private SetLockTimeCallback mSetLockTimeCallback;
	private GetFirmwareVersionCallback mGetFirmwareVersionCallback;
	private GetAutoLockTimeCallback mGetAutoLockTimeCallback;
	private GetLockStatusCallback mGetLockStatusCallback;
	private SetAutoLockTimeCallback mSetAutoLockTimeCallback;
	private SetAdminKeyboardPwdCallback mSetAdminKeyboardPwdCallback;
	private ModifyUserKeyboardPwdCallback mModifyUserKeyboardPwdCallback;
	private ModifyUserKeyboardPwdPeriodCallback mModifyUserKeyboardPwdPeriodCallback;
	private ResetKeyboardPwdCallback mResetKeyboardPwdCallback;
	private ResetEkeyCallback mResetEkeyCallback;
	private DeleteKeyboardPwdCallback mDeleteKeyboardPwdCallback;
	private AddKeyboardPwdCallback mAddKeyboardPwdCallback;
	private FindMyDeviceCallback mFindMyDeviceCallback;
	private EnterAddFingerprintCallback mEnterAddFingerprintCallback;
	private AddFingerprintCallback mAddFingerprintCallback;
	private AddCardCallback mAddCardCallback;
	private DeleteFingerprintCallback mDeleteFingerprintCallback;
	private DeleteCardCallback mDeleteCardCallback;
	private ClearFingersCallback mClearFingersCallback;
	private ClearCardsCallback mClearCardsCallback;
	private ModifyFingerprintPeriodCallback mModifyFingerprintPeriodCallback;
	private ModifyCardPeriodCallback mModifyCardPeriodCallback;
	private long lockTime;
	private int autoLockTime;
	private int pwdType;
	private String originalPwd;
	private String newPwd;
	private long startDate;
	private long endDate;
	private int keyType; // 电子钥匙类型（1限时，2永久，3单次,4周一循环，5周二循环，6周三循环，7周四循环，8周五循环，9周六循环，10周日循环，11每日循环，12工作日循环，13周末循环）
	private int cyclicStartHour; // 循环钥匙的开始小时
	private int cyclicEndHour; // 循环钥匙的结束小时
	private long createDate;
	private String fingerprintType;
	private String fingerprintPriority;
	private String cardType;
	private String cardPriority;
	private String fingerId;
	private String cardId;

	public static LockBleSession getInstance() {
		return new LockBleSession();
	}

	public LockOperation getLockOperation() {
		return mLockOperation;
	}

	public void setLockOperation(LockOperation lockOperation) {
		mLockOperation = lockOperation;
	}

	public InitLockRequestCallback getInitLockRequestCallback() {
		return mInitLockRequestCallback;
	}

	public void setInitLockRequestCallback(InitLockRequestCallback initLockRequestCallback) {
		mInitLockRequestCallback = initLockRequestCallback;
	}

	public InitLockVerifyCallback getInitLockVerifyCallback() {
		return mInitLockVerifyCallback;
	}

	public void setInitLockVerifyCallback(InitLockVerifyCallback initLockVerifyCallback) {
		mInitLockVerifyCallback = initLockVerifyCallback;
	}

	public GetBatteryLevelCallback getGetBatteryLevelCallback() {
		return mGetBatteryLevelCallback;
	}

	public void setGetBatteryLevelCallback(GetBatteryLevelCallback getBatteryLevelCallback) {
		mGetBatteryLevelCallback = getBatteryLevelCallback;
	}

	public GetLockVersionCallback getGetLockVersionCallback() {
		return mGetLockVersionCallback;
	}

	public void setGetLockVersionCallback(GetLockVersionCallback getLockVersionCallback) {
		mGetLockVersionCallback = getLockVersionCallback;
	}

	public GetLockInfoCallback getGetLockInfoCallback() {
		return mGetLockInfoCallback;
	}

	public void setGetLockInfoCallback(GetLockInfoCallback getLockInfoCallback) {
		mGetLockInfoCallback = getLockInfoCallback;
	}

	public GetLockTimeCallback getGetLockTimeCallback() {
		return mGetLockTimeCallback;
	}

	public void setGetLockTimeCallback(GetLockTimeCallback getLockTimeCallback) {
		mGetLockTimeCallback = getLockTimeCallback;
	}

	public GetLockOperateLogCallback getGetLockOperateLogCallback() {
		return mGetLockOperateLogCallback;
	}

	public void setGetLockOperateLogCallback(GetLockOperateLogCallback getLockOperateLogCallback) {
		mGetLockOperateLogCallback = getLockOperateLogCallback;
	}

	public AuthVerifyCallback getAuthVerifyCallback() {
		return mAuthVerifyCallback;
	}

	public void setAuthVerifyCallback(AuthVerifyCallback authVerifyCallback) {
		mAuthVerifyCallback = authVerifyCallback;
	}

	public AdminUnlockCallback getAdminUnlockCallback() {
		return mAdminUnlockCallback;
	}

	public void setAdminUnlockCallback(AdminUnlockCallback adminUnlockCallback) {
		mAdminUnlockCallback = adminUnlockCallback;
	}

	public AdminLockCallback getAdminLockCallback() {
		return mAdminLockCallback;
	}

	public void setAdminLockCallback(AdminLockCallback adminLockCallback) {
		mAdminLockCallback = adminLockCallback;
	}

	public UserUnlockCallback getUserUnlockCallback() {
		return mUserUnlockCallback;
	}

	public void setUserUnlockCallback(UserUnlockCallback userUnlockCallback) {
		mUserUnlockCallback = userUnlockCallback;
	}

	public UserLockCallback getUserLockCallback() {
		return mUserLockCallback;
	}

	public void setUserLockCallback(UserLockCallback userLockCallback) {
		mUserLockCallback = userLockCallback;
	}

	public DeleteLockCallback getDeleteLockCallback() {
		return mDeleteLockCallback;
	}

	public void setDeleteLockCallback(DeleteLockCallback deleteLockCallback) {
		mDeleteLockCallback = deleteLockCallback;
	}

	public SetLockTimeCallback getSetLockTimeCallback() {
		return mSetLockTimeCallback;
	}

	public void setSetLockTimeCallback(SetLockTimeCallback setLockTimeCallback) {
		mSetLockTimeCallback = setLockTimeCallback;
	}

	public GetFirmwareVersionCallback getGetFirmwareVersionCallback() {
		return mGetFirmwareVersionCallback;
	}

	public void setGetFirmwareVersionCallback(GetFirmwareVersionCallback getFirmwareVersionCallback) {
		mGetFirmwareVersionCallback = getFirmwareVersionCallback;
	}

	public GetAutoLockTimeCallback getGetAutoLockTimeCallback() {
		return mGetAutoLockTimeCallback;
	}

	public void setGetAutoLockTimeCallback(GetAutoLockTimeCallback getAutoLockTimeCallback) {
		mGetAutoLockTimeCallback = getAutoLockTimeCallback;
	}

	public GetLockStatusCallback getGetLockStatusCallback() {
		return mGetLockStatusCallback;
	}

	public void setGetLockStatusCallback(GetLockStatusCallback getLockStatusCallback) {
		mGetLockStatusCallback = getLockStatusCallback;
	}

	public SetAutoLockTimeCallback getSetAutoLockTimeCallback() {
		return mSetAutoLockTimeCallback;
	}

	public void setSetAutoLockTimeCallback(SetAutoLockTimeCallback setAutoLockTimeCallback) {
		mSetAutoLockTimeCallback = setAutoLockTimeCallback;
	}

	public SetAdminKeyboardPwdCallback getSetAdminKeyboardPwdCallback() {
		return mSetAdminKeyboardPwdCallback;
	}

	public void setSetAdminKeyboardPwdCallback(SetAdminKeyboardPwdCallback setAdminKeyboardPwdCallback) {
		mSetAdminKeyboardPwdCallback = setAdminKeyboardPwdCallback;
	}

	public ResetKeyboardPwdCallback getResetKeyboardPwdCallback() {
		return mResetKeyboardPwdCallback;
	}

	public void setResetKeyboardPwdCallback(ResetKeyboardPwdCallback resetKeyboardPwdCallback) {
		mResetKeyboardPwdCallback = resetKeyboardPwdCallback;
	}

	public ResetEkeyCallback getResetEkeyCallback() {
		return mResetEkeyCallback;
	}

	public void setResetEkeyCallback(ResetEkeyCallback resetEkeyCallback) {
		mResetEkeyCallback = resetEkeyCallback;
	}

	public DeleteKeyboardPwdCallback getDeleteKeyboardPwdCallback() {
		return mDeleteKeyboardPwdCallback;
	}

	public void setDeleteKeyboardPwdCallback(DeleteKeyboardPwdCallback deleteKeyboardPwdCallback) {
		mDeleteKeyboardPwdCallback = deleteKeyboardPwdCallback;
	}

	public AddKeyboardPwdCallback getAddKeyboardPwdCallback() {
		return mAddKeyboardPwdCallback;
	}

	public void setAddKeyboardPwdCallback(AddKeyboardPwdCallback addKeyboardPwdCallback) {
		mAddKeyboardPwdCallback = addKeyboardPwdCallback;
	}

	public FindMyDeviceCallback getFindMyDeviceCallback() {
		return mFindMyDeviceCallback;
	}

	public void setFindMyDeviceCallback(FindMyDeviceCallback findMyDeviceCallback) {
		mFindMyDeviceCallback = findMyDeviceCallback;
	}

	public void setmEnterAddFingerprintCallback(EnterAddFingerprintCallback mEnterAddFingerprintCallback) {
		this.mEnterAddFingerprintCallback = mEnterAddFingerprintCallback;
	}

	public EnterAddFingerprintCallback getmEnterAddFingerprintCallback() {
		return mEnterAddFingerprintCallback;
	}

	public void setmAddFingerprintCallback(AddFingerprintCallback mAddFingerprintCallback) {
		this.mAddFingerprintCallback = mAddFingerprintCallback;
	}

	public AddFingerprintCallback getmAddFingerprintCallback() {
		return mAddFingerprintCallback;
	}

	public void setmDeleteFingerprintCallback(DeleteFingerprintCallback mDeleteFingerprintCallback) {
		this.mDeleteFingerprintCallback = mDeleteFingerprintCallback;
	}


	public DeleteFingerprintCallback getmDeleteFingerprintCallback() {
		return mDeleteFingerprintCallback;
	}

	public void setmAddCardCallback(AddCardCallback mAddCardCallback) {
		this.mAddCardCallback = mAddCardCallback;
	}

	public AddCardCallback getmAddCardCallback() {
		return mAddCardCallback;
	}

	public void setmDeleteCardCallback(DeleteCardCallback mDeleteCardCallback) {
		this.mDeleteCardCallback = mDeleteCardCallback;
	}

	public DeleteCardCallback getmDeleteCardCallback() {
		return mDeleteCardCallback;
	}

	public void setmClearCardsCallback(ClearCardsCallback mClearCardsCallback) {
		this.mClearCardsCallback = mClearCardsCallback;
	}

	public ClearCardsCallback getmClearCardsCallback() {
		return mClearCardsCallback;
	}

	public void setmClearFingersCallback(ClearFingersCallback mClearFingersCallback) {
		this.mClearFingersCallback = mClearFingersCallback;
	}

	public ClearFingersCallback getmClearFingersCallback() {
		return mClearFingersCallback;
	}


	public long getLockTime() {
		return lockTime;
	}

	public void setLockTime(long lockTime) {
		this.lockTime = lockTime;
	}

	public int getAutoLockTime() {
		return autoLockTime;
	}

	public void setAutoLockTime(int autoLockTime) {
		this.autoLockTime = autoLockTime;
	}

	public int getPwdType() {
		return pwdType;
	}

	public void setPwdType(int pwdType) {
		this.pwdType = pwdType;
	}

	public String getOriginalPwd() {
		return originalPwd;
	}

	public void setOriginalPwd(String originalPwd) {
		this.originalPwd = originalPwd;
	}

	public String getNewPwd() {
		return newPwd;
	}

	public void setNewPwd(String newPwd) {
		this.newPwd = newPwd;
	}

	public long getStartDate() {
		return startDate;
	}

	public void setStartDate(long startDate) {
		this.startDate = startDate;
	}

	public long getEndDate() {
		return endDate;
	}

	public void setEndDate(long endDate) {
		this.endDate = endDate;
	}

	public int getKeyType() {
		return keyType;
	}

	public void setKeyType(int keyType) {
		this.keyType = keyType;
	}

	public int getCyclicStartHour() {
		return cyclicStartHour;
	}

	public void setCyclicStartHour(int cyclicStartHour) {
		this.cyclicStartHour = cyclicStartHour;
	}

	public int getCyclicEndHour() {
		return cyclicEndHour;
	}

	public void setCyclicEndHour(int cyclicEndHour) {
		this.cyclicEndHour = cyclicEndHour;
	}

	public long getCreateDate() {
		return createDate;
	}

	public void setCreateDate(long createDate) {
		this.createDate = createDate;
	}

	public void setFingerprintType(String fingerprintType) {
		this.fingerprintType = fingerprintType;
	}
	public String getFingerprintType() {
		return fingerprintType;
	}

	public void setFingerprintPriority(String fingerprintPriority) {
		this.fingerprintPriority = fingerprintPriority;
	}

	public String getFingerprintPriority() {
		return fingerprintPriority;
	}

	public void setCardType(String cardType) {
		this.cardType = cardType;
	}

	public String getCardType() {
		return cardType;
	}

	public void setCardPriority(String cardPriority) {
		this.cardPriority = cardPriority;
	}

	public String getCardPriority() {
		return cardPriority;
	}

	public void setFingerId(String fingerId) {
		this.fingerId = fingerId;
	}

	public String getFingerId() {
		return fingerId;
	}

	public void setCardId(String cardId) {
		this.cardId = cardId;
	}

	public String getCardId() {
		return cardId;
	}

	public void setmModifyUserKeyboardPwdCallback(ModifyUserKeyboardPwdCallback mModifyUserKeyboardPwdCallback) {
		this.mModifyUserKeyboardPwdCallback = mModifyUserKeyboardPwdCallback;
	}

	public ModifyUserKeyboardPwdCallback getmModifyUserKeyboardPwdCallback() {
		return mModifyUserKeyboardPwdCallback;
	}

	public void setmModifyUserKeyboardPwdPeriodCallback(ModifyUserKeyboardPwdPeriodCallback mModifyUserKeyboardPwdPeriodCallback) {
		this.mModifyUserKeyboardPwdPeriodCallback = mModifyUserKeyboardPwdPeriodCallback;
	}

	public ModifyUserKeyboardPwdPeriodCallback getmModifyUserKeyboardPwdPeriodCallback() {
		return mModifyUserKeyboardPwdPeriodCallback;
	}

	public void setmModifyFingerprintPeriodCallback(ModifyFingerprintPeriodCallback mModifyFingerprintPeriodCallback) {
		this.mModifyFingerprintPeriodCallback = mModifyFingerprintPeriodCallback;
	}

	public ModifyFingerprintPeriodCallback getmModifyFingerprintPeriodCallback() {
		return mModifyFingerprintPeriodCallback;
	}

	public void setmModifyCardPeriodCallback(ModifyCardPeriodCallback mModifyCardPeriodCallback) {
		this.mModifyCardPeriodCallback = mModifyCardPeriodCallback;
	}

	public ModifyCardPeriodCallback getmModifyCardPeriodCallback() {
		return mModifyCardPeriodCallback;
	}
}
