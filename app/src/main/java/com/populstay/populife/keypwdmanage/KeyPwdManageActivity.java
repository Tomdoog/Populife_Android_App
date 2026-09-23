package com.populstay.populife.keypwdmanage;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.tabs.TabLayout;
import com.populock.manhattan.sdk.constant.LockOperation;
import com.populstay.populife.R;
import com.populstay.populife.helper.LockRecordUploader;
import com.populstay.populife.activity.LockSendEkeyActivity;
import com.populstay.populife.activity.LockSendPasscodeActivity;
import com.populstay.populife.activity.LockSendPasscodeNewActivity;
import com.populstay.populife.app.MyApplication;
import com.populstay.populife.base.BaseActivity;
import com.populstay.populife.base.BasePagerAdapter;
import com.populstay.populife.common.Urls;
import com.populstay.populife.entity.Key;
import com.populstay.populife.entity.Passcode;
import com.populstay.populife.enumtype.Operation;
import com.populstay.populife.eventbus.Event;
import com.populstay.populife.home.entity.HomeDeviceInfo;
import com.populstay.populife.lock.ILockFingerprintSearch;
import com.populstay.populife.lock.ILockGetOperateLog;
import com.populstay.populife.lock.ILockIcCardSearch;
import com.populstay.populife.manhattanlock.MHILockGetOperateLog;
import com.populstay.populife.net.RestClient;
import com.populstay.populife.net.callback.IError;
import com.populstay.populife.net.callback.IFailure;
import com.populstay.populife.net.callback.ISuccess;
import com.populstay.populife.util.date.DateUtil;
import com.populstay.populife.util.dialog.DialogUtil;
import com.populstay.populife.util.log.PeachLogger;
import com.populstay.populife.util.storage.PeachPreference;
import com.ttlock.bl.sdk.callback.GetAllValidFingerprintCallback;
import com.ttlock.bl.sdk.callback.GetAllValidICCardCallback;
import com.ttlock.bl.sdk.callback.GetOperationLogCallback;
import com.ttlock.bl.sdk.constant.LogType;
import com.ttlock.bl.sdk.entity.Error;
import com.ttlock.bl.sdk.entity.LockError;

import org.greenrobot.eventbus.EventBus;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.viewpager.widget.ViewPager;

import static com.populstay.populife.app.MyApplication.CURRENT_KEY;
import static com.populstay.populife.app.MyApplication.mTTLockAPI;
import static com.populstay.populife.app.MyApplication.sPPLOCK;

public class KeyPwdManageActivity extends BaseActivity implements View.OnClickListener {

	private static final String KEY_KEY = "KEY_KEY";
	private static final String KEY_TYPE = "KEY_TYPE";
	private static final String KEY_FROM = "KEY_FROM";
	private static final String KEY_LOCK_TYPE = "KEY_LOCK_TYPE";
//	public List<Passcode> mPasscodeList = new ArrayList<>();
	private int mFrom = KeyPwdConstant.IFrom.FROM_LOCK_DETAILS;
	private int mAccessType = KeyPwdConstant.IType.TYPE_KEY; // 1：钥匙，2：密码，3：指纹，4：门卡
	private TabLayout mTabLayout;
	private ViewPager mViewPager;
	private List<Fragment> mFragmentList = new ArrayList<>();
	private BasePagerAdapter mAdapter;
	private Key mKey = CURRENT_KEY;
	private String mLockType;
	private ImageView mIvMoreMenu;
	private TextView tvTitle;
	private TextView tvRefresh;
	private boolean isMHLock = false;

