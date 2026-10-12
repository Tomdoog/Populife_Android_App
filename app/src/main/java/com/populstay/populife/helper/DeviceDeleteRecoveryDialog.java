package com.populstay.populife.helper;

import android.app.Activity;
import android.content.DialogInterface;
import android.text.InputType;
import android.text.TextUtils;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.populstay.populife.R;
import com.populstay.populife.net.BackendError;
import com.populstay.populife.common.Urls;
import com.populstay.populife.eventbus.Event;
import com.populstay.populife.net.RestClient;
import com.populstay.populife.net.callback.IError;
import com.populstay.populife.net.callback.IFailure;
import com.populstay.populife.net.callback.ISuccess;
import com.populstay.populife.util.dialog.DialogUtil;
import com.populstay.populife.util.log.PeachLogger;
import com.populstay.populife.util.storage.PeachPreference;
import com.populstay.populife.util.string.StringUtil;

import org.greenrobot.eventbus.EventBus;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

/**
 * 设备删除异常态的详情页恢复弹窗(见产品《设备删除一致性与异常恢复》第七节)。
 *
 * 两种弹窗,只在用户主动进入对应设备详情时出现,不在首页/后台自动打扰:
 *  A. 状态"物理删除已确认、等待账号移除"(PENDING_ACCOUNT):锁已重置、云端还留着,引导用户完成账号移除;
 *  B. 状态"删除结果待确认"(UNKNOWN/DELETING):不知锁到底删没删,引导用户靠近设备实测。
 *     —— B 的"原钥匙能开则自动恢复"由详情页的开锁成功回调处理(见 LockDetailFragment),
 *        本弹窗只负责"用户确认初始密码能开 → Remove"这条移除路径。
 *
 * Remove 一律要求重新输入 App 密码(原删除准备的授权可能已跨会话失效):
 *   输密码 → lock/user/check 校验 → lock/del/v2 完成账号移除(不重发物理删除)。
 *
 * 例外是{@link #onSameLockReAdded}:用户直接"覆盖添加"同一把锁时静默收尾,不弹任何窗。
 */
public class DeviceDeleteRecoveryDialog {

	private static final String TAG = "DeviceDeleteRecovery";

	private static AlertDialog sDialog;

	public interface OnRemovedListener {
		void onRemoved();
	}

	public static boolean isShowing() {
		return sDialog != null && sDialog.isShowing();
	}

	/**
	 * 同一把锁被重新添加(覆盖添加)成功后调用,静默收尾上一笔没做完的删除。
	 *
	 * 用户不走"完成移除"、直接重置后再添加同一把锁,是最常见的自救路径。此时:
	 *  - 物理锁已重置且已重新绑到本账号 —— 这本身就是"物理删除确实成功"的最强证据,不必再让用户确认;
	 *  - 服务端若换发了新 lockId,旧那条云端记录就是纯幽灵(MAC 已被新记录占用,再也打不开),
	 *    留着只会让首页多一台同名死设备、进详情页还弹窗打扰。
	 * 所以这里直接补发 lock/del/v2 把幽灵清掉。不需要再验密码:用户刚刚完成添加,所有权已经证明过了。
	 *
	 * 失败(断网/业务错)不做任何提示也不清本地记录 —— 详情页的恢复弹窗仍在,用户下次进去还能手动完成。
	 *
	 * @param newLockId 本次初始化服务端返回的 lockId
	 * @param lockMac   锁的 MAC
	 */
	public static void onSameLockReAdded(final String userId, int newLockId, final String lockMac) {
		final int ghostLockId = DeviceDeleteState.onSameLockReAdded(userId, newLockId, lockMac);
		if (ghostLockId <= 0) {
			return;
		}
		PeachLogger.d(TAG + " 覆盖添加,静默移除幽灵记录 ghostLockId=" + ghostLockId + " mac=" + lockMac);
		RestClient.builder()
				.url(Urls.LOCK_ADMIN_DELETE_V2)
				.params("userId", userId)
				.params("lockId", ghostLockId)
				.success(new ISuccess() {
					@Override
					public void onSuccess(String response) {
						boolean ok = false;
						try {
							JSONObject result = JSON.parseObject(response);
							Integer code = result.getInteger("code");
							Boolean success = result.getBoolean("success");
							ok = code != null && code == 200 && Boolean.TRUE.equals(success);
						} catch (Exception e) {
							e.printStackTrace();
						}
						PeachLogger.d(TAG + " 幽灵移除" + (ok ? "成功" : "失败") + " " + response);
						if (ok) {
							DeviceDeleteState.clearGhost(userId, ghostLockId, lockMac);
							PeachPreference.setBoolean(PeachPreference.HAVE_NEW_MESSAGE, true);
							EventBus.getDefault().post(new Event(Event.EventType.DELETE_LOCK_SUCCESS));
						}
					}
				})
				.failure(new IFailure() {
					@Override
					public void onFailure() {
						PeachLogger.d(TAG + " 幽灵移除失败(网络),保留记录待用户手动完成");
					}
				})
				.error(new IError() {
					@Override
					public void onError(int code, String msg) {
						PeachLogger.d(TAG + " 幽灵移除失败 code=" + code + " msg=" + msg);
					}
				})
				.build()
				.post();
	}

