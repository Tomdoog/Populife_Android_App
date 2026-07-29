package com.populstay.populife.util.dialog;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;

/**
 * AlertDialog 对话框工具类
 * Created by Jerry
 */

public class DialogUtil {

	private static AlertDialog DIALOG;

	/**
	 * 返回一个列表对话框
	 *
	 * @param context               上下文
	 * @param title                 对话框标题
	 * @param message               对话框列表内容
	 * @param positiveContent       "确定"按钮要显示的内容
	 * @param negativeContent       "取消"按钮要显示的内容
	 * @param positiveClickListener "确认"按钮的监听事件
	 * @param negativeClickListener "取消"按钮的监听事件
	 */
	public static AlertDialog showCommonDialog(Context context, String title, String message,
										String positiveContent, String negativeContent,
										DialogInterface.OnClickListener positiveClickListener,
										DialogInterface.OnClickListener negativeClickListener) {
		if(null != DIALOG && DIALOG.isShowing()){
			DIALOG.dismiss();
		}

		AlertDialog.Builder builder = new AlertDialog.Builder(context);
		builder.setTitle(title)
				.setMessage(message)
				.setCancelable(false)
				.setPositiveButton(positiveContent, positiveClickListener)
				.setNegativeButton(negativeContent, negativeClickListener);

		DIALOG = builder.create();
		DIALOG.setCanceledOnTouchOutside(false);
		DIALOG.show();
		return DIALOG;
	}

	/**
	 * 让带输入框的弹窗自动弹出软键盘。
	 *
	 * 全App的 dialog_input 弹窗都是"先 show() 再 window.setContentView()",窗口在 show() 时就已完成
	 * 首次布局和焦点分配,后塞进去的 EditText 拿不到焦点,表现为点输入框键盘不弹。这里统一补上:
	 * 给窗口开输入法、清掉可能残留的不可聚焦标志、把焦点交给输入框,并在下一帧主动拉起键盘。
	 *
	 * @param window 弹窗的 Window(已 setContentView)
	 * @param input  弹窗里的输入框;为 null 或不可见时不处理
	 */
	public static void autoPopSoftInput(Window window, final EditText input) {
		if (window == null || input == null || input.getVisibility() != View.VISIBLE) {
			return;
		}
		window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
				| WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
		window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
				| WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
		input.setFocusable(true);
		input.setFocusableInTouchMode(true);
		input.requestFocus();
		input.post(new Runnable() {
			@Override
			public void run() {
				InputMethodManager imm = (InputMethodManager) input.getContext()
						.getSystemService(Context.INPUT_METHOD_SERVICE);
				if (imm != null) {
					imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
				}
			}
		});
	}

	/**
	 * 新建一个含有多个 item 的 dialog 对话框，并显示
	 *
	 * @param context       上下文
	 * @param title         对话框标题
	 * @param items         对话框列表内容
	 * @param clickListener 列表内容的点击事件
	 */
	public static void showListDialog(Context context, String title, String[] items,
									  DialogInterface.OnClickListener clickListener) {
		AlertDialog.Builder dialog = new AlertDialog.Builder(context);
		dialog.setTitle(title)
				.setItems(items, clickListener)
				.show();
	}

	public interface DialogButtonInterface {
		void positiveClick(String positiveContent, String positiveClickListener);

		void negativeClick(String negativeContent, String negativeClickListener);
	}

}