	public static void actionStart(Context context, Key key, int type, int from, String lockType) {
		Intent intent = new Intent(context, KeyPwdManageActivity.class);
		intent.putExtra(KEY_KEY, key);
		intent.putExtra(KEY_TYPE, type);
		intent.putExtra(KEY_FROM, from);
		intent.putExtra(KEY_LOCK_TYPE,lockType);
		context.startActivity(intent);
	}

	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_key_pwd_manage);
		getIntentData();
		initTitleBar();
		initView();
		initTab();
		setListener();
		initStatus();
	}

	private void getIntentData() {
		if (getIntent().hasExtra(KEY_KEY)){
			mKey = getIntent().getParcelableExtra(KEY_KEY);
			MyApplication.CURRENT_KEY = mKey;
		}
		//mKey = getIntent().getParcelableExtra(KEY_KEY);
		mAccessType = getIntent().getIntExtra(KEY_TYPE, KeyPwdConstant.IType.TYPE_KEY);
		mFrom = getIntent().getIntExtra(KEY_FROM, KeyPwdConstant.IFrom.FROM_LOCK_DETAILS);
		mLockType = getIntent().getStringExtra(KEY_LOCK_TYPE);
		if (mLockType.startsWith(HomeDeviceInfo.IDeviceName.NAME_LOCK_MANHATTAN_MOON_LOCK)
		||mLockType.startsWith(HomeDeviceInfo.IDeviceName.NAME_LOCK_MANHATTAN_MOON_LOCK_LOWER)
				||mLockType.startsWith(HomeDeviceInfo.IDeviceName.NAME_LOCK_KEY_BOX_3)) {
			isMHLock = true;
		}
	}

	private void initTitleBar() {
		tvTitle = findViewById(R.id.page_left_title);
		tvRefresh = findViewById(R.id.page_action);
		tvRefresh.setText("");
		tvRefresh.setCompoundDrawablesWithIntrinsicBounds(
				getResources().getDrawable(R.drawable.refresh_icon), null, null, null);
	}

	private void initView() {
		mTabLayout = findViewById(R.id.tl_lock_send_passcode);
		mViewPager = findViewById(R.id.vp_lock_send_passcode);
		mViewPager.setOffscreenPageLimit(3);
		mTabLayout.setupWithViewPager(mViewPager);
		mIvMoreMenu = findViewById(R.id.iv_more_menu);
	}

	protected void initTab() {

		Resources res = getResources();
		String[] titles = new String[]{
				res.getString(R.string.key_pwd_status_available),
				res.getString(R.string.key_pwd_status_not_activated)};

		if (KeyPwdConstant.IFrom.FROM_MORE == mFrom) {
			titles = new String[]{
					res.getString(R.string.key_pwd_status_invalid)};
			mFragmentList.add(KeyPwdListFragment.newInstance(KeyPwdConstant.IKeyPwdCategory.KEY_PWD_CATEGORY_INVALID, mKey, mAccessType));//已失效
			mTabLayout.setVisibility(View.GONE);
			mIvMoreMenu.setVisibility(View.GONE);
		} else {
			mFragmentList.add(KeyPwdListFragment.newInstance(KeyPwdConstant.IKeyPwdCategory.KEY_PWD_CATEGORY_AVAILABLE, mKey, mAccessType));//生效中
			mFragmentList.add(KeyPwdListFragment.newInstance(KeyPwdConstant.IKeyPwdCategory.KEY_PWD_CATEGORY_NOT_ACTIVATED, mKey, mAccessType));//待生效
		}

		mAdapter = new BasePagerAdapter(getSupportFragmentManager(), mFragmentList, titles);
		mViewPager.setAdapter(mAdapter);

		// 去除点击水波纹效果
		for (int i = 0; i < mTabLayout.getTabCount(); i++) {
			TabLayout.Tab tab = mTabLayout.getTabAt(i);
			LinearLayout layout = tab.view;
			layout.setBackgroundColor(ContextCompat.getColor(this, R.color.transparent));
		}
	}

	private void setListener() {
		mIvMoreMenu.setOnClickListener(this);
		tvRefresh.setOnClickListener(this);
	}

	private void initStatus() {

		int title = R.string.management_title_pwd;
		if (mFrom == KeyPwdConstant.IFrom.FROM_LOCK_DETAILS) {
			switch (mAccessType) {
				case KeyPwdConstant.IType.TYPE_KEY:
					tvRefresh.setVisibility(View.GONE);
					title = R.string.management_title_key;
					break;

				case KeyPwdConstant.IType.TYPE_PWD:
					title = R.string.management_title_pwd;
					break;

				case KeyPwdConstant.IType.TYPE_FINGERPRINT:
					title = R.string.management_title_fingerprint;
					break;

				case KeyPwdConstant.IType.TYPE_IC_CARD:
					title = R.string.management_title_ic_card;
					break;

				default:
					break;
			}
		} else {
			tvRefresh.setVisibility(View.GONE);
			switch (mAccessType) {
				case KeyPwdConstant.IType.TYPE_KEY:
					title = R.string.management_title_key_invalid;
					break;

				case KeyPwdConstant.IType.TYPE_PWD:
					title = R.string.management_title_pwd_invalid;
					break;

				case KeyPwdConstant.IType.TYPE_FINGERPRINT:
					title = R.string.management_title_fingerprint_invalid;
					break;

				case KeyPwdConstant.IType.TYPE_IC_CARD:
					title = R.string.management_title_ic_card_invalid;
					break;

				default:
					break;
			}
		}

		tvTitle.setText(title);
	}

    @Override
    public void onClick(View v) {
        int id = v.getId();

        if (id == R.id.iv_more_menu) {
            KeyPwdMoreActivity.actionStart(this, mKey, mAccessType);

        } else if (id == R.id.page_action) {
            tvRefresh.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    DialogUtil.showCommonDialog(KeyPwdManageActivity.this,
                            getDialogTitle(), getDialogContent(),
                            getString(R.string.ok), getString(R.string.cancel),
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    if (isBleNetEnableWithToast()) {
                                        if (mAccessType == KeyPwdConstant.IType.TYPE_PWD) {
                                            // 读取锁操作记录
                                            readLockOperateLog();
                                        } else if (mAccessType == KeyPwdConstant.IType.TYPE_FINGERPRINT) {
                                            // 读取锁里所有的指纹信息
                                            if (mKey.getLockId() < 0) {
                                                readLockOperateLog();
                                            } else {
                                                searchLockFingerprints();
                                            }
                                        } else if (mAccessType == KeyPwdConstant.IType.TYPE_IC_CARD) {
                                            // 读取锁里所有的 IC 卡信息
                                            searchLockIcCards();
                                        }
                                    }
                                }
                            }, null);
                }
            });
        }
    }

    private String getDialogTitle() {
		int title = R.string.sync_password_status;
		switch (mAccessType) {
			case KeyPwdConstant.IType.TYPE_PWD:
				title = R.string.sync_password_status;
				break;

			case KeyPwdConstant.IType.TYPE_FINGERPRINT:
				title = R.string.sync_fingerprint_status;
				break;

			case KeyPwdConstant.IType.TYPE_IC_CARD:
				title = R.string.sync_access_card_status;
				break;

			default:
				break;
		}
		return getString(title);
	}

	private String getDialogContent() {
		int title = R.string.note_sync_password_status;
		switch (mAccessType) {
			case KeyPwdConstant.IType.TYPE_PWD:
				title = R.string.note_sync_password_status;
				break;

			case KeyPwdConstant.IType.TYPE_FINGERPRINT:
				title = R.string.note_sync_fingerprint_status;
				break;

			case KeyPwdConstant.IType.TYPE_IC_CARD:
				title = R.string.note_sync_access_card_status;
				break;

			default:
				break;
		}
		return getString(title);
	}

