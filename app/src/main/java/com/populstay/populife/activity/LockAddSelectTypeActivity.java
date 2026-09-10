package com.populstay.populife.activity;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.populstay.populife.R;
import com.populstay.populife.adapter.DeviceListAdapter;
import com.populstay.populife.base.BaseActivity;
import com.populstay.populife.home.entity.HomeDevice;
import com.populstay.populife.home.entity.HomeDeviceInfo;
import com.populstay.populife.util.log.PeachLogger;

import java.util.ArrayList;
import java.util.List;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class LockAddSelectTypeActivity extends BaseActivity implements View.OnClickListener {

	private LinearLayout mLlDeadbolt, mLlKeybox, mLlMortise, mLlMoonLock;

	private RecyclerView mDeviceListView;
	private DeviceListAdapter mDeviceListAdapter;
	private List<HomeDevice> mDeviceList;
	private ImageView mIvNewMsg;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_lock_add_select_type);
		initData();
		initView();
		initListener();
	}

	private void initData() {
		mDeviceList = new ArrayList<>();

		// 密码盒
		HomeDevice device = new HomeDevice();
		device.setName(HomeDeviceInfo.IDeviceName.NAME_LOCK_KEY_BOX);
		mDeviceList.add(device);

		// K4
		device = new HomeDevice();
		device.setName(HomeDeviceInfo.IDeviceName.NAME_LOCK_PPL_KB4_S);
		mDeviceList.add(device);

		// P3 指纹挂锁
		device = new HomeDevice();
		device.setName(HomeDeviceInfo.IDeviceName.NAME_LOCK_PADLOCK);
		mDeviceList.add(device);

		// 横闩锁
		device = new HomeDevice();
		device.setName(HomeDeviceInfo.IDeviceName.NAME_LOCK_DEADBOLT);
		mDeviceList.add(device);

		// 网关
		device = new HomeDevice();
		device.setName(HomeDeviceInfo.IDeviceName.NAME_GATEWAY);
		mDeviceList.add(device);

		// 门锁（kjx 生态锁，统一处理）
		device = new HomeDevice();
		device.setName(HomeDeviceInfo.IDeviceName.NAME_LOCK_KJX_DOOR_LOCK);
		mDeviceList.add(device);

		// 曼哈顿把手锁
		device = new HomeDevice();
		device.setName(HomeDeviceInfo.IDeviceName.NAME_LOCK_MANHATTAN_MOON_LOCK);
		mDeviceList.add(device);
	}

	private void initView() {
		((TextView) findViewById(R.id.page_title)).setText(R.string.add_device);

		mLlDeadbolt = findViewById(R.id.ll_lock_type_deadbolt);
		mLlKeybox = findViewById(R.id.ll_lock_type_keybox);
		mLlMortise = findViewById(R.id.ll_lock_type_mortise);
		mLlMoonLock = findViewById(R.id.ll_lock_type_moonlock);

		mDeviceListView = findViewById(R.id.device_list_recyclerview);
//		mDeviceListView.setLayoutManager(new GridLayoutManager(this,2));
		mDeviceListView.setLayoutManager(new LinearLayoutManager(this));
		mDeviceListAdapter = new DeviceListAdapter(mDeviceList, this, DeviceListAdapter.SHOW_TYPE_CARD, DeviceListAdapter.USE_FROM_SELECT_DEVICE_TYPE_LIST);
		mDeviceListView.setAdapter(mDeviceListAdapter);
		mDeviceListAdapter.setOnItemClickListener(new DeviceListAdapter.OnItemClickListener() {
			@Override
			public void onItemClick(View v, int position) {
				mDeviceListAdapter.selectItem(position);
				HomeDevice device = mDeviceList.get(position);

				if (device.getName().startsWith(HomeDeviceInfo.IDeviceName.NAME_GATEWAY)) {
					goToNewActivity(GatewayAddGuideActivity.class);
				} else if (HomeDeviceInfo.isDeadboltLack(device.getName())
						|| HomeDeviceInfo.isKeyBox(device.getName())
						|| HomeDeviceInfo.isKeyBoxK4(device.getName())
						|| HomeDeviceInfo.isPadlock(device.getName())
						|| device.getName().startsWith(HomeDeviceInfo.IDeviceName.NAME_LOCK_KJX_DOOR_LOCK)
						|| device.getName().startsWith(HomeDeviceInfo.IDeviceName.NAME_LOCK_MANHATTAN_MOON_LOCK)) {
					LockAddGuideActivity.actionStartAddLock(LockAddSelectTypeActivity.this, device.getName());
				}
			}
		});

		initTitleBarRightBtn();
	}

	private void initTitleBarRightBtn() {
		findViewById(R.id.page_action).setVisibility(View.GONE);

		mIvNewMsg = findViewById(R.id.iv_main_lock_msg_new);
		View tvSupport = findViewById(R.id.rl_main_lock_online_service);
		tvSupport.setVisibility(View.VISIBLE);
		tvSupport.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				startImServiceActivity(LockAddSelectTypeActivity.this);
			}
		});
	}

	@Override
	protected void onResume() {
		super.onResume();
	}

	private void initListener() {
		mLlDeadbolt.setOnClickListener(this);
		mLlKeybox.setOnClickListener(this);
		mLlMortise.setOnClickListener(this);
		mLlMoonLock.setOnClickListener(this);
	}

    @Override
    public void onClick(View view) {
        int id = view.getId();

        if (id == R.id.ll_lock_type_deadbolt) {
            if (isBleNetEnableWithToast()) {
                LockAddGuideActivity.actionStartAddLock(LockAddSelectTypeActivity.this, "");
            }
        } else if (id == R.id.ll_lock_type_keybox) {
            if (isBleNetEnableWithToast()) {
                goToNewActivity(LockAddGuideKeyboxOpenActivity.class);
            }
        } else if (id == R.id.ll_lock_type_mortise) {
            if (isBleNetEnableWithToast()) {
                goToNewActivity(FoundDeviceActivity.class);
            }
        } else if (id == R.id.ll_lock_type_moonlock) {
            if (isBleNetEnableWithToast()) {
                LockAddGuideActivity.actionStartAddLock(LockAddSelectTypeActivity.this, "");
            }
        }
    }
}
