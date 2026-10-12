package com.populstay.populife.activity;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.populock.manhattan.sdk.constant.LockOperation;
import com.populstay.populife.R;
import com.populstay.populife.app.MyApplication;
import com.populstay.populife.base.BaseActivity;
import com.populstay.populife.common.Urls;
import com.populstay.populife.entity.Key;
import com.populstay.populife.entity.Passcode;
import com.populstay.populife.enumtype.Operation;
import com.populstay.populife.eventbus.Event;
import com.populstay.populife.keypwdmanage.KeyPwdConstant;
import com.populstay.populife.keypwdmanage.entity.KeyPwd;
import com.populstay.populife.lock.ILockDeletePasscode;
import com.populstay.populife.lock.ILockFingerprintDelete;
import com.populstay.populife.lock.ILockIcCardDelete;
import com.populstay.populife.lock.ILockModifyPasscode;
import com.populstay.populife.manhattanlock.MHILockDeleteCard;
import com.populstay.populife.manhattanlock.MHILockDeleteFingerprint;
import com.populstay.populife.manhattanlock.MHILockDeletePasscode;
import com.populstay.populife.manhattanlock.MHILockModifyPasscode;
import com.populstay.populife.net.RestClient;
import com.populstay.populife.net.callback.IError;
import com.populstay.populife.net.callback.IFailure;
import com.populstay.populife.net.callback.ISuccess;
import com.populstay.populife.ui.loader.PeachLoader;
import com.populstay.populife.util.Utils;
import com.populstay.populife.util.date.DateUtil;
import com.populstay.populife.util.date.LockTime;
import com.populstay.populife.util.dialog.DialogUtil;
import com.populstay.populife.util.log.PeachLogger;
import com.populstay.populife.util.storage.PeachPreference;
import com.populstay.populife.util.string.StringUtil;
import com.ttlock.bl.sdk.callback.DeleteFingerprintCallback;
import com.ttlock.bl.sdk.callback.DeleteICCardCallback;
import com.ttlock.bl.sdk.callback.DeletePasscodeCallback;
import com.ttlock.bl.sdk.callback.ModifyPasscodeCallback;
import com.ttlock.bl.sdk.constant.FeatureValue;
import com.ttlock.bl.sdk.entity.Error;
import com.ttlock.bl.sdk.entity.LockError;
import com.ttlock.bl.sdk.util.DigitUtil;
import com.ttlock.bl.sdk.util.FeatureValueUtil;

import org.greenrobot.eventbus.EventBus;

import java.util.HashMap;
import java.util.WeakHashMap;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatButton;
import androidx.cardview.widget.CardView;

import static com.populstay.populife.app.MyApplication.mTTLockAPI;
import static com.populstay.populife.app.MyApplication.sPPLOCK;
import static com.populstay.populife.keypwdmanage.KeyPwdListFragment.TYPE_KEY_PWD_FP_CARD;

public class PasscodeDetailActivity extends BaseActivity implements View.OnClickListener {

	public static final String KEY_PASSCODE = "key_passcode";
	private static final String KEY_CATEGORY = "key_category";
	private static final String KEY_KEY = "KEY_KEY";
	private static final int REQUEST_CODE_MODIFY_PASSCODE_PERIOD = 1;

	private TextView mTvPageTitle, mTvSend, mTvPasscode, mTvNameTitle, mTvName, mTvRemark, mTvValidPeriod,
			mTvStartTime, mTvEndTime, mTvSender, mTvSendingTime, mTvDelete, tv_pwd_type, tv_status, tv_create_custom_pwd_tips_2, infoText;
    private CardView cv_create_custom_pwd_tips;
	private LinearLayout mLlPasscode, mLlName, mLlRemark, mLlValidPeriod, mLlRecord, ll_create_info, mLlPwdType;
	private ImageView mIvPasscodeMore, mIvValidPeriodMore;
	private AlertDialog DIALOG;
	private EditText mEtDialogInput;

	private Key mKey = MyApplication.CURRENT_KEY;
	private KeyPwd mKeyPwd;
	private int mModifyPasscodeType; // 修改密码的类型（0 修改密码，1 修改名称（密码/指纹/门卡），2 修改指纹备注）
	private int mCategory;// 分类，1：可使用,2：待激活,3：已失效
	private int mAccessType = KeyPwdConstant.IType.TYPE_PWD; // 1：钥匙，2：密码，3：指纹，4：门卡
	private boolean mIsOperationSuccess;
	private boolean mIsLockOperationSuccess;

	/**
	 * 启动当前 activity
	 *
	 * @param context  上下文
	 * @param passcode 键盘密码
	 */
	public static void actionStart(Context context, Passcode passcode) {
		Intent intent = new Intent(context, PasscodeDetailActivity.class);
		intent.putExtra(KEY_PASSCODE, passcode);
		context.startActivity(intent);
	}

