package com.populstay.populife.helper;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.populstay.populife.R;
import com.populstay.populife.util.log.PeachLogger;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 导出 App 运行日志:把本地日志目录打包成 zip,通过系统分享(微信/邮件等)发给客服。
 *
 * 日志由 BaseApplication.initLogger 写在 getExternalCacheDir()/logs/ 下 —— 该目录在 Android 11+
 * 上用户用文件管理器进不去,所以必须提供 App 内一键导出,否则日志送不到客服手里。
 *
 * 注意:按产品要求日志内容<b>未脱敏</b>,zip 里含明文的开锁密码/密钥(adminPwd/noKeyPwd/aesKeyStr/lockData)。
 */
public class LogExportHelper {

	private static final String TAG = "LogExportHelper";

	public static void exportAndShare(final Activity activity) {
		if (activity == null) {
			return;
		}
		File cacheDir = activity.getExternalCacheDir();
		if (cacheDir == null) {
			Toast.makeText(activity, R.string.log_export_fail, Toast.LENGTH_SHORT).show();
			return;
		}
		final File logDir = new File(cacheDir, "logs");
		File[] files = logDir.listFiles();
		if (files == null || files.length == 0) {
			Toast.makeText(activity, R.string.log_export_none, Toast.LENGTH_SHORT).show();
			return;
		}

		Toast.makeText(activity, R.string.log_export_preparing, Toast.LENGTH_SHORT).show();
		// 打包放后台线程,避免日志较多时卡 UI
		new Thread(new Runnable() {
			@Override
			public void run() {
				final File zip = zipLogs(activity, logDir);
				activity.runOnUiThread(new Runnable() {
					@Override
					public void run() {
						if (activity.isFinishing()) {
							return;
						}
						if (zip == null) {
							Toast.makeText(activity, R.string.log_export_fail, Toast.LENGTH_SHORT).show();
							return;
						}
						share(activity, zip);
					}
				});
			}
		}).start();
	}

	private static File zipLogs(Activity activity, File logDir) {
		ZipOutputStream zos = null;
		try {
			File zip = new File(activity.getExternalCacheDir(), "populife_logs_" + System.currentTimeMillis() + ".zip");
			zos = new ZipOutputStream(new FileOutputStream(zip));
			byte[] buf = new byte[8192];
			File[] files = logDir.listFiles();
			if (files != null) {
				for (File f : files) {
					if (f == null || !f.isFile()) {
						continue;
					}
					BufferedInputStream bis = null;
					try {
						bis = new BufferedInputStream(new FileInputStream(f));
						zos.putNextEntry(new ZipEntry(f.getName()));
						int len;
						while ((len = bis.read(buf)) != -1) {
							zos.write(buf, 0, len);
						}
						zos.closeEntry();
					} finally {
						if (bis != null) {
							try {
								bis.close();
							} catch (Exception ignored) {
							}
						}
					}
				}
			}
			zos.close();
			zos = null;
			return zip;
		} catch (Exception e) {
			e.printStackTrace();
			PeachLogger.d(TAG + " zip fail: " + e.getMessage());
			return null;
		} finally {
			if (zos != null) {
				try {
					zos.close();
				} catch (Exception ignored) {
				}
			}
		}
	}

	private static void share(Activity activity, File zip) {
		try {
			Uri uri = FileProvider.getUriForFile(activity, activity.getPackageName() + ".provider", zip);
			Intent intent = new Intent(Intent.ACTION_SEND);
			intent.setType("application/zip");
			intent.putExtra(Intent.EXTRA_STREAM, uri);
			intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
			activity.startActivity(Intent.createChooser(intent, activity.getString(R.string.log_export_share_title)));
		} catch (Exception e) {
			e.printStackTrace();
			PeachLogger.d(TAG + " share fail: " + e.getMessage());
			Toast.makeText(activity, R.string.log_export_fail, Toast.LENGTH_SHORT).show();
		}
	}
}
