package com.populstay.populife.net;

import android.content.Context;

import com.populstay.populife.net.callback.IError;
import com.populstay.populife.net.callback.IFailure;
import com.populstay.populife.net.callback.IRequest;
import com.populstay.populife.net.callback.ISuccess;
import com.populstay.populife.net.callback.RequestCallbacks;
import com.populstay.populife.net.download.DownloadHandler;
import com.populstay.populife.ui.loader.LoaderStyle;
import com.populstay.populife.ui.loader.PeachLoader;
import com.populstay.populife.util.log.PeachLogger;

import java.io.File;
import java.util.WeakHashMap;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;

/**
 * Created by Jerry
 */
public class RestClient {

	private final WeakHashMap<String, Object> PARAMS;
	private final String URL;
	private final IRequest REQUEST;
	private final String DOWNLOAD_DIR;
	private final String EXTENSION;
	private final String NAME;
	private final ISuccess SUCCESS;
	private final IFailure FAILURE;
	private final IError ERROR;
	private final RequestBody BODY;
	private final LoaderStyle LOADER_STYLE;
	private final File FILE;
	private final Context CONTEXT;
	private boolean mIsCloseLoadDialog = true;

	public RestClient(String url,
					  WeakHashMap<String, Object> params,
					  String downloadDir,
					  String extension,
					  String name,
					  IRequest request,
					  ISuccess success,
					  IFailure failure,
					  IError error,
					  RequestBody body,
					  File file,
					  Context context,
					  LoaderStyle loaderStyle) {
		this.URL = url;
		this.PARAMS = params;
		this.DOWNLOAD_DIR = downloadDir;
		this.EXTENSION = extension;
		this.NAME = name;
		this.REQUEST = request;
		this.SUCCESS = success;
		this.FAILURE = failure;
		this.ERROR = error;
		this.BODY = body;
		this.FILE = file;
		this.CONTEXT = context;
		this.LOADER_STYLE = loaderStyle;
	}

	public boolean isIsCloseLoadDialog() {
		return mIsCloseLoadDialog;
	}

	public void setIsCloseLoadDialog(boolean mIsCloseLoadDialog) {
		this.mIsCloseLoadDialog = mIsCloseLoadDialog;
	}

	public static RestClientBuilder builder() {
		return new RestClientBuilder();
	}

	private void request(HttpMethod method) {
		if (method == HttpMethod.GET || method == HttpMethod.POST
				|| method == HttpMethod.PUT || method == HttpMethod.DELETE) {
			ScienerApiVersion.addTo(URL, PARAMS);
		}
//		//判断网络连接状态
//		if (NetworkUtil.isNetworkAvailable(BaseApplication.getApplication())) {
			final RestService service = RestCreator.getRestService();
			Call<String> call = null;

			if (REQUEST != null) {
				REQUEST.onRequestStart();
			}

			if (LOADER_STYLE != null) {
				PeachLoader.showLoading(CONTEXT, LOADER_STYLE);
			}

		  PeachLogger.d("Http", "request--url=" + URL + ", PARAMS=" + (null != PARAMS ? PARAMS.toString() : ""));

			switch (method) {
				case GET:
					call = service.get(URL, PARAMS);
					break;

				case POST:
					call = service.post(URL, PARAMS);
					break;

				case POST_RAW:
					call = service.postRaw(URL, BODY);
					break;

				case PUT:
					call = service.put(URL, PARAMS);
					break;

				case PUT_RAW:
					call = service.putRaw(URL, BODY);
					break;

				case DELETE:
					call = service.delete(URL, PARAMS);
					break;

				case UPLOAD:
					final RequestBody requestBody =
							RequestBody.create(MediaType.parse(MultipartBody.FORM.toString()), FILE);
					final MultipartBody.Part body =
							MultipartBody.Part.createFormData("file", FILE.getName(), requestBody);
					call = service.upload(URL, body);
					break;

				default:
					break;
			}

			if (call != null) {
				call.enqueue(getRequestCallback());
			}
//		} else {
//			ToastUtil.showToast(R.string.note_network_error);
//		}
	}

	private Callback<String> getRequestCallback() {
		return new RequestCallbacks(
				REQUEST,
				SUCCESS,
				FAILURE,
				ERROR,
				LOADER_STYLE,
				mIsCloseLoadDialog
		);
	}

	public final void get() {
		request(HttpMethod.GET);
	}

	public final void post() {
		if (BODY == null) {
			request(HttpMethod.POST);
		} else {
			if (!PARAMS.isEmpty()) {
				throw new RuntimeException("params must be null!");
			}
			request(HttpMethod.POST_RAW);
		}
	}

	public final void put() {
		if (BODY == null) {
			request(HttpMethod.PUT);
		} else {
			if (!PARAMS.isEmpty()) {
				throw new RuntimeException("params must be null!");
			}
			request(HttpMethod.PUT_RAW);
		}
	}

	public final void delete() {
		request(HttpMethod.DELETE);
	}

	public final void upload() {
		request(HttpMethod.UPLOAD);
	}

	public final void download() {
		new DownloadHandler(URL, PARAMS, REQUEST, DOWNLOAD_DIR, EXTENSION, NAME,
				SUCCESS, FAILURE, ERROR)
				.handleDownload();
	}
}