	public static void actionStart(Context context, int type, KeyPwd keyPwd, int category, Key key) {
		Intent intent = new Intent(context, PasscodeDetailActivity.class);
		intent.putExtra(TYPE_KEY_PWD_FP_CARD, type);
		intent.putExtra(KEY_PASSCODE, keyPwd);
		intent.putExtra(KEY_CATEGORY, category);
		intent.putExtra(KEY_KEY, key);
		context.startActivity(intent);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_passcode_detail);
		getIntentData();
		initView();
		initListener();
	}

	private void getIntentData() {
		Intent data = getIntent();
		mAccessType = data.getIntExtra(TYPE_KEY_PWD_FP_CARD, KeyPwdConstant.IType.TYPE_PWD);
		mKeyPwd = data.getParcelableExtra(KEY_PASSCODE);
		mCategory = data.getIntExtra(KEY_CATEGORY, KeyPwdConstant.IKeyPwdCategory.KEY_PWD_CATEGORY_AVAILABLE);
		if (data.hasExtra(KEY_KEY)){
			mKey = getIntent().getParcelableExtra(KEY_KEY);
			MyApplication.CURRENT_KEY = mKey;
		}
	}

	private void initView() {
		mTvPageTitle = findViewById(R.id.page_title);
		mTvSend = findViewById(R.id.page_action);
		mTvSend.setText(R.string.share);
		mTvSend.setVisibility(View.GONE);
		mTvPasscode = findViewById(R.id.tv_passcode_detail_passcode);
		mTvNameTitle = findViewById(R.id.tv_passcode_detail_name_title);
		mTvName = findViewById(R.id.tv_passcode_detail_name);
		mTvRemark = findViewById(R.id.tv_passcode_detail_remark);
		mTvValidPeriod = findViewById(R.id.tv_passcode_detail_valid_period);
		mTvStartTime = findViewById(R.id.tv_passcode_detail_start_time);
		mTvEndTime = findViewById(R.id.tv_passcode_detail_end_time);
		mTvSender = findViewById(R.id.tv_passcode_detail_sender);
		mTvSendingTime = findViewById(R.id.tv_passcode_detail_sending_time);
		mTvDelete = findViewById(R.id.tv_passcode_detail_delete);
		mLlPasscode = findViewById(R.id.ll_passcode_detail_passcode);
		mLlName = findViewById(R.id.ll_passcode_detail_name);
		mLlRemark = findViewById(R.id.ll_passcode_detail_remark);
		mLlValidPeriod = findViewById(R.id.ll_passcode_detail_valid_period);
		mLlRecord = findViewById(R.id.ll_passcode_detail_records);
		ll_create_info = findViewById(R.id.ll_create_info);
		mLlPwdType = findViewById(R.id.ll_pwd_type);
		mIvPasscodeMore = findViewById(R.id.iv_passcode_detail_passcode_more);
		mIvValidPeriodMore = findViewById(R.id.iv_passcode_detail_valid_period_more);
		tv_pwd_type = findViewById(R.id.tv_pwd_type);
		tv_status = findViewById(R.id.tv_status);
        tv_create_custom_pwd_tips_2 = findViewById(R.id.tv_create_custom_pwd_tips_2);
        cv_create_custom_pwd_tips = findViewById(R.id.cv_create_custom_pwd_tips);
        infoText = findViewById(R.id.infoText);

		refreshUI();
	}


	@SuppressLint("SetTextI18n")
	private void refreshUI() {
		switch (mCategory) {
			case KeyPwdConstant.IKeyPwdCategory.KEY_PWD_CATEGORY_AVAILABLE:
				tv_status.setText(getResources().getString(R.string.key_pwd_status_available));
				break;

			case KeyPwdConstant.IKeyPwdCategory.KEY_PWD_CATEGORY_NOT_ACTIVATED:
				tv_status.setText(getResources().getString(R.string.key_pwd_status_not_activated));
				break;

			case KeyPwdConstant.IKeyPwdCategory.KEY_PWD_CATEGORY_INVALID:
				tv_status.setText(getResources().getString(R.string.key_pwd_status_invalid));
				break;

			default:
				break;
		}

		mTvPasscode.setText(mKeyPwd.getKeyboardPwd());
		mTvName.setText(mKeyPwd.getAlias());
		if (!TextUtils.isEmpty(mKeyPwd.getSendUser())) {
			mTvSender.setText(mKeyPwd.getSendUser());
		}
		mTvSendingTime.setText(DateUtil.getDateToString(mKeyPwd.getCreateDate(), DateUtil.DATE_FORMAT_YYYY_MM_DD_HH_MM_SS));

		switch (mAccessType) {
			case KeyPwdConstant.IType.TYPE_PWD: // 数字密码
				mTvPageTitle.setText(R.string.pin_code_details);

				/**
				 * type	value
				 * One-time			1
				 * Permanent		2
				 * Period			3
				 * Clear			4
				 * Weekend Cyclic	5
				 * Daily Cyclic		6
				 * Workday Cyclic	7
				 * Monday Cyclic		8
				 * Tuesday Cyclic	9
				 * Wednesday Cyclic	10
				 * Thursday Cyclic	11
				 * Friday Cyclic		12
				 * Saturday Cyclic	13
				 * Sunday Cyclic		14
				 */
				int passcodeType = mKeyPwd.getKeyboardPwdType();
				switch (passcodeType) {
					// 单次
					case 1:
						tv_pwd_type.setText(getResources().getString(R.string.key_pwd_one_time));
						mLlPasscode.setEnabled(false);
						mLlName.setEnabled(true);
						mLlValidPeriod.setEnabled(false);
						mIvPasscodeMore.setVisibility(View.GONE);
						mIvValidPeriodMore.setVisibility(View.GONE);
						mTvValidPeriod.setVisibility(View.GONE);
						mTvStartTime.setVisibility(View.VISIBLE);
						mTvEndTime.setVisibility(View.VISIBLE);
						mTvStartTime.setText(DateUtil.getDateToString(mKeyPwd.getCreateDate(), "yyyy-MM-dd HH:mm"));
						mTvEndTime.setText(DateUtil.getDateToString(mKeyPwd.getCreateDate() + 3600 * 1000 * 6, "yyyy-MM-dd HH:mm"));
						break;

					// 永久
                    case 16:
					case 2:
						tv_pwd_type.setText(getResources().getString(R.string.key_pwd_permanent));
                        mLlPasscode.setEnabled(true);
						mLlName.setEnabled(true);
						mLlValidPeriod.setEnabled(false);
						mIvPasscodeMore.setVisibility(View.VISIBLE);
						mIvValidPeriodMore.setVisibility(View.GONE);
						mTvValidPeriod.setVisibility(View.VISIBLE);
						mTvValidPeriod.setText(R.string.permanent);
						mTvStartTime.setVisibility(View.GONE);
						mTvEndTime.setVisibility(View.GONE);
                        showTipVisible();
						break;

					// 限时
					case 3:
						tv_pwd_type.setText(getResources().getString(R.string.key_pwd_period));
                        mLlPasscode.setEnabled(true);
						mLlName.setEnabled(true);
						mLlValidPeriod.setEnabled(true);
						mIvPasscodeMore.setVisibility(View.VISIBLE);
						mIvValidPeriodMore.setVisibility(View.VISIBLE);
						mTvValidPeriod.setVisibility(View.GONE);
						mTvStartTime.setVisibility(View.VISIBLE);
						mTvEndTime.setVisibility(View.VISIBLE);
						mTvStartTime.setText(DateUtil.getDateToString(mKeyPwd.getStartDate(), "yyyy-MM-dd HH:mm"));
						mTvEndTime.setText(DateUtil.getDateToString(mKeyPwd.getEndDate(), "yyyy-MM-dd HH:mm"));
						break;
					// 清空
					case 4:
						tv_pwd_type.setText(getResources().getString(R.string.key_pwd_clear));
						mLlPasscode.setEnabled(false);
						mLlName.setEnabled(true);
						mLlValidPeriod.setEnabled(false);
						mIvPasscodeMore.setVisibility(View.GONE);
						mIvValidPeriodMore.setVisibility(View.GONE);
						mTvValidPeriod.setVisibility(View.GONE);
						mTvStartTime.setVisibility(View.VISIBLE);
						mTvEndTime.setVisibility(View.VISIBLE);
						mTvStartTime.setText(DateUtil.getDateToString(mKeyPwd.getCreateDate(), "yyyy-MM-dd HH:mm"));
						mTvEndTime.setText(DateUtil.getDateToString(mKeyPwd.getCreateDate() + 3600 * 1000 * 24, "yyyy-MM-dd HH:mm"));
						mLlRecord.setVisibility(View.VISIBLE);
						break;
					// 循环
					case 5:
					case 6:
					case 7:
					case 8:
					case 9:
					case 10:
					case 11:
					case 12:
					case 13:
					case 14:
						tv_pwd_type.setText(getResources().getString(R.string.key_pwd_cyclic));
                        mLlPasscode.setEnabled(true);
						mLlName.setEnabled(true);
						mLlValidPeriod.setEnabled(false);
						mIvPasscodeMore.setVisibility(View.VISIBLE);
						mIvValidPeriodMore.setVisibility(View.GONE);
						mTvValidPeriod.setVisibility(View.VISIBLE);
						mTvStartTime.setVisibility(View.GONE);
						mTvEndTime.setVisibility(View.GONE);
						String cyclicMode = "";
						String cyclicTime = " " + DateUtil.getDateToString(mKeyPwd.getStartDate(), "HH:00") + "-"
								+ DateUtil.getDateToString(mKeyPwd.getEndDate(), "HH:00");
						switch (passcodeType) {
							case 5:
								cyclicMode = getString(R.string.weekend);
								break;

							case 6:
								cyclicMode = getString(R.string.daily);
								break;

							case 7:
								cyclicMode = getString(R.string.workday);
								break;

							case 8:
								cyclicMode = getString(R.string.monday);
								break;

							case 9:
								cyclicMode = getString(R.string.tuesday);
								break;

							case 10:
								cyclicMode = getString(R.string.wednesday);
								break;

							case 11:
								cyclicMode = getString(R.string.thursday);
								break;

							case 12:
								cyclicMode = getString(R.string.friday);
								break;

							case 13:
								cyclicMode = getString(R.string.saturday);
								break;

							case 14:
								cyclicMode = getString(R.string.sunday);
								break;

							default:
								break;
						}
						mTvValidPeriod.setText(cyclicMode + cyclicTime);
						break;
					// 自定义密码
					case 15:
						tv_pwd_type.setText(getResources().getString(R.string.key_pwd_custom));
						mLlPasscode.setEnabled(true);
						mLlName.setEnabled(true);
						mLlValidPeriod.setEnabled(true);
						mIvPasscodeMore.setVisibility(View.VISIBLE);
						mIvValidPeriodMore.setVisibility(View.VISIBLE);
						mTvValidPeriod.setVisibility(View.GONE);
						mTvStartTime.setVisibility(View.VISIBLE);
						mTvEndTime.setVisibility(View.VISIBLE);
						mTvStartTime.setText(DateUtil.getDateToString(mKeyPwd.getStartDate(), "yyyy-MM-dd HH:mm"));
						mTvEndTime.setText(DateUtil.getDateToString(mKeyPwd.getEndDate(), "yyyy-MM-dd HH:mm"));
						break;
					// 管理员密码
					case -1:
						tv_pwd_type.setText(getResources().getString(R.string.administrator_code));
						mTvName.setText(getAdminCodeName());
						mLlPasscode.setEnabled(true);
						mLlName.setEnabled(true);
						mIvPasscodeMore.setVisibility(View.VISIBLE);
						mIvPasscodeMore.setVisibility(View.VISIBLE);

						mTvDelete.setVisibility(View.GONE);
						ll_create_info.setVisibility(View.GONE);
						mLlValidPeriod.setVisibility(View.GONE);
						mLlValidPeriod.setEnabled(false);
						mIvValidPeriodMore.setVisibility(View.GONE);
						mTvValidPeriod.setVisibility(View.GONE);
						mTvStartTime.setVisibility(View.GONE);
						mTvEndTime.setVisibility(View.GONE);
				/*mTvStartTime.setText(DateUtil.getDateToString(mPasscode.getStartDate(), "yyyy-MM-dd HH:mm"));
				mTvEndTime.setText(DateUtil.getDateToString(mPasscode.getEndDate(), "yyyy-MM-dd HH:mm"));*/
						break;

				}
                showTipVisible();
				break;

			case KeyPwdConstant.IType.TYPE_FINGERPRINT: // 指纹
				mTvPageTitle.setText(R.string.fingerprint_details);
				mLlPwdType.setVisibility(View.GONE);
				mLlPasscode.setVisibility(View.GONE);
				mLlName.setEnabled(true);
				mTvNameTitle.setText(R.string.fingerprint_user_name);
				mLlRemark.setVisibility(View.VISIBLE);
				mTvRemark.setText(mKeyPwd.getRemark());
				mLlValidPeriod.setEnabled(true);
				mIvValidPeriodMore.setVisibility(View.VISIBLE);
				int cardType = mKeyPwd.getType();
				if (cardType == 1) { // 限时
					mTvValidPeriod.setVisibility(View.GONE);
					mTvStartTime.setVisibility(View.VISIBLE);
					mTvEndTime.setVisibility(View.VISIBLE);
					mTvStartTime.setText(DateUtil.getDateToString(mKeyPwd.getStartDate(), DateUtil.DATE_FORMAT_YYYY_MM_DD_HH_MM));
					mTvEndTime.setText(DateUtil.getDateToString(mKeyPwd.getEndDate(), DateUtil.DATE_FORMAT_YYYY_MM_DD_HH_MM));
				} else if (cardType == 2) { // 永久
					mTvValidPeriod.setVisibility(View.VISIBLE);
					mTvValidPeriod.setText(R.string.permanent);
					mTvStartTime.setVisibility(View.GONE);
					mTvEndTime.setVisibility(View.GONE);
				}
				break;

			case KeyPwdConstant.IType.TYPE_IC_CARD: // 门卡
				mTvPageTitle.setText(R.string.ic_card_details);
				mLlPwdType.setVisibility(View.GONE);
				mLlPasscode.setVisibility(View.GONE);
				mLlName.setEnabled(true);
				mTvNameTitle.setText(R.string.ic_card_name);
				mLlValidPeriod.setEnabled(true);
				mIvValidPeriodMore.setVisibility(View.VISIBLE);
				int fingerprintType = mKeyPwd.getType();
				if (fingerprintType == 1) { // 限时
					mTvValidPeriod.setVisibility(View.GONE);
					mTvStartTime.setVisibility(View.VISIBLE);
					mTvEndTime.setVisibility(View.VISIBLE);
					mTvStartTime.setText(DateUtil.getDateToString(mKeyPwd.getStartDate(), DateUtil.DATE_FORMAT_YYYY_MM_DD_HH_MM));
					mTvEndTime.setText(DateUtil.getDateToString(mKeyPwd.getEndDate(), DateUtil.DATE_FORMAT_YYYY_MM_DD_HH_MM));
				} else if (fingerprintType == 2) { // 永久
					mTvValidPeriod.setVisibility(View.VISIBLE);
					mTvValidPeriod.setText(R.string.permanent);
					mTvStartTime.setVisibility(View.GONE);
					mTvEndTime.setVisibility(View.GONE);
				}
				break;

			default:
				break;
		}
	}

    private void showTipVisible() {
        int temp = mKeyPwd.getKeyboardPwdType();
        if ( temp == -1){
            tv_create_custom_pwd_tips_2.setVisibility(View.GONE);
            cv_create_custom_pwd_tips.setVisibility(View.GONE);
        } else if (temp == 1) {
            tv_create_custom_pwd_tips_2.setVisibility(View.GONE);
            cv_create_custom_pwd_tips.setVisibility(View.VISIBLE);
            infoText.setText(R.string.edit_pwd_hint_one_time);
        } else {
            infoText.setText(R.string.edit_pwd_hint_permanent);
            // status：密码状态(0删除，1未激活，2失效， 3正常，4未知)
            if ("4".equals(mKeyPwd.getStatus())) {
                tv_create_custom_pwd_tips_2.setVisibility(View.GONE);
                cv_create_custom_pwd_tips.setVisibility(View.VISIBLE);
            } else if ("3".equals(mKeyPwd.getStatus())) {
                // useStaus：激活期内的使用状态，1：未同步过，2：使用过，3：未使用
                if (mKeyPwd.getUseStaus() == 1 || mKeyPwd.getUseStaus() == 3) {
                    tv_create_custom_pwd_tips_2.setVisibility(View.GONE);
                    cv_create_custom_pwd_tips.setVisibility(View.VISIBLE);
                }
            }
        }
    }

	private void initListener() {
		mTvSend.setOnClickListener(this);
		mLlPasscode.setOnClickListener(this);
		mLlName.setOnClickListener(this);
		mLlRemark.setOnClickListener(this);
		mLlValidPeriod.setOnClickListener(this);
		mLlRecord.setOnClickListener(this);
		mTvDelete.setOnClickListener(this);
	}
    @Override
    public void onClick(View view) {
        int id = view.getId();
        Intent intent;

        if (id == R.id.page_action) {
            showShare();
        } else if (id == R.id.ll_passcode_detail_passcode) {
            mModifyPasscodeType = 0; // 修改密码
            ModifyCommonPasscodeActivity.actionStart(this, mKeyPwd, mKey);

        } else if (id == R.id.ll_passcode_detail_name) {
            mModifyPasscodeType = 1; // 修改名称
            showInputDialog();

        } else if (id == R.id.ll_passcode_detail_remark) {
            mModifyPasscodeType = 2; // 修改指纹备注
            showInputDialog();

        } else if (id == R.id.ll_passcode_detail_valid_period) {
            intent = new Intent(this, PasscodePeriodModifyActivity.class);
            intent.putExtra(KEY_PASSCODE, mKeyPwd);
            intent.putExtra(PasscodePeriodModifyActivity.KEY_PASSCODE_PWD, mKeyPwd.getKeyboardPwd());
            intent.putExtra(PasscodePeriodModifyActivity.KEY_PASSCODE_ID, mKeyPwd.getId());
            intent.putExtra(PasscodePeriodModifyActivity.KEY_PASSCODE_TYPE, mKeyPwd.getKeyboardPwdType());
            intent.putExtra(PasscodePeriodModifyActivity.KEY_PASSCODE_START_TIME, mKeyPwd.getStartDate());
            intent.putExtra(PasscodePeriodModifyActivity.KEY_PASSCODE_END_TIME, mKeyPwd.getEndDate());
            intent.putExtra(TYPE_KEY_PWD_FP_CARD, mAccessType);
            startActivityForResult(intent, REQUEST_CODE_MODIFY_PASSCODE_PERIOD);

        } else if (id == R.id.ll_passcode_detail_records) {
            PasscodeRecordActivity.actionStart(this, mAccessType,
                    mKeyPwd.getCardNumber(),
                    mKeyPwd.getFingerprintNumber(),
                    mKeyPwd.getKeyboardPwd(),
                    mKey.getLockId());

        } else if (id == R.id.tv_passcode_detail_delete) {
            DialogUtil.showCommonDialog(this, null,
                    getDialogDeleteHint(),
                    getString(R.string.note_pwd_delete_confirm_ok_btn),
                    getString(R.string.cancel),
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            handleDelete();
                        }
                    }, null);

        } else if (id == R.id.btn_dialog_input_cancel) {
            DIALOG.cancel();

        } else if (id == R.id.btn_dialog_input_ok) {
            String input = mEtDialogInput.getText().toString();
            if (mModifyPasscodeType == 0) {
                handleModifyPassword(input);
            } else if (mModifyPasscodeType == 1) {
                handleModifyName(input);
            } else if (mModifyPasscodeType == 2) {
                handleModifyFingerprintRemark(input);
            }

        } else {
            // 可以添加默认处理
        }
    }

