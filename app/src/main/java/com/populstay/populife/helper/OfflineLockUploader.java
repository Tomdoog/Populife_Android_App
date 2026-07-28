package com.populstay.populife.helper;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.populstay.populife.base.BaseApplication;
import com.populstay.populife.common.Urls;
import com.populstay.populife.db.PopulifeDBUtil;
import com.populstay.populife.entity.OfflineLock;
import com.populstay.populife.eventbus.Event;
import com.populstay.populife.net.RestClient;
import com.populstay.populife.net.callback.IError;
import com.populstay.populife.net.callback.IFailure;
import com.populstay.populife.net.callback.ISuccess;
import com.populstay.populife.util.log.PeachLogger;
import com.populstay.populife.util.net.NetworkUtil;
import com.populstay.populife.util.storage.PeachPreference;
import com.populstay.populife.util.string.StringUtil;

import org.greenrobot.eventbus.EventBus;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 未同步设备(OfflineLock)同步器 —— 自动补传 / 首页弹窗 / Me 页手动同步 三处共用的统一入口。
 *
 * 背景:蓝牙初始化成功即落 OfflineLock 本地表,只有云端 init 成功才删。
 * 若绑定过程中断网/超时/闪退,记录会残留 → 设备"云端没有、打不开"。
 *
 * 三种调用方式(成功后的行为由调用方决定):
 *  1. {@link #tryUploadAll(String)}  自动补传,静默执行、不跳页,成功后仅刷新首页;
 *  2. {@link #syncOne(OfflineLock, SyncCallback)}  单条同步,带回调,供首页弹窗与 Me 页手动同步使用,
 *     调用方拿到成功回调后自行跳转设备结果/设置页。
 *
 * 统一保证:
 *  - 共享同一把并发锁,自动同步与手动同步不会并发(init 接口非幂等,重复调用会重置密码);
 *  - 上传前统一用 lock/get 按 mac 去重,云端已存在则清本地残留、不重发 init;
 *  - 失败一律保留本地记录等重试,绝不 reset 物理锁;
 *  - 失败原因统一分类为 {@link #FAIL_NETWORK} / {@link #FAIL_BUSINESS},并记录状态供首页兜底弹窗判断。
 */
public class OfflineLockUploader {

	private static final String TAG = "OfflineLockUploader";

	/** 网络类失败:无网络、请求发不出去、超时 —— 提示"网络连接不可用" */
	public static final int FAIL_NETWORK = 1;
	/** 非网络失败:服务端返回非成功、数据异常 —— 提示"同步失败,请重启 App" */
	public static final int FAIL_BUSINESS = 2;

	/** 同步状态记录(存 SharedPreferences,按 userId + lockMac 区分,不动数据库表) */
	private static final String KEY_SYNC_FAILED = "unsync_failed_";
	private static final String KEY_FAIL_TYPE = "unsync_fail_type_";
	private static final String KEY_NO_REMIND = "unsync_no_remind_";

	/** 单条同步的回调:供首页弹窗、Me 页手动同步使用 */
	public interface SyncCallback {
		/**
		 * @param data 云端 init 返回的 data(含 lockId/noKeyPwd 等);
		 *             若为 null 表示该设备云端已存在(去重命中),仅清理了本地残留、无需再跳设置页
		 */
		void onSuccess(OfflineLock lock, JSONObject data);

		void onFail(OfflineLock lock, int failType);
	}

	/** 内部统一的单条上传结果回调 */
	private interface InternalCallback {
		void onDone(boolean success, JSONObject data, int failType);
	}

	private static volatile OfflineLockUploader sInstance;

	/** 防重入:自动同步与手动同步共享,保证同一时刻只有一个同步任务在执行 */
	private final AtomicBoolean mUploading = new AtomicBoolean(false);

	private OfflineLockUploader() {
	}

	public static OfflineLockUploader getInstance() {
		if (sInstance == null) {
			synchronized (OfflineLockUploader.class) {
				if (sInstance == null) {
					sInstance = new OfflineLockUploader();
				}
			}
		}
		return sInstance;
	}

	// ==================================================================
	// 一、自动补传(静默,不跳页)
	// ==================================================================

	/**
	 * 触发一轮自动补传。可从任意线程调用,内部切主线程执行。
	 *
	 * @param from 触发来源,仅用于日志(MainActivity / network / foreground / lockList / me)
	 */
	public void tryUploadAll(final String from) {
		tryUploadAll(from, null);
	}

	/**
	 * @param onFinish 本轮结束后的回调(同步完成、无网、无待传记录都会回调;若已有同步在跑则不回调,
	 *                 由正在跑的那一轮负责)。供首页在同步结束后判断是否需要弹窗兜底提醒。
	 */
	public void tryUploadAll(final String from, final Runnable onFinish) {
		BaseApplication.getHandler().post(new Runnable() {
			@Override
			public void run() {
				doUploadAll(from, onFinish);
			}
		});
	}

	private void finish(Runnable onFinish) {
		if (onFinish != null) {
			onFinish.run();
		}
	}

	private void doUploadAll(final String from, final Runnable onFinish) {
		final String userId = PeachPreference.readUserId();
		if (StringUtil.isBlank(userId)) {
			PeachLogger.d(TAG + " skip: not logged in (from=" + from + ")");
			finish(onFinish);
			return;
		}
		if (!NetworkUtil.isNetConnected()) {
			PeachLogger.d(TAG + " skip: no network (from=" + from + ")");
			finish(onFinish);
			return;
		}
		if (!mUploading.compareAndSet(false, true)) {
			PeachLogger.d(TAG + " skip: already syncing (from=" + from + ")");
			return;
		}

		final List<OfflineLock> list = queryLocalList(userId);
		if (list == null || list.isEmpty()) {
			PeachLogger.d(TAG + " nothing to upload (from=" + from + ")");
			mUploading.set(false);
			finish(onFinish);
			return;
		}

		PeachLogger.d(TAG + " found " + list.size() + " local record(s) localMacs=" + collectLocalMacs(list)
				+ ", fetch cloud locks for dedupe (from=" + from + ")");

		fetchCloudLocks(userId, new CloudLocksCallback() {
			@Override
			public void onReady(Map<String, JSONObject> cloudLocks) {
				uploadNext(list, 0, cloudLocks, onFinish);
			}

			@Override
			public void onUnavailable() {
				// 拿不到云端状态就放弃本轮,避免重发 init 误伤已初始化的设备
				PeachLogger.d(TAG + " lock/get unavailable, abort this round");
				mUploading.set(false);
				finish(onFinish);
			}
		});
	}

	/** 串行逐条上传:一条处理完(成功/失败/去重跳过)再处理下一条,全部完成后释放锁 */
	private void uploadNext(final List<OfflineLock> list, final int index, final Map<String, JSONObject> cloudLocks,
						   final Runnable onFinish) {
		if (index >= list.size()) {
			mUploading.set(false);
			PeachLogger.d(TAG + " upload round finished");
			finish(onFinish);
			return;
		}

		final OfflineLock lock = list.get(index);
		final Runnable next = new Runnable() {
			@Override
			public void run() {
				uploadNext(list, index + 1, cloudLocks, onFinish);
			}
		};

		if (lock == null || StringUtil.isBlank(lock.getLockDataJson())) {
			next.run();
			return;
		}

		final InternalCallback done = new InternalCallback() {
			@Override
			public void onDone(boolean success, JSONObject data, int failType) {
				if (success) {
					EventBus.getDefault().post(new Event(Event.EventType.ADD_DEVICE_SUCCESS));
				}
				next.run();
			}
		};

		JSONObject cloudLock = getCloudLock(lock, cloudLocks);
		if (cloudLock != null) {
			// 云端已有:不重发 init,但仍需确认它已归属空间(否则首页看不到)
			PeachLogger.d(TAG + " dedupe: already in cloud " + lock.getLockMac());
			finalizeSync(lock, cloudLock, done);
		} else {
			uploadOne(lock, done);
		}
	}

	/**
	 * 云端 init 成功 / 去重命中后的收尾:<b>确认设备已归属某个空间,才算同步完成</b>。
	 *
	 * 首页是按空间(home)拉取设备的 —— 只把设备 init 到账号、却不绑定空间,它在首页根本不可见;
	 * 此时若又删掉了本地记录,设备就会从 App 里彻底消失(云端有、锁上也绑着),用户完全无从管理。
	 * 因此这里必须绑定空间成功后再删本地记录;绑不上就保留记录,设备至少还留在"未同步设备"里可见。
	 */
	private void finalizeSync(final OfflineLock lock, final JSONObject data, final InternalCallback callback) {
		// lock/get 返回带 home 字段:非空表示已归属空间,直接完成(init 返回没有该字段,视为未绑定)
		if (data != null && data.get("home") != null) {
			PeachLogger.d(TAG + " already in a space, sync done " + lock.getLockMac());
			completeSync(lock, data, callback);
			return;
		}

		final int lockId = data == null ? 0 : intOf(data.getInteger("lockId"));
		final String homeId = PeachPreference.getLastSelectHomeId();
		if (lockId <= 0 || StringUtil.isBlank(homeId)) {
			// 拿不到 lockId 或当前没有可用空间:保留本地记录,下次触发时再试
			PeachLogger.d(TAG + " cannot bind space (lockId=" + lockId + ", homeId=" + homeId
					+ "), keep local " + lock.getLockMac());
			markSyncFailed(lock.getUserId(), lock.getLockMac(), FAIL_BUSINESS);
			callback.onDone(false, data, FAIL_BUSINESS);
			return;
		}

		RestClient.builder()
				.url(Urls.LOCK_BIND_HOME)
				.params("lockId", String.valueOf(lockId))
				.params("homeId", homeId)
				.success(new ISuccess() {
					@Override
					public void onSuccess(String response) {
						boolean ok = false;
						try {
							JSONObject result = JSON.parseObject(response);
							Integer code = result.getInteger("code");
							ok = code != null && code == 200;
						} catch (Exception e) {
							e.printStackTrace();
						}
						if (ok) {
							PeachLogger.d(TAG + " bind space success, sync done " + lock.getLockMac());
							completeSync(lock, data, callback);
						} else {
							PeachLogger.d(TAG + " bind space business fail, keep local " + lock.getLockMac());
							markSyncFailed(lock.getUserId(), lock.getLockMac(), FAIL_BUSINESS);
							callback.onDone(false, data, FAIL_BUSINESS);
						}
					}
				})
				.failure(new IFailure() {
					@Override
					public void onFailure() {
						int failType = classifyNetworkFail();
						PeachLogger.d(TAG + " bind space onFailure, keep local " + lock.getLockMac());
						markSyncFailed(lock.getUserId(), lock.getLockMac(), failType);
						callback.onDone(false, data, failType);
					}
				})
				.error(new IError() {
					@Override
					public void onError(int code, String msg) {
						int failType = classifyNetworkFail();
						PeachLogger.d(TAG + " bind space onError " + code + " " + msg + ", keep local " + lock.getLockMac());
						markSyncFailed(lock.getUserId(), lock.getLockMac(), failType);
						callback.onDone(false, data, failType);
					}
				})
				.build()
				.post();
	}

	/** 同步真正完成:设备已在账号下且已归属空间,可以安全清除本地记录 */
	private void completeSync(OfflineLock lock, JSONObject data, InternalCallback callback) {
		deleteLocal(lock);
		clearSyncFailed(lock.getUserId(), lock.getLockMac());
		callback.onDone(true, data, 0);
	}

	private static int intOf(Integer value) {
		return value == null ? 0 : value;
	}

	// ==================================================================
	// 二、单条同步(带回调,供首页弹窗 / Me 页手动同步)
	// ==================================================================

	/**
	 * 用户主动触发的单条同步。与自动补传共享并发锁;若后台正在自动同步,稍后重试一次再放弃。
	 * 成功后本类只负责清本地记录与状态,跳转由调用方按业务决定。
	 */
	public void syncOne(final OfflineLock lock, final SyncCallback callback) {
		if (lock == null) {
			return;
		}
		if (!NetworkUtil.isNetConnected()) {
			PeachLogger.d(TAG + " syncOne: no network " + lock.getLockMac());
			markSyncFailed(lock.getUserId(), lock.getLockMac(), FAIL_NETWORK);
			notifyFail(callback, lock, FAIL_NETWORK);
			return;
		}
		acquireThenSync(lock, callback, 0);
	}

	/** 等待后台同步让出锁的轮询参数:最多 20 × 500ms = 10 秒 */
	private static final int MAX_WAIT_ROUND = 20;
	private static final long WAIT_INTERVAL_MS = 500;

	/**
	 * 后台自动同步很可能正在传同一台设备。此时若直接判失败,会在后台其实即将成功的情况下
	 * 向用户谎报"同步失败" —— 所以这里等它做完再执行:
	 * 拿到锁后走正常流程,若该设备已被后台传成功,去重会命中并按成功处理(还能拿到云端 lockId/密码)。
	 */
	private void acquireThenSync(final OfflineLock lock, final SyncCallback callback, final int round) {
		if (mUploading.compareAndSet(false, true)) {
			doSyncOne(lock, callback);
			return;
		}
		if (round >= MAX_WAIT_ROUND) {
			PeachLogger.d(TAG + " syncOne: background sync still running after wait, give up " + lock.getLockMac());
			notifyFail(callback, lock, FAIL_BUSINESS);
			return;
		}
		if (round == 0) {
			PeachLogger.d(TAG + " syncOne: background sync in progress, waiting for it " + lock.getLockMac());
		}
		BaseApplication.getHandler().postDelayed(new Runnable() {
			@Override
			public void run() {
				acquireThenSync(lock, callback, round + 1);
			}
		}, WAIT_INTERVAL_MS);
	}

	/** 调用前必须已持有 mUploading 锁 */
	private void doSyncOne(final OfflineLock lock, final SyncCallback callback) {
		final String userId = StringUtil.isBlank(lock.getUserId()) ? PeachPreference.readUserId() : lock.getUserId();

		fetchCloudLocks(userId, new CloudLocksCallback() {
			@Override
			public void onReady(Map<String, JSONObject> cloudLocks) {
				InternalCallback done = new InternalCallback() {
					@Override
					public void onDone(boolean success, JSONObject data, int failType) {
						mUploading.set(false);
						if (success) {
							EventBus.getDefault().post(new Event(Event.EventType.ADD_DEVICE_SUCCESS));
							if (callback != null) {
								// 回传云端数据,其中 lockId / noKeyPwd / 蓝牙密钥足以跳转设备结果页
								callback.onSuccess(lock, data);
							}
						} else if (callback != null) {
							callback.onFail(lock, failType);
						}
					}
				};

				JSONObject cloudLock = getCloudLock(lock, cloudLocks);
				if (cloudLock != null) {
					// 云端已存在(多为后台自动同步刚传成功):不重发 init,但仍要确认已归属空间
					PeachLogger.d(TAG + " syncOne dedupe: already in cloud " + lock.getLockMac());
					finalizeSync(lock, cloudLock, done);
				} else {
					uploadOne(lock, done);
				}
			}

			@Override
			public void onUnavailable() {
				mUploading.set(false);
				int failType = NetworkUtil.isNetConnected() ? FAIL_BUSINESS : FAIL_NETWORK;
				markSyncFailed(lock.getUserId(), lock.getLockMac(), failType);
				notifyFail(callback, lock, failType);
			}
		});
	}

	private void notifyFail(SyncCallback callback, OfflineLock lock, int failType) {
		if (callback != null) {
			callback.onFail(lock, failType);
		}
	}

	// ==================================================================
	// 三、云端去重(lock/get)
	// ==================================================================

	private interface CloudLocksCallback {
		/** key 为大写 mac,value 为云端返回的锁对象(含 lockId / noKeyPwd 等) */
		void onReady(Map<String, JSONObject> cloudLocks);

		/** 查询失败:拿不到云端状态,调用方应放弃本次上传 */
		void onUnavailable();
	}

	/** 拉取当前用户云端所有锁,按 mac 建索引用于去重;命中时其锁对象可直接用于跳转设备结果页 */
	private void fetchCloudLocks(String userId, final CloudLocksCallback callback) {
		RestClient.builder()
				.url(Urls.LOCK_GET)
				.params("userId", userId)
				.success(new ISuccess() {
					@Override
					public void onSuccess(String response) {
						Map<String, JSONObject> cloudLocks = parseCloudLocks(response);
						if (cloudLocks == null) {
							callback.onUnavailable();
							return;
						}
						PeachLogger.d(TAG + " cloud lock count=" + cloudLocks.size() + " cloudMacs=" + cloudLocks.keySet());
						callback.onReady(cloudLocks);
					}
				})
				.failure(new IFailure() {
					@Override
					public void onFailure() {
						PeachLogger.d(TAG + " lock/get onFailure");
						callback.onUnavailable();
					}
				})
				.error(new IError() {
					@Override
					public void onError(int code, String msg) {
						PeachLogger.d(TAG + " lock/get onError " + code + " " + msg);
						callback.onUnavailable();
					}
				})
				.build()
				.post();
	}

	/** code != 200 或解析异常返回 null,表示查询无效 */
	private Map<String, JSONObject> parseCloudLocks(String response) {
		try {
			JSONObject result = JSON.parseObject(response);
			Integer code = result.getInteger("code");
			if (code == null || code != 200) {
				return null;
			}
			Map<String, JSONObject> locks = new HashMap<>();
			JSONArray data = result.getJSONArray("data");
			if (data != null) {
				for (int i = 0; i < data.size(); i++) {
					JSONObject lock = data.getJSONObject(i);
					String mac = lock.getString("mac");
					if (!StringUtil.isBlank(mac)) {
						locks.put(mac.toUpperCase(), lock);
					}
				}
			}
			return locks;
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	/** 命中返回云端锁对象(含 lockId/noKeyPwd,可直接用于跳转设备结果页),未命中返回 null */
	private JSONObject getCloudLock(OfflineLock lock, Map<String, JSONObject> cloudLocks) {
		String mac = lock.getLockMac();
		if (mac == null || cloudLocks == null) {
			return null;
		}
		return cloudLocks.get(mac.toUpperCase());
	}

	// ==================================================================
	// 四、单条上传(云端 init)
	// ==================================================================

	private void uploadOne(OfflineLock lock, InternalCallback callback) {
		if (lock.getLockType() < 0) {
			// lockType < 0 为曼哈顿(MHT)锁
			uploadInit(lock, Urls.MH_LOCK_INIT, true, callback);
		} else {
			// KJX(TTLock)锁
			uploadInit(lock, Urls.LOCK_INIT, false, callback);
		}
	}

	private void uploadInit(final OfflineLock lock, String url, final boolean isMht, final InternalCallback callback) {
		final WeakHashMap<String, Object> params;
		try {
			params = isMht ? buildMhtParams(lock.getLockDataJson()) : buildKjxParams(lock.getLockDataJson());
		} catch (Exception e) {
			e.printStackTrace();
			PeachLogger.d(TAG + " parse lockData fail, keep local " + lock.getLockMac());
			markSyncFailed(lock.getUserId(), lock.getLockMac(), FAIL_BUSINESS);
			callback.onDone(false, null, FAIL_BUSINESS);
			return;
		}

		RestClient.builder()
				.url(url)
				.params(params)
				.success(new ISuccess() {
					@Override
					public void onSuccess(String response) {
						JSONObject data = null;
						boolean ok = false;
						try {
							JSONObject result = JSON.parseObject(response);
							Integer code = result.getInteger("code");
							if (code != null && code == 200) {
								ok = true;
								data = result.getJSONObject("data");
							} else {
								PeachLogger.d(TAG + " init business fail code=" + code + ", keep local " + lock.getLockMac());
							}
						} catch (Exception e) {
							e.printStackTrace();
						}

						if (ok) {
							// init 成功还不算完,必须再绑定空间,否则设备在首页不可见
							PeachLogger.d(TAG + " init success, binding space " + lock.getLockMac());
							finalizeSync(lock, data, callback);
						} else {
							markSyncFailed(lock.getUserId(), lock.getLockMac(), FAIL_BUSINESS);
							callback.onDone(false, null, FAIL_BUSINESS);
						}
					}
				})
				.failure(new IFailure() {
					@Override
					public void onFailure() {
						int failType = classifyNetworkFail();
						PeachLogger.d(TAG + " init onFailure(type=" + failType + "), keep local " + lock.getLockMac());
						markSyncFailed(lock.getUserId(), lock.getLockMac(), failType);
						callback.onDone(false, null, failType);
					}
				})
				.error(new IError() {
					@Override
					public void onError(int code, String msg) {
						int failType = classifyNetworkFail();
						PeachLogger.d(TAG + " init onError " + code + " " + msg + "(type=" + failType + "), keep local " + lock.getLockMac());
						markSyncFailed(lock.getUserId(), lock.getLockMac(), failType);
						callback.onDone(false, null, failType);
					}
				})
				.build()
				.post();
	}

	/**
	 * 请求层失败的原因归类:当下无网络即判定为网络问题,否则归为非网络失败。
	 * (RestClient 的失败回调不携带异常信息,只能据此判断)
	 */
	private int classifyNetworkFail() {
		return NetworkUtil.isNetConnected() ? FAIL_BUSINESS : FAIL_NETWORK;
	}

	// ==================================================================
	// 五、本地记录与同步状态
	// ==================================================================

	public List<OfflineLock> queryLocalList(String userId) {
		try {
			return PopulifeDBUtil.getInstance(BaseApplication.getApplication()).queryByUserId(userId);
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	private void deleteLocal(OfflineLock lock) {
		try {
			PopulifeDBUtil.getInstance(BaseApplication.getApplication())
					.deleteByUserAndMac(lock.getUserId(), lock.getLockMac());
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static String prefKey(String prefix, String userId, String mac) {
		return prefix + (userId == null ? "" : userId) + "_" + (mac == null ? "" : mac);
	}

	/** 记录同步失败(供首页兜底弹窗判断是否提醒、以及展示哪种错误文案) */
	public static void markSyncFailed(String userId, String mac, int failType) {
		PeachPreference.setBoolean(prefKey(KEY_SYNC_FAILED, userId, mac), true);
		PeachPreference.putStr(prefKey(KEY_FAIL_TYPE, userId, mac), String.valueOf(failType));
	}

	public static void clearSyncFailed(String userId, String mac) {
		PeachPreference.setBoolean(prefKey(KEY_SYNC_FAILED, userId, mac), false);
		PeachPreference.putStr(prefKey(KEY_FAIL_TYPE, userId, mac), "");
	}

	public static boolean hasSyncFailed(String userId, String mac) {
		return PeachPreference.getBoolean(prefKey(KEY_SYNC_FAILED, userId, mac));
	}

	public static int getLastFailType(String userId, String mac) {
		String v = PeachPreference.getStr(prefKey(KEY_FAIL_TYPE, userId, mac));
		if (StringUtil.isBlank(v)) {
			return FAIL_BUSINESS;
		}
		try {
			return Integer.parseInt(v);
		} catch (Exception e) {
			return FAIL_BUSINESS;
		}
	}

	/** "不再提醒我同步此设备":只影响首页弹窗,不影响自动同步与 Me 页入口 */
	public static void setDoNotRemind(String userId, String mac, boolean noRemind) {
		PeachPreference.setBoolean(prefKey(KEY_NO_REMIND, userId, mac), noRemind);
	}

	public static boolean isDoNotRemind(String userId, String mac) {
		return PeachPreference.getBoolean(prefKey(KEY_NO_REMIND, userId, mac));
	}

	private String collectLocalMacs(List<OfflineLock> list) {
		StringBuilder sb = new StringBuilder("[");
		for (int i = 0; i < list.size(); i++) {
			if (i > 0) {
				sb.append(", ");
			}
			OfflineLock l = list.get(i);
			sb.append(l == null ? "null" : l.getLockMac());
		}
		return sb.append("]").toString();
	}

	// ==================================================================
	// 六、init 参数构造
	// 与 OfflineLockListActivity.parseLockData / mhParseLockData 保持一致,
	// 仅保留上传所需参数,去掉用于 reset / 跳转成功页的 mKey / mHomeDevice。
	// ==================================================================

	private WeakHashMap<String, Object> buildKjxParams(String lockDataJson) {
		JSONObject lockInfo = JSON.parseObject(lockDataJson);
		WeakHashMap<String, Object> params = new WeakHashMap<>();
		params.put("userId", PeachPreference.readUserId());
		String lockName = lockInfo.getString("lockName");
		params.put("name", lockName);
		params.put("lockName", lockName);
		params.put("mac", lockInfo.getString("lockMac"));
		params.put("lockData", lockInfo.getString("lockData"));
		params.put("key", lockInfo.getString("lockKey"));
		params.put("flagPos", lockInfo.getInteger("lockFlagPos"));
		params.put("aesKey", lockInfo.getString("aesKeyStr"));
		params.put("adminPwd", lockInfo.getString("adminPwd"));
		params.put("noKeyPwd", lockInfo.getString("noKeyPwd"));
		String deletePwd = lockInfo.getString("deletePwd");
		params.put("deletePwd", StringUtil.isBlank(deletePwd) ? "" : deletePwd);
		params.put("pwdInfo", lockInfo.getString("pwdInfo"));
		params.put("timestamp", lockInfo.getString("timestamp"));
		params.put("specialValue", lockInfo.getInteger("specialValue"));
		params.put("electricQuantity", lockInfo.getInteger("electricQuantity"));
		params.put("timezoneRawOffSet", String.valueOf(lockInfo.getInteger("timezoneRawOffset")));
		params.put("modelNum", lockInfo.getString("modelNum"));
		params.put("hardwareRevision", lockInfo.getString("hardwareRevision"));
		params.put("firmwareRevision", lockInfo.getString("firmwareRevision"));
		JSONObject lockVersion = lockInfo.getJSONObject("lockVersion");
		params.put("protocolType", lockVersion.getInteger("protocolType"));
		params.put("protocolVersion", lockVersion.getInteger("protocolVersion"));
		params.put("scene", lockVersion.getInteger("scene"));
		params.put("groupId", lockVersion.getInteger("groupId"));
		params.put("orgId", lockVersion.getInteger("orgId"));
		// Retrofit 的 FieldMap 不接受 null 值(否则抛 "Field map contained null value" 走 onFailure),
		// 剔除 null 项再提交;字段缺失的锁因此少传若干可选字段,由服务端按可选处理。
		params.values().removeAll(Collections.singleton(null));
		return params;
	}

	private WeakHashMap<String, Object> buildMhtParams(String lockDataJson) {
		JSONObject lockInfo = JSON.parseObject(lockDataJson);
		WeakHashMap<String, Object> params = new WeakHashMap<>();
		params.put("userId", PeachPreference.readUserId());
		String lockName = lockInfo.getString("lockName");
		params.put("name", lockName);
		params.put("lockName", lockName);
		params.put("alias", lockName);
		params.put("mac", lockInfo.getString("lockMac"));
		params.put("aesKey", lockInfo.getString("aesKey"));
		params.put("timestamp", lockInfo.getString("timestamp"));
		params.put("specialValue", 1230);
		params.put("electricQuantity", lockInfo.getInteger("batteryLevel"));
		params.put("timezoneRawOffSet", String.valueOf(lockInfo.getInteger("timezoneRawOffset")));
		params.put("modelNum", lockInfo.getString("modelNum"));
		params.put("hardwareRevision", lockInfo.getString("hardwareVersion"));
		params.put("firmwareRevision", lockInfo.getString("firmwareVersion"));
		params.put("protocolVersion", 1);
		params.values().removeAll(Collections.singleton(null));
		return params;
	}
}