//	@Override
//	protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
//		super.onActivityResult(requestCode, resultCode, data);
//		if (RESULT_OK == resultCode) {
//			if (KeyPwdTypeSelectActivity.KEY_PWD_TYPE_REQUEST_CODE == requestCode) {
//				// 创建【数字密码】前，拿到用户选择的密码类型
//				String keyPwdTypeSelected = data.getStringExtra(KeyPwdTypeSelectActivity.KEY_PWD_TYPE_SELECTED);
//				createKeyPwd(keyPwdTypeSelected);
//			}
//		}
//	}

	public void setCurrentTab(int position) {
		if (null != mViewPager) {
			mViewPager.setCurrentItem(position);
		}
	}

	/**
	 * 创建蓝牙钥匙/数字密码
	 *
	 * @param keyPwdTypeSelected 钥匙/密码 类型
	 */
	public void createKeyPwd(String keyPwdTypeSelected) {
		if (KeyPwdConstant.IKeyPwdType.KEY_PWD_TYPE_KEY_BT_KEY.equals(keyPwdTypeSelected)) { // 创建蓝牙钥匙
			LockSendEkeyActivity.actionStart(KeyPwdManageActivity.this, mKey.getLockId(), mKey.isAdmin(), mKey);
		} else { // 创建数字密码
			ArrayList<String> passwordList = new ArrayList<>();
			passwordList.add(mKey.getNoKeyPwd());
//			// _TODO: 7/8/21  mPasscodeList 没有初始赋值
//			for (Passcode passcode : mPasscodeList) {
//				passwordList.add(passcode.getKeyboardPwd());
//			}
			LockSendPasscodeNewActivity.actionStart(KeyPwdManageActivity.this, mKey, mKey.getLockId(),
					mKey.getKeyId(), mKey.getLockName(), mKey.getLockMac(), passwordList, keyPwdTypeSelected);
		}
	}

	/**
	 * 读取锁操作记录
	 */
	private void readLockOperateLog() {
		showLoading();
		setReadOperateLogCallback();
		if (mKey.getLockId()<0) {
			if (sPPLOCK.isConnected(mKey.getLockMac())) {
				sPPLOCK.getOperateLog(PeachPreference.readUserId(),String.valueOf(mKey.getLockId()),String.valueOf(mKey.getKeyId()),String.valueOf(mKey.getK1()),mKey.getLockName());
			} else {
				//sPPLOCK.connect(mKey.getLockMac());
				startLockActionScan();
			}
		}else {
            mTTLockAPI.getOperationLog(LogType.ALL, mKey.getLockData(), mKey.getLockMac(), new GetOperationLogCallback() {
                @Override
                public void onGetLogSuccess(String s) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            stopLoading();
                            PeachLogger.d(s);
                            uploadLockOperateLog(s);
                        }
                    });
                }

                @Override
                public void onFail(LockError lockError) {

                    stopLoading();
                    toastFail();
                }
            });
		}


	}

	private void setReadOperateLogCallback() {
		if (mKey.getLockId()<0) {
			MyApplication.pplBleSession.setOperation(LockOperation.GET_OPERATE_LOG);
			MyApplication.pplBleSession.setmILockGetOperateLog(new MHILockGetOperateLog() {
				@Override
				public void onSuccess() {
					runOnUiThread(new Runnable() {
						@Override
						public void run() {
							stopLoading();
							EventBus.getDefault().post(new Event(Event.EventType.SYN_PWD_INFO_SUCCESS));
						}
					});
				}
				@Override
				public void onFail() {
					stopLoading();
					toastFail();
				}
			});
		}

	}

	/**
	 * 上传锁密码操作记录
	 */
	private void uploadLockOperateLog(String operateLog) {
		showLoading();
		LockRecordUploader.getInstance().uploadRecords(mKey.getLockId(), operateLog, (uploaded, saved) -> {
			stopLoading();
			if (isFinishing() || isDestroyed()) return;
			if (!uploaded) {
				toast(saved ? R.string.record_sync_queued : R.string.record_storage_failed);
				return;
			}
			toastSuccess();
			EventBus.getDefault().post(new Event(Event.EventType.SYN_PWD_INFO_SUCCESS));
		});
	}

	/**
	 * 读取锁的 IC 卡信息
	 */
	private void searchLockIcCards() {
		showLoading();
        mTTLockAPI.getAllValidICCards(mKey.getLockData(), mKey.getLockMac(), new GetAllValidICCardCallback() {
            @Override
            public void onGetAllValidICCardSuccess(String s) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        stopLoading();
                        requestUploadIcCards(s);
                    }
                });
            }

            @Override
            public void onFail(LockError lockError) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        readLockOperateLog();
                    }
                });
            }
        });
	}

	/**
	 * 请求服务器，上传 IC 卡
	 */
	private void requestUploadIcCards(String icCardInfo) {
		RestClient.builder()
				.url(Urls.IC_CARD_UPLOAD)
				.loader(this)
				.params("userId", PeachPreference.readUserId())
				.params("lockId", mKey.getLockId())
				.params("records", icCardInfo)
				.success(new ISuccess() {
					@Override
					public void onSuccess(String response) {
						PeachLogger.d("IC_CARD_UPLOAD", response);

						readLockOperateLog();
					}
				})
				.failure(new IFailure() {
					@Override
					public void onFailure() {
						readLockOperateLog();
					}
				})
				.error(new IError() {
					@Override
					public void onError(int code, String msg) {
						readLockOperateLog();
					}
				})
				.build()
				.post();
	}

	/**
	 * 读取锁的指纹信息
	 */
	private void searchLockFingerprints() {
		showLoading();
        mTTLockAPI.getAllValidFingerprints(mKey.getLockData(), mKey.getLockMac(), new GetAllValidFingerprintCallback() {
            @Override
            public void onGetAllFingerprintsSuccess(String s) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        stopLoading();
                        requestUploadFingerprints(s);
                    }
                });
            }

            @Override
            public void onFail(LockError lockError) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        readLockOperateLog();
                    }
                });
            }
        });
	}

	/**
	 * 请求服务器，上传指纹信息
	 */
	private void requestUploadFingerprints(String fingerprintInfo) {
		RestClient.builder()
				.url(Urls.FINGERPRINT_UPLOAD)
				.loader(this)
				.params("userId", PeachPreference.readUserId())
				.params("lockId", mKey.getLockId())
				.params("records", fingerprintInfo)
				.success(new ISuccess() {
					@Override
					public void onSuccess(String response) {
						PeachLogger.d("FINGERPRINT_UPLOAD", response);

						readLockOperateLog();
					}
				})
				.failure(new IFailure() {
					@Override
					public void onFailure() {
						readLockOperateLog();
					}
				})
				.error(new IError() {
					@Override
					public void onError(int code, String msg) {
						readLockOperateLog();
					}
				})
				.build()
				.post();
	}
}