	public static void dismiss() {
		try {
			if (sDialog != null && sDialog.isShowing()) {
				sDialog.dismiss();
			}
		} catch (Exception ignored) {
		}
		sDialog = null;
	}

	/**
	 * @param pendingAccount true=状态A(完成设备移除);false=状态B(确认设备状态)
	 * @param onRemoved      账号移除成功后的回调(详情页据此刷新)
	 */
	public static void show(final Activity activity, final boolean pendingAccount,
							final String userId, final int lockId, final OnRemovedListener onRemoved) {
		if (activity == null || activity.isFinishing()) {
			return;
		}
		if (isShowing()) {
			return;
		}
		String title = activity.getString(pendingAccount ? R.string.delete_dlg_a_title : R.string.delete_dlg_b_title);
		String body = activity.getString(pendingAccount ? R.string.delete_dlg_a_body : R.string.delete_dlg_b_body);

		android.app.AlertDialog tipDialog = DialogUtil.showCommonDialog(activity, title, body,
				activity.getString(R.string.delete_dlg_remove),
				activity.getString(R.string.delete_dlg_later),
				new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						if (pendingAccount) {
							// 状态A:物理删除已确认,直接进入账号移除
							promptPasswordThenRemove(activity, userId, lockId, onRemoved);
						} else {
							// 状态B:物理状态还没确认,先过前置判定,不能直接移除
							confirmPhysicalResetThenRemove(activity, userId, lockId, onRemoved);
						}
					}
				},
				new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						// Later:保留记录,不移除;下次进详情再提示
					}
				});
		styleButtons(activity, tipDialog);
	}

	/**
	 * 状态B 的 Remove 前置判定(产品《设备删除一致性与异常恢复》七.B):
	 * 账号移除是不可逆的 —— 记录一删,lockData 和管理员权限跟着没了,App 再也发不出蓝牙删除指令。
	 * 若此时锁其实没被重置,它就变成谁也管不了的孤儿锁,只能让用户手动长按重置键。
	 *
	 * 所以必须先让用户实测确认锁已被重置(初始密码能开=已回到出厂态),确认了才允许移除;
	 * 开不了或连不上则保留记录、不移除,提示稍后重试或联系客服。
	 *
	 * 注意:这里不能"顺手再发一次蓝牙删除" —— 产品明确禁止在结果未知态重试物理删除(文档八.3)。
	 */
	private static void confirmPhysicalResetThenRemove(final Activity activity, final String userId,
													   final int lockId, final OnRemovedListener onRemoved) {
		android.app.AlertDialog checkDialog = DialogUtil.showCommonDialog(activity,
				activity.getString(R.string.delete_dlg_b_check_title),
				activity.getString(R.string.delete_dlg_b_check_body),
				activity.getString(R.string.delete_dlg_b_check_yes),
				activity.getString(R.string.delete_dlg_b_check_no),
				new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						// 用户确认锁已回到出厂态,可以完成账号移除
						PeachLogger.d(TAG + " 状态B 用户确认初始密码可开锁,进入账号移除 lockId=" + lockId);
						promptPasswordThenRemove(activity, userId, lockId, onRemoved);
					}
				},
				new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						// 开不了/连不上:物理状态仍未确认,保留记录,绝不移除
						PeachLogger.d(TAG + " 状态B 用户未能确认物理状态,保留记录不移除 lockId=" + lockId);
						Toast.makeText(activity, R.string.delete_dlg_b_check_blocked, Toast.LENGTH_LONG).show();
					}
				});
		styleButtons(activity, checkDialog);
	}

	/** 主操作(通往账号移除)标红警示;两个按钮都按原文大小写显示,不做系统默认的全大写 */
	private static void styleButtons(Activity activity, android.app.AlertDialog dialog) {
		Button positive = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
		if (positive != null) {
			positive.setAllCaps(false);
			positive.setTextColor(ContextCompat.getColor(activity, R.color.text_red_destructive));
		}
		Button negative = dialog.getButton(DialogInterface.BUTTON_NEGATIVE);
		if (negative != null) {
			negative.setAllCaps(false);
		}
	}

	private static void promptPasswordThenRemove(final Activity activity, final String userId,
												 final int lockId, final OnRemovedListener onRemoved) {
		final AlertDialog dialog = new AlertDialog.Builder(activity).create();
		dialog.setCanceledOnTouchOutside(false);
		dialog.show();
		Window window = dialog.getWindow();
		if (window == null) {
			return;
		}
		window.setContentView(R.layout.dialog_input);
		window.setBackgroundDrawableResource(android.R.color.transparent);
		sDialog = dialog;

		TextView tvTitle = window.findViewById(R.id.tv_dialog_input_title);
		final EditText etInput = window.findViewById(R.id.et_dialog_input_content);
		View btnCancel = window.findViewById(R.id.btn_dialog_input_cancel);
		View btnOk = window.findViewById(R.id.btn_dialog_input_ok);

		tvTitle.setText(R.string.enter_account_passwprd);
		etInput.setHint(R.string.enter_pwd);
		etInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

		DialogUtil.autoPopSoftInput(window, etInput);

		btnCancel.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				dialog.dismiss();
			}
		});
		btnOk.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				String pwd = etInput.getText().toString();
				if (TextUtils.isEmpty(pwd)) {
					Toast.makeText(activity, R.string.enter_pwd, Toast.LENGTH_SHORT).show();
					return;
				}
				dialog.dismiss();
				verifyThenRemove(activity, userId, lockId, pwd, onRemoved);
			}
		});
	}

	/** 删除准备(user/check 验密码)→ 账号移除(del/v2) */
	private static void verifyThenRemove(final Activity activity, final String userId,
										 final int lockId, String password, final OnRemovedListener onRemoved) {
		RestClient.builder()
				.url(Urls.LOCK_USER_CHECK)
				.loader(activity)
				.params("userId", userId)
				.params("lockId", lockId)
				.params("password", password)
				.success(new ISuccess() {
					@Override
					public void onSuccess(String response) {
						boolean ok = false;
						int errorRes = R.string.unsync_fail_business;
						try {
							JSONObject result = JSON.parseObject(response);
							Integer code = result.getInteger("code");
							ok = code != null && code == 200;
							if (code != null) errorRes = BackendError.messageRes(result, errorRes, BackendError.Credential.UNKNOWN);
						} catch (Exception e) {
							e.printStackTrace();
						}
						if (ok) {
							requestAccountRemove(activity, userId, lockId, onRemoved);
						} else {
							Toast.makeText(activity, errorRes, Toast.LENGTH_SHORT).show();
						}
					}
				})
				.failure(new IFailure() {
					@Override
					public void onFailure() {
						Toast.makeText(activity, R.string.unsync_fail_network, Toast.LENGTH_SHORT).show();
					}
				})
				.error(new IError() {
					@Override
					public void onError(int code, String msg) {
						Toast.makeText(activity, StringUtil.isBlank(msg) ? activity.getString(R.string.unsync_fail_business) : msg, Toast.LENGTH_SHORT).show();
					}
				})
				.build()
				.post();
	}

	private static void requestAccountRemove(final Activity activity, final String userId,
											 final int lockId, final OnRemovedListener onRemoved) {
		RestClient.builder()
				.url(Urls.LOCK_ADMIN_DELETE_V2)
				.loader(activity)
				.params("userId", userId)
				.params("lockId", lockId)
				.success(new ISuccess() {
					@Override
					public void onSuccess(String response) {
						PeachLogger.d(TAG + " del/v2 " + response);
						boolean ok = false;
                        int errorRes = R.string.unsync_fail_business;
						try {
							JSONObject result = JSON.parseObject(response);
							Integer code = result.getInteger("code");
                            if (code != null) errorRes = BackendError.messageRes(result, errorRes, BackendError.Credential.UNKNOWN);
							Boolean success = result.getBoolean("success");
							ok = code != null && code == 200 && Boolean.TRUE.equals(success);
						} catch (Exception e) {
							e.printStackTrace();
						}
						if (ok) {
							DeviceDeleteState.clear(userId, lockId);
							int lockNum = PeachPreference.getAccountLockNum(userId);
							PeachPreference.setAccountLockNum(userId, lockNum - 1);
							PeachPreference.setLastUnlockTimeAndType(lockId, 0, 0);
							PeachPreference.setBoolean(PeachPreference.HAVE_NEW_MESSAGE, true);
							Toast.makeText(activity, R.string.delete_removed_success, Toast.LENGTH_SHORT).show();
							EventBus.getDefault().post(new Event(Event.EventType.DELETE_LOCK_SUCCESS));
							if (onRemoved != null) {
								onRemoved.onRemoved();
							}
						} else {
							// 保留记录,下次可重试
							Toast.makeText(activity, errorRes, Toast.LENGTH_SHORT).show();
						}
					}
				})
				.failure(new IFailure() {
					@Override
					public void onFailure() {
						Toast.makeText(activity, R.string.unsync_fail_network, Toast.LENGTH_SHORT).show();
					}
				})
				.error(new IError() {
					@Override
					public void onError(int code, String msg) {
						Toast.makeText(activity, R.string.unsync_fail_business, Toast.LENGTH_SHORT).show();
					}
				})
				.build()
				.post();
	}
}