// --- 辅助方法 ---

    private void handleDelete() {
        if (!isNetEnableWithToast()) return;

        if (mAccessType == KeyPwdConstant.IType.TYPE_PWD) {
            if (isBleEnableWithoutToast()) deletePasscode();
            else requestDeleteViaGatewayIfAdmin();
        } else if (mAccessType == KeyPwdConstant.IType.TYPE_IC_CARD) {
            if (isBleEnableWithoutToast()) lockDeleteIcCard(mKeyPwd.getCardNumber());
            else requestDeleteViaGatewayIfAdmin();
        } else if (mAccessType == KeyPwdConstant.IType.TYPE_FINGERPRINT) {
            if (isBleEnableWithoutToast())
                lockDeleteFingerprint(Long.parseLong(mKeyPwd.getFingerprintNumber()), mKeyPwd.getFingerprintId());
            else requestDeleteViaGatewayIfAdmin();
        }
    }

    private void requestDeleteViaGatewayIfAdmin() {
        if (mKey.isAdmin() && FeatureValueUtil.isSupportFeature(mKey.getLockData(), FeatureValue.GATEWAY_UNLOCK)) {
            requestDeletePwdFpCard(2);
        } else {
            toast(R.string.enable_bluetooth);
        }
    }

    private void handleModifyPassword(String input) {
        if (StringUtil.isBlank(input)) {
            toast(R.string.enter_password);
        } else if (input.length() < 6) {
            toast(R.string.note_passcode_invalid);
        } else {
            if (isBleNetEnableWithToast()) {
                modifyPasscode(input);
                DIALOG.cancel();
            }
        }
    }

    private void handleModifyName(String input) {
        if (StringUtil.isBlank(input)) {
            toast(mEtDialogInput.getHint().toString());
            return;
        }

        switch (mAccessType) {
            case KeyPwdConstant.IType.TYPE_PWD:
                if (isAdminCode()) {
                    PeachPreference.saveAdminCodeName(mKey.getLockId(), input);
                    mKeyPwd.setAlias(input);
                    mTvName.setText(mKeyPwd.getAlias());
                    EventBus.getDefault().post(new Event(Event.EventType.MODIFY_LOCK_ADMIN_PASSCODE_NAME, input));
                } else {
                    modifyPasscodeAlias(input);
                }
                break;
            case KeyPwdConstant.IType.TYPE_IC_CARD:
                updateIcCardInfo(1, input);
                break;
            case KeyPwdConstant.IType.TYPE_FINGERPRINT:
                updateFingerprintInfo(1, input, null);
                break;
        }
        DIALOG.cancel();
    }

    private void handleModifyFingerprintRemark(String input) {
        if (StringUtil.isBlank(input)) {
            toast(mEtDialogInput.getHint().toString());
            return;
        }
        updateFingerprintInfo(1, null, input);
        DIALOG.cancel();
    }

	private String getDialogDeleteHint() {
		int resId = 0;
		switch (mAccessType) {
			case KeyPwdConstant.IType.TYPE_PWD:
				resId = R.string.note_pwd_delete_confirm_dialog_hint;
				break;

			case KeyPwdConstant.IType.TYPE_IC_CARD:
				resId = R.string.note_ic_card_delete_confirm_dialog_hint;
				break;

			case KeyPwdConstant.IType.TYPE_FINGERPRINT:
				resId = R.string.note_fingerprint_delete_confirm_dialog_hint;
				break;

			default:
				break;
		}

		return getString(resId);
	}

	private String getAdminCodeName() {
		String name = mKeyPwd.getAlias();
		// 管理员密码名称
		if (isAdminCode() && TextUtils.isEmpty(name)) {
			name = getResources().getString(R.string.administrator_code);
		}
		return name;
	}

	private boolean isAdminCode() {
		return mKeyPwd.getKeyboardPwdType() == -1;
	}

	private void deletePasscode() {
		PeachLoader.showLoading(this);
		if (mKey.getLockId() < 0) {
			if (sPPLOCK.isConnected(mKey.getLockMac())) {
				int pwdType = covertMHpwdType(mKeyPwd.getKeyboardPwdType());
				setDeletePasscodeCallback();
				sPPLOCK.deleteKeyboardPwd(PeachPreference.readUserId(), String.valueOf(mKey.getLockId()), String.valueOf(mKey.getKeyId()), pwdType, mKeyPwd.getKeyboardPwd(), mKey.getK1());
			} else {
				MyApplication.pplBleSession.setLockMac(mKey.getLockMac());
				setDeletePasscodeCallback();
				startLockActionScan();
			}
		} else {
            mTTLockAPI.deletePasscode(mKeyPwd.getKeyboardPwd(), mKey.getLockData(), mKey.getLockMac(), new DeletePasscodeCallback() {
                @Override
                public void onDeletePasscodeSuccess() {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            stopLoading();
                            mIsLockOperationSuccess = true;
                            requestDeletePwdFpCard(1);
                        }
                    });
                }

                @Override
                public void onFail(LockError lockError) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            stopLoading();
                            mIsLockOperationSuccess = true;
                            if (mKey.isAdmin() && FeatureValueUtil.isSupportFeature(mKey.getLockData(), FeatureValue.GATEWAY_UNLOCK)) {
                                requestDeletePwdFpCard(2);
                            } else {
                                makeToast(false);
                            }
                        }
                    });
                }
            });
		}

	}

	private int covertMHpwdType(int keyPwdType) {
		int type;
		type = keyPwdType;
		if (keyPwdType == 3 || keyPwdType == 15) {
			type = 4;//限时密码
		}
		if (keyPwdType == 4) {
			type = 6;//清空密码
		}
		if (keyPwdType >= 5 && keyPwdType < 15) {
			type = 3;//循环密码
		}
		return type;
	}

	private void setDeletePasscodeCallback() {
		if (mKey.getLockId()<0){
			MyApplication.pplBleSession.setOperation(LockOperation.DELETE_KEYBOARD_PWD);
			MyApplication.pplBleSession.setKeyboardPwdType(mKeyPwd.getKeyboardPwdType());
			MyApplication.pplBleSession.setKeyboardPwdOriginal(mKeyPwd.getKeyboardPwd());
			MyApplication.pplBleSession.setmILockDeletePasscode(new MHILockDeletePasscode() {
				@Override
				public void onSuccess() {
					runOnUiThread(new Runnable() {
						@Override
						public void run() {
							stopLoading();
							mIsLockOperationSuccess = true;
							requestDeletePwdFpCard(1);
						}
					});
				}

				@Override
				public void onFail() {
					runOnUiThread(new Runnable() {
						@Override
						public void run() {
							stopLoading();
							mIsLockOperationSuccess = true;
							makeToast(false);
						}
					});
				}
			});
		}

	}

	/**
	 * 通过 SDK 删除某张 IC 卡
	 */
	private void lockDeleteIcCard(String cardNumber) {
		showLoading();
		if (mKey.getLockId()<0){
			if (sPPLOCK.isConnected(mKey.getLockMac())) {
				setMH_DeleteICCardCallback(cardNumber);
				sPPLOCK.delCard(PeachPreference.readUserId(),String.valueOf(mKey.getLockId()),String.valueOf(mKey.getKeyId())
						,cardNumber,mKey.getK1());
			} else {
				//toast("删除卡片通过扫描后进行连接" + cardNumber);
				setMH_DeleteICCardCallback(cardNumber);
				startLockActionScan();
			}
		}else {
			long cardId = Long.parseLong(mKeyPwd.getCardNumber());
            mTTLockAPI.deleteICCard(cardNumber, mKey.getLockData(), mKey.getLockMac(), new DeleteICCardCallback() {
                @Override
                public void onDeleteICCardSuccess() {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            stopLoading();
                            mIsLockOperationSuccess = true;
                            requestDeletePwdFpCard(1);
                        }
                    });
                }

                @Override
                public void onFail(LockError lockError) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            stopLoading();
                            mIsLockOperationSuccess = true;
                            if (lockError == LockError.IC_CARD_NOT_EXIST) {
                                // 锁里不存在该指纹，直接删除服务器指纹数据
                                requestDeletePwdFpCard(1);
                            } else {
                                if (mKey.isAdmin() && FeatureValueUtil.isSupportFeature(mKey.getLockData(), FeatureValue.GATEWAY_UNLOCK)) {
                                    requestDeletePwdFpCard(2);
                                } else {
                                    toast(R.string.operation_fail);
                                }
                            }
                        }
                    });
                }
            });
		}

	}

	private void setMH_DeleteICCardCallback(String cardId) {
		MyApplication.pplBleSession.setOperation(LockOperation.DEL_CARD);
		MyApplication.pplBleSession.setLockMac(mKey.getLockMac());
		MyApplication.pplBleSession.setCardId(cardId);
		MyApplication.pplBleSession.setmILockDeleteCard(new MHILockDeleteCard() {
			@Override
			public void onSuccess() {
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						stopLoading();
						mIsLockOperationSuccess = true;
						//toast("正在从服务器删除卡片" + String.valueOf(mKey.getLockId()) + mKeyPwd.getCardNumber());
						requestDeletePwdFpCard(1);
					}
				});
			}

			@Override
			public void onFail() {
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						stopLoading();
						mIsLockOperationSuccess = true;
						toast(R.string.operation_fail);
					}
				});
			}
		});
	}

	/**
	 * 通过 SDK 删除某个指纹
	 */
	private void lockDeleteFingerprint(long fingerprintNumber, String fingerId) {
		showLoading();
		if (mKey.getLockId()<0){
			if (sPPLOCK.isConnected(mKey.getLockMac())) {
				setMH_DeleteFingerprintCallback(fingerId);
				sPPLOCK.delFingerprint(PeachPreference.readUserId(),String.valueOf(mKey.getLockId()),String.valueOf(mKey.getKeyId())
						,mKeyPwd.getFingerprintId(),mKey.getK1());
			} else {
				setMH_DeleteFingerprintCallback(fingerId);
				startLockActionScan();
			}
		}else {
            mTTLockAPI.deleteFingerprint(String.valueOf(fingerprintNumber), mKey.getLockData(), mKey.getLockMac(), new DeleteFingerprintCallback() {
                @Override
                public void onDeleteFingerprintSuccess() {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            stopLoading();
                            mIsLockOperationSuccess = true;
                            requestDeletePwdFpCard(1);
                        }
                    });
                }

                @Override
                public void onFail(LockError lockError) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            stopLoading();
                            mIsLockOperationSuccess = true;
                            if (lockError == LockError.FINGER_PRINT_NOT_EXIST) {
                                // 锁里不存在该指纹，直接删除服务器指纹数据
                                requestDeletePwdFpCard(1);
                            } else {
                                if (mKey.isAdmin() && FeatureValueUtil.isSupportFeature(mKey.getLockData(), FeatureValue.GATEWAY_UNLOCK)) {
                                    requestDeletePwdFpCard(2);
                                } else {
                                    toast(R.string.operation_fail);
                                }
                            }
                        }
                    });
                }
            });
		}

	}

	private void setMH_DeleteFingerprintCallback(String fingerprintId) {
		MyApplication.pplBleSession.setOperation(LockOperation.DEL_FINGERPRINT);
		MyApplication.pplBleSession.setLockMac(mKey.getLockMac());
		MyApplication.pplBleSession.setFingerId(fingerprintId);
		MyApplication.pplBleSession.setmILockDeleteFingerprint(new MHILockDeleteFingerprint() {
			@Override
			public void onSuccess() {
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						stopLoading();
						mIsLockOperationSuccess = true;
						toast("删除指纹成功，正在上传指纹数据到服务器...");
						requestDeletePwdFpCard(1);

					}
				});
			}

			@Override
			public void onFail() {
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						stopLoading();
						mIsLockOperationSuccess = true;
						toast(R.string.operation_fail);
					}
				});
			}
		});
	}

	private void modifyPasscode(String newPwd) {
		PeachLoader.showLoading(this);
		if (mKey.getLockId()<0){
			if (sPPLOCK.isConnected(mKey.getLockMac())) {
				setModifyPasscodeCallback(newPwd);
				int pwdType = mKeyPwd.getKeyboardPwdType();
				if(mKeyPwd.getKeyboardPwdType()==15){
					pwdType = 3;
				}
				sPPLOCK.modifyUserKeyboardPwd(PeachPreference.readUserId(),String.valueOf(mKey.getLockId()),
						String.valueOf(mKey.getKeyId()),mKeyPwd.getKeyboardPwd(),newPwd,pwdType,mKeyPwd.getStartDate(),mKeyPwd.getEndDate(),mKey.getK1());
			} else {
				MyApplication.pplBleSession.setLockMac(mKey.getLockMac());
				setModifyPasscodeCallback(newPwd);
				//sPPLOCK.connect(mKey.getLockMac());
				startLockActionScan();
			}

		}else {
            mTTLockAPI.modifyPasscode(mKeyPwd.getKeyboardPwd(), newPwd, 0, 0, mKey.getLockData(), mKey.getLockMac(), new ModifyPasscodeCallback() {
                @Override
                public void onModifyPasscodeSuccess() {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            stopLoading();
                            requestModifyPasscode(newPwd);
                        }
                    });
                }

                @Override
                public void onFail(LockError lockError) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            stopLoading();
                            if (lockError == LockError.LOCK_PASSWORD_NOT_EXIST) {
                                toast(R.string.note_unused_passcode_cannot_be_modified);
                            } else {
                                toast(R.string.operation_fail);
                            }
                        }
                    });
                }
            });
		}

	}

	private void setModifyPasscodeCallback(final String newPwd) {
		if (mKey.getLockId()<0){
			MyApplication.pplBleSession.setOperation(LockOperation.MODIFY_KEYBOARD_PWD);
			int pwdType = mKeyPwd.getKeyboardPwdType();
			if(mKeyPwd.getKeyboardPwdType()==15){
				pwdType = 3;
			}
			MyApplication.pplBleSession.setKeyboardPwdType(pwdType);
			MyApplication.pplBleSession.setKeyboardPwdOriginal(mKeyPwd.getKeyboardPwd());
			MyApplication.pplBleSession.setKeyboardPwdNew(newPwd);
			MyApplication.pplBleSession.setStartDate(mKeyPwd.getStartDate());
			MyApplication.pplBleSession.setEndDate(mKeyPwd.getEndDate());
			MyApplication.pplBleSession.setmILockModifyPasscode(new MHILockModifyPasscode() {

				@Override
				public void onSuccess() {
					runOnUiThread(new Runnable() {
						@Override
						public void run() {
							stopLoading();
							requestModifyPasscode(newPwd);
						}
					});
				}

				@Override
				public void onFail() {
					runOnUiThread(new Runnable() {
						@Override
						public void run() {
							stopLoading();
							toast(R.string.operation_fail);

						}
					});
				}
			});
		}

	}

	/**
	 * 请求服务器，修改键盘密码
	 */
	private void requestModifyPasscode(final String newPwd) {
		RestClient.builder()
				.url(Urls.LOCK_PASSCODE_MODIFY)
				.loader(this)
				.params("userId", PeachPreference.readUserId())
				.params("lockId", mKey.getLockId())
				.params("keyboardPwdId", mKeyPwd.getId())
				.params("changeType", 1)
				.params("newKeyboardPwd", newPwd)
				.params("timeZone", LockTime.hours(mKey.getTimezoneRawOffset()))
				.success(new ISuccess() {
					@Override
					public void onSuccess(String response) {
						PeachLogger.d("LOCK_PASSCODE_MODIFY", response);
						JSONObject result = JSON.parseObject(response);
						int code = result.getInteger("code");
						if (code == 200) {
							mKeyPwd.setKeyboardPwd(newPwd);
							refreshUI();
							toast(R.string.operation_success);
						} else {
							toastBackendError(code, R.string.operation_fail);
						}
					}
				})
				.build()
				.post();
	}

	/**
	 * 修改密码别名
	 */
	private void modifyPasscodeAlias(final String passcodeAlias) {
		RestClient.builder()
				.url(Urls.LOCK_PASSCODE_ALIAS_MODIFY)
				.loader(this)
				.params("alias", passcodeAlias)
				.params("keyboardPwdId", mKeyPwd.getId())
				.params("userId", PeachPreference.readUserId())
				.success(new ISuccess() {
					@Override
					public void onSuccess(String response) {
						PeachLogger.d("LOCK_PASSCODE_ALIAS_MODIFY", response);
						JSONObject result = JSON.parseObject(response);
						int code = result.getInteger("code");
						if (code == 200) {
							toast(R.string.note_modify_name_success);
							mKeyPwd.setAlias(passcodeAlias);
							EventBus.getDefault().post(new Event(Event.EventType.MODIFY_LOCK_PASSCODE_NAME, passcodeAlias));
							refreshUI();
						} else {
							toastBackendError(code, R.string.note_modify_name_fail);
						}
					}
				})
				.failure(new IFailure() {
					@Override
					public void onFailure() {
						toast(R.string.note_modify_name_fail);
					}
				})
				.error(new IError() {
					@Override
					public void onError(int code, String msg) {
						toast(R.string.note_modify_name_fail);
					}
				})
				.build()
				.post();
	}

	/**
	 * 更新门卡信息
	 */
	private void updateIcCardInfo(int changeType, final String alias) {
		WeakHashMap<String, Object> params = new WeakHashMap<>();
		params.put("cardId", mKeyPwd.getCardId());
		params.put("userId", PeachPreference.readUserId());
		params.put("lockId", mKey.getLockId());
		if (mModifyPasscodeType == 1) { // 修改卡片名称
			params.put("alias", alias);
		} else {
			params.put("changeType", changeType);
			params.put("startDate", DateUtil.getDateToString(mKeyPwd.getStartDate(), DateUtil.DATE_FORMAT_YYYY_MM_DD_HH_MM));
			params.put("endDate", DateUtil.getDateToString(mKeyPwd.getEndDate(), DateUtil.DATE_FORMAT_YYYY_MM_DD_HH_MM));
			params.put("timeZone", DateUtil.getTimeZone());
		}

		RestClient.builder()
				.url(Urls.IC_CARD_INFO_UPDATE)
				.loader(this)
				.params(params)
				.success(new ISuccess() {
					@Override
					public void onSuccess(String response) {
						PeachLogger.d("IC_CARD_UPDATE", response);
						JSONObject result = JSON.parseObject(response);
						int code = result.getInteger("code");
						if (code == 200) {
							toast(R.string.note_modify_name_success);
							mKeyPwd.setAlias(alias);
							EventBus.getDefault().post(new Event(Event.EventType.MODIFY_LOCK_PASSCODE_NAME, alias));
							refreshUI();
						} else {
							toastBackendError(code, R.string.note_modify_name_fail);
						}
					}
				})
				.failure(new IFailure() {
					@Override
					public void onFailure() {
						toast(R.string.note_modify_name_fail);
					}
				})
				.error(new IError() {
					@Override
					public void onError(int code, String msg) {
						toast(R.string.note_modify_name_fail);
					}
				})
				.build()
				.post();
	}

	/**
	 * 更新指纹信息
	 */
	private void updateFingerprintInfo(int changeType, final String alias, final String remark) {
		WeakHashMap<String, Object> params = new WeakHashMap<>();
		params.put("id", mKeyPwd.getFpStringId());
		params.put("lockId", mKey.getLockId());
		if (mModifyPasscodeType == 1 && !StringUtil.isBlank(alias)) { // 修改指纹名称
			params.put("alias", alias);
		} else if (mModifyPasscodeType == 2 && !StringUtil.isBlank(remark)) { // 修改指纹备注
			params.put("remark", remark);
		} else {
			params.put("changeType", changeType);
			params.put("startDate", DateUtil.getDateToString(mKeyPwd.getStartDate(), DateUtil.DATE_FORMAT_YYYY_MM_DD_HH_MM));
			params.put("endDate", DateUtil.getDateToString(mKeyPwd.getEndDate(), DateUtil.DATE_FORMAT_YYYY_MM_DD_HH_MM));
			params.put("timeZone", DateUtil.getTimeZone());
		}

		RestClient.builder()
				.url(Urls.FINGERPRINT_INFO_UPDATE)
				.loader(this)
				.params(params)
				.success(new ISuccess() {
					@Override
					public void onSuccess(String response) {
						PeachLogger.d("FINGERPRINT_UPDATE", response);
						JSONObject result = JSON.parseObject(response);
						int code = result.getInteger("code");
						if (code == 200) {
							if (mModifyPasscodeType == 1) {
								toast(R.string.note_modify_name_success);
								mKeyPwd.setAlias(alias);
							} else if (mModifyPasscodeType == 2) {
								toast(R.string.note_modify_remark_success);
								mKeyPwd.setRemark(remark);
							}
							EventBus.getDefault().post(new Event(Event.EventType.MODIFY_LOCK_PASSCODE_NAME, alias));
							refreshUI();
						} else {
							toastBackendError(code, mModifyPasscodeType == 1 ? R.string.note_modify_name_fail : R.string.note_modify_remark_fail);
						}
					}
				})
				.failure(new IFailure() {
					@Override
					public void onFailure() {
						toast(mModifyPasscodeType == 1 ? R.string.note_modify_name_fail : R.string.note_modify_remark_fail);
					}
				})
				.error(new IError() {
					@Override
					public void onError(int code, String msg) {
						toast(mModifyPasscodeType == 1 ? R.string.note_modify_name_fail : R.string.note_modify_remark_fail);
					}
				})
				.build()
				.post();
	}

	private void showInputDialog() {
		DIALOG = new AlertDialog.Builder(this).create();
		DIALOG.setCanceledOnTouchOutside(false);
		DIALOG.show();
		final Window window = DIALOG.getWindow();
		if (window != null) {
			window.setContentView(R.layout.dialog_input);
			window.setGravity(Gravity.CENTER);
//			window.setWindowAnimations(R.style.anim_panel_up_from_bottom);
			window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
			//设置属性
			final WindowManager.LayoutParams params = window.getAttributes();
			params.width = WindowManager.LayoutParams.MATCH_PARENT;
			params.flags = WindowManager.LayoutParams.FLAG_DIM_BEHIND;
			params.dimAmount = 0.5f;
			window.setAttributes(params);

			TextView title = window.findViewById(R.id.tv_dialog_input_title);
			mEtDialogInput = window.findViewById(R.id.et_dialog_input_content);
			AppCompatButton cancel = window.findViewById(R.id.btn_dialog_input_cancel);
			AppCompatButton ok = window.findViewById(R.id.btn_dialog_input_ok);
			if (mModifyPasscodeType == 0) {
				title.setText(R.string.modify_passcode);
				try {
					mEtDialogInput.setHint(R.string.passcode_format_6_9_digits);
					mEtDialogInput.setInputType(InputType.TYPE_CLASS_NUMBER);
					mEtDialogInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(9)});
					mEtDialogInput.setText(mKeyPwd.getKeyboardPwd());
					mEtDialogInput.setSelection(mKeyPwd.getKeyboardPwd().length());
				} catch (Exception e) {
					e.printStackTrace();
				}
			} else if (mModifyPasscodeType == 1) { // 修改名称
				try {
					mEtDialogInput.setInputType(InputType.TYPE_CLASS_TEXT);
					mEtDialogInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(20)});
					String name = mKeyPwd.getAlias();
					// 管理员密码名称
					if (isAdminCode() && StringUtil.isBlank(name)) {
						name = getResources().getString(R.string.administrator_code);
					}
					mEtDialogInput.setText(name);
					mEtDialogInput.setSelection(name.length());

					switch (mAccessType) {
						case KeyPwdConstant.IType.TYPE_PWD:
							title.setText(R.string.modify_passcode_name);
							mEtDialogInput.setHint(R.string.enter_passcode_name);
							break;

						case KeyPwdConstant.IType.TYPE_IC_CARD:
							title.setText(R.string.modify_ic_card_name);
							mEtDialogInput.setHint(R.string.enter_ic_card_name);
							break;

						case KeyPwdConstant.IType.TYPE_FINGERPRINT:
							title.setText(R.string.modify_fingerprint_name);
							mEtDialogInput.setHint(R.string.enter_fingerprint_name);
							break;

						default:
							break;
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			} else if (mModifyPasscodeType == 2) { // 修改指纹备注
				try {
					mEtDialogInput.setInputType(InputType.TYPE_CLASS_TEXT);
					mEtDialogInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(20)});
					mEtDialogInput.setText(mKeyPwd.getRemark());
					mEtDialogInput.setSelection(mKeyPwd.getRemark().length());
					title.setText(R.string.modify_fingerprint_remark);
					mEtDialogInput.setHint(R.string.enter_fingerprint_remark);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			DialogUtil.autoPopSoftInput(window, mEtDialogInput);
			cancel.setOnClickListener(this);
			ok.setOnClickListener(this);
		}
	}

	/**
	 * 请求服务器，删除键盘密码/指纹/门卡
	 * mediumType可选	Integer
	 * 通讯介质（1：蓝牙，2：网关，默认是1）
	 */
	private void requestDeletePwdFpCard(int mediumType) {
		RestClient.builder()
				.url(getRequestUrl())
				.loader(this)
				.params(getRequestParams(mediumType))
				.success(new ISuccess() {
					@Override
					public void onSuccess(String response) {
						PeachLogger.d("LOCK_DELETE_PWD_FINGERPRINT_CARD", response);
						stopLoading();
						JSONObject result = JSON.parseObject(response);
						int code = result.getInteger("code");
						if (code == 200) {
							EventBus.getDefault().post(new Event(Event.EventType.DELETE_PWD));
							makeToast(true);
							finish();
						} else {
							makeToast(false);
						}
					}
				})
				.failure(new IFailure() {
					@Override
					public void onFailure() {
						stopLoading();
						makeToast(false);
					}
				})
				.error(new IError() {
					@Override
					public void onError(int code, String msg) {
						stopLoading();
						makeToast(false);
					}
				})
				.build()
				.post();
	}

	private void makeToast(boolean isSuccess) {
		if (KeyPwdConstant.IType.TYPE_PWD == mAccessType) {
			toast(isSuccess ? R.string.passcode_delete_success : R.string.passcode_delete_fail);
		} else {
			if (isSuccess) {
				toastSuccess();
			} else {
				toastFail();
			}
		}
	}

	private String getRequestUrl() {
		String url = Urls.LOCK_PASSCODE_DELETE;
		switch (mAccessType) {
			case KeyPwdConstant.IType.TYPE_PWD:
				url = Urls.LOCK_PASSCODE_DELETE;
				break;

			case KeyPwdConstant.IType.TYPE_IC_CARD:
				url = Urls.IC_CARD_DELETE;

				break;

			case KeyPwdConstant.IType.TYPE_FINGERPRINT:
				url = Urls.FINGERPRINT_DELETE;

				break;

			default:
				break;
		}

		return url;
	}

	private WeakHashMap<String, Object> getRequestParams(int mediumType) {
		WeakHashMap<String, Object> params = new WeakHashMap<>();
		switch (mAccessType) {
			case KeyPwdConstant.IType.TYPE_PWD:
				params.put("lockId", mKey.getLockId());
				params.put("userId", PeachPreference.readUserId());
				params.put("keyboardPwdId", mKeyPwd.getId());
				params.put("mediumType", mediumType);
				break;

			case KeyPwdConstant.IType.TYPE_IC_CARD:
				params.put("lockId", mKey.getLockId());
				params.put("cardNumber", mKeyPwd.getCardNumber());
				params.put("deleteType", mediumType);
				//toast("从服务器删除卡片" + String.valueOf(mKey.getLockId()) + mKeyPwd.getCardNumber());
				break;

			case KeyPwdConstant.IType.TYPE_FINGERPRINT:
				params.put("id", mKeyPwd.getFpStringId());
				params.put("lockId", mKey.getLockId());
				params.put("userId", PeachPreference.readUserId());
				params.put("deleteType", mediumType);
				break;

			default:
				break;
		}

		return params;
	}

	public void showShare() {
		try {
			Intent vIt = new Intent(Intent.ACTION_SEND);
//							vIt.setPackage("com.facebook.orca");
			vIt.setType("text/plain");
			vIt.putExtra(Intent.EXTRA_TEXT, getShareContent());
			startActivity(vIt);
		} catch (Exception e) {
			e.printStackTrace();
		}
//		OnekeyShare oks = new OnekeyShare();
//
//		// 自定义分享平台
//		oks.setCustomerLogo(BitmapFactory.decodeResource(getResources(), R.drawable.ic_share_zalo),
//				"Zalo", new View.OnClickListener() {
//					@Override
//					public void onClick(View view) {
//						try {
//							Intent vIt = new Intent(Intent.ACTION_SEND);
////							vIt.setPackage("com.facebook.orca");
//							vIt.setType("text/plain");
//							vIt.putExtra(Intent.EXTRA_TEXT, getShareContent());
//							startActivity(vIt);
//						} catch (Exception e) {
//							e.printStackTrace();
//						}
//					}
//				});
//
//		//关闭sso授权
//		oks.disableSSOWhenAuthorize();
//		oks.setCallback(new PlatformActionListener() {
//			@Override
//			public void onComplete(Platform platform, int i, HashMap<String, Object> hashMap) {
//
//			}
//
//			@Override
//			public void onError(Platform platform, int i, Throwable throwable) {
//
//			}
//
//			@Override
//			public void onCancel(Platform platform, int i) {
//
//			}
//		});
//
//		// title标题，微信、QQ和QQ空间等平台使用
//		oks.setTitle(getString(R.string.app_name));
//		// titleUrl QQ和QQ空间跳转链接
////		oks.setTitleUrl("http://sharesdk.cn");
////		oks.setAddress("13201812820");
//		// text是分享文本，所有平台都需要这个字段
//		oks.setText(getShareContent());
//		// imagePath是图片的本地路径，Linked-In以外的平台都支持此参数
////		oks.setImagePath("/sdcard/test.jpg");//确保SDcard下面存在此张图片
//		// url在微信、微博，Facebook等平台中使用
////		oks.setUrl("http://sharesdk.cn");
//		// comment是我对这条分享的评论，仅在人人网使用
////		oks.setComment("我是测试评论文本");
//		// 启动分享GUI
//		oks.show(this);
	}

	private String getShareContent() {
		return Utils.getShareContent(this, mKeyPwd.getKeyboardPwdType(), mTvPasscode.getText().toString(),
				mKeyPwd.getCreateDate(), mKeyPwd.getStartDate(), mKeyPwd.getEndDate(), mKey.getLockName());
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && requestCode == REQUEST_CODE_MODIFY_PASSCODE_PERIOD) {
			switch (mAccessType) {
				case KeyPwdConstant.IType.TYPE_PWD:
					mKeyPwd.setKeyboardPwdType(3);
					break;

				case KeyPwdConstant.IType.TYPE_IC_CARD:
					mKeyPwd.setType(1);
					break;

				case KeyPwdConstant.IType.TYPE_FINGERPRINT:
					mKeyPwd.setType(1);
					break;

				default:
					break;
			}

			long startTime = data.getLongExtra(PasscodePeriodModifyActivity.KEY_PASSCODE_START_TIME, mKeyPwd.getStartDate());
			long endTime = data.getLongExtra(PasscodePeriodModifyActivity.KEY_PASSCODE_END_TIME, mKeyPwd.getEndDate());
			mKeyPwd.setStartDate(startTime);
			mKeyPwd.setEndDate(endTime);
			refreshUI();
		}
	}


	@Override
	public void onEventSub(Event event) {
		super.onEventSub(event);
		switch (event.type) {
			case Event.EventType.MODIFY_LOCK_PASSCODE:
			case Event.EventType.MODIFY_LOCK_ADMIN_PASSCODE:
				String newPwd = (String) event.obj;
				mKeyPwd.setKeyboardPwd(newPwd);
				refreshUI();
				break;
			case Event.EventType.MODIFY_LOCK_ADMIN_PASSCODE_NAME:
				mKeyPwd.setAlias((String) event.obj);
				refreshUI();
				break;
		}
	}
}
