package yc.bluetooth.androidble;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import yc.bluetooth.androidble.ble.BLEManager;
import yc.bluetooth.androidble.ble.OnBleConnectListener;
import yc.bluetooth.androidble.ble.OnDeviceSearchListener;
import yc.bluetooth.androidble.permission.PermissionListener;
import yc.bluetooth.androidble.permission.PermissionRequest;
import yc.bluetooth.androidble.util.TypeConversion;

import android.graphics.Color;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ToggleButton;

import com.jjoe64.graphview.GraphView.GraphViewData;
import com.jjoe64.graphview.GraphView.LegendAlign;
import com.jjoe64.graphview.GraphViewSeries;
import com.jjoe64.graphview.GraphViewSeries.GraphViewSeriesStyle;
import com.jjoe64.graphview.GraphViewStyle;
import com.jjoe64.graphview.LineGraphView;


/**
 * BLE开发
 */
public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private static final String TAG = "BLEMain";

    //bt_patch(mtu).bin
    public static final String SERVICE_UUID = "000000ff-0000-1000-8000-00805f9b34fb"; //蓝牙通讯服务
    public static final String READ_UUID = "0000ff01-0000-1000-8000-00805f9b34fb";  //读特征
    public static final String WRITE_UUID = "0000ff03-0000-1000-8000-00805f9b34fb";  //写特征

    //设置命令

    public final static String sendControlBegin = "1101";
    public final static String sendControlStop = "1102";
    public final static String sendControlPaused = "1103";

    // 存储字符串数据
    public static int adc1Value = 0;
    public static int adc2Value = 0;
    public static int adc3Value = 0;

    //动态申请权限
    private String[] requestPermissionArray = new String[]{
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
    };
    // 声明一个集合，在后面的代码中用来存储用户拒绝授权的权限
    private List<String> deniedPermissionList = new ArrayList<>();

    private static final int CONNECT_SUCCESS = 0x01;
    private static final int CONNECT_FAILURE = 0x02;
    private static final int DISCONNECT_SUCCESS = 0x03;
    private static final int SEND_SUCCESS = 0x04;
    private static final int SEND_FAILURE= 0x05;
    private static final int RECEIVE_SUCCESS= 0x06;
    private static final int RECEIVE_FAILURE =0x07;
    private static final int START_DISCOVERY = 0x08;
    private static final int STOP_DISCOVERY = 0x09;
    private static final int DISCOVERY_DEVICE = 0x0A;
    private static final int DISCOVERY_OUT_TIME = 0x0B;
    private static final int SELECT_DEVICE = 0x0C;
    private static final int BT_OPENED = 0x0D;
    private static final int BT_CLOSED = 0x0E;


    private Button btSearch;
    private TextView tvCurConState;
    private TextView tvName;
    private TextView tvAddress;
    private Button btConnect;
    private Button btDisconnect;
    private EditText etSendMsg;
    private Button btSend;
    private Button btStart;
    private Button btStop;
    private Button btSuspend;
    private TextView tvSendResult;
    private TextView tvReceive;
    private LinearLayout llDeviceList;
    private LinearLayout llDataSendReceive;
    private ListView lvDevices;
    private LVDevicesAdapter lvDevicesAdapter;

    private boolean if_update_value = false;

    private Context mContext;
    private BLEManager bleManager;
    private BLEBroadcastReceiver bleBroadcastReceiver;
    private BluetoothDevice curBluetoothDevice;  //当前连接的设备
    //当前设备连接状态
    private boolean curConnState = false;

    private static final boolean D = MainActivity.D;

    private static LineGraphView graphView;
    private static GraphViewSeries adc1, adc2, adc3;
    private static double counter = 100d;

    private static CheckBox mCheckBox1, mCheckBox2, mCheckBox3;
    private static EditText mQangle, mQbias, mRmeasure;

    private static double[][] buffer = new double[3][101]; // Used to store the 101 last readings

    public MainActivity()
    {
        for (int i = 0; i < 3; i++)
            for (int i2 = 0; i2 < buffer[i].length; i2++)
                buffer[i][i2] = 180d;
    }


    @SuppressLint("HandlerLeak")
    private Handler mHandler = new Handler(){
        @SuppressLint("SetTextI18n")
        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);

            switch(msg.what){
                case START_DISCOVERY:
                    Log.d(TAG, "开始搜索设备...");
                    break;

                case STOP_DISCOVERY:
                    Log.d(TAG, "停止搜索设备...");
                    break;

                case DISCOVERY_DEVICE:  //扫描到设备
                    BLEDevice bleDevice = (BLEDevice) msg.obj;
                    lvDevicesAdapter.addDevice(bleDevice);
//                    if (lvDevices != null) {
//                        setListViewHeightByItemCount(lvDevices);
//                    }

                    break;

                case SELECT_DEVICE:
                    BluetoothDevice bluetoothDevice = (BluetoothDevice) msg.obj;
                    tvName.setText(bluetoothDevice.getName());
                    tvAddress.setText(bluetoothDevice.getAddress());
                    curBluetoothDevice = bluetoothDevice;
                    break;

                case CONNECT_FAILURE: //连接失败
                    Log.d(TAG, "连接失败");
                    tvCurConState.setText("连接失败");
                    curConnState = false;
                    break;

                case CONNECT_SUCCESS:  //连接成功
                    Log.d(TAG, "连接成功");
                    tvCurConState.setText("连接成功");
                    curConnState = true;
                    llDataSendReceive.setVisibility(View.VISIBLE);
                    llDeviceList.setVisibility(View.GONE);
                    break;

                case DISCONNECT_SUCCESS:
                    if_update_value = false;
                    Log.d(TAG, "断开成功");
                    tvCurConState.setText("断开成功");
                    curConnState = false;

                    break;

                case SEND_FAILURE: //发送失败
                    byte[] sendBufFail = (byte[]) msg.obj;
                    String sendFail = TypeConversion.bytes2HexString(sendBufFail,sendBufFail.length);
                    tvSendResult.setText("发送数据失败，长度" + sendBufFail.length + "--> " + sendFail);
                    break;

                case SEND_SUCCESS:  //发送成功
                    byte[] sendBufSuc = (byte[]) msg.obj;
                    String sendResult = TypeConversion.bytes2HexString(sendBufSuc,sendBufSuc.length);
                    tvSendResult.setText("发送数据成功，长度" + sendBufSuc.length + "--> " + sendResult);
                    break;

                case RECEIVE_FAILURE: //接收失败
                    String receiveError = (String) msg.obj;
                    tvReceive.setText(receiveError);
                    break;

                case RECEIVE_SUCCESS:  //接收成功
                    byte[] recBufSuc = (byte[]) msg.obj;

                    String receiveResult = TypeConversion.bytes2HexString(recBufSuc,recBufSuc.length);
                    if (recBufSuc.length == 12) {
                        adc1Value = (recBufSuc[3] & 0xff) | ((recBufSuc[2] << 8) & 0xff00) | ((recBufSuc[1] << 24) >>> 8) | (recBufSuc[0] << 24);
                        adc2Value = (recBufSuc[7] & 0xff) | ((recBufSuc[6] << 8) & 0xff00) | ((recBufSuc[5] << 24) >>> 8) | (recBufSuc[4] << 24);
                        adc3Value = (recBufSuc[11] & 0xff) | ((recBufSuc[10] << 8) & 0xff00) | ((recBufSuc[9] << 24) >>> 8) | (recBufSuc[8] << 24);

                    }
                    tvReceive.setText("接收数据成功，长度" + recBufSuc.length + "--> " + receiveResult);
                    break;

                case BT_CLOSED:
                    if_update_value = false;
                    Log.d(TAG, "系统蓝牙已关闭");
                    break;

                case BT_OPENED:
                    Log.d(TAG, "系统蓝牙已打开");
                    break;
            }

        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mContext = MainActivity.this;

        //动态申请权限（Android 6.0）

        //初始化视图
        initView();
        //初始化监听
        iniListener();
        //初始化数据
        initData();
        //注册广播
        initBLEBroadcastReceiver();
        //初始化权限
        initPermissions();
        //初始化波形图
        createView();


        final Handler handler=new Handler();
//2，然后创建一个Runnable对像
        Runnable runnable=new Runnable(){
            @Override
            public void run() {
                if (if_update_value) {
                    updateADCValues();
                }
                handler.postDelayed(this, 10);
            }
        };

        runnable.run();
    }

    /*
     * 根据列表项个数动态设置ListView高度，解决ScrollView嵌套ListView只显示一行问题
     */
    private void setListViewHeightByItemCount(ListView listView) {
        ListAdapter listAdapter = listView.getAdapter();
        if (listAdapter == null) {
            return;
        }

        int totalHeight = 0;
        for (int i = 0; i < listAdapter.getCount(); i++) {
            View listItem = listAdapter.getView(i, null, listView);
            listItem.measure(0, View.MeasureSpec.makeMeasureSpec(0,
                    View.MeasureSpec.UNSPECIFIED));
            totalHeight += listItem.getMeasuredHeight();
        }

        ViewGroup.LayoutParams params = listView.getLayoutParams();
        params.height = totalHeight + (listView.getDividerHeight() * (listAdapter.getCount() - 1));
        listView.setLayoutParams(params);
    }

    /**
     * 初始化视图
     */
    private void initView() {
        btSearch = findViewById(R.id.bt_search);
        tvCurConState = findViewById(R.id.tv_cur_con_state);
        btConnect = findViewById(R.id.bt_connect);
        btDisconnect = findViewById(R.id.bt_disconnect);
        tvName = findViewById(R.id.tv_name);
        tvAddress = findViewById(R.id.tv_address);
        etSendMsg = findViewById(R.id.et_send_msg);
        btSend = findViewById(R.id.bt_to_send);
        tvSendResult = findViewById(R.id.tv_send_result);
        tvReceive = findViewById(R.id.tv_receive_result);
        llDeviceList = findViewById(R.id.ll_device_list);
        llDataSendReceive  = findViewById(R.id.ll_data_send_receive);
        lvDevices = findViewById(R.id.lv_devices);
        btStart = findViewById(R.id.bt_to_start);
        btStop = findViewById(R.id.bt_to_stop);
        btSuspend = findViewById(R.id.bt_to_suspend);
    }


    /**
     * 初始化监听
     */
    private void iniListener() {
        btSearch.setOnClickListener(this);
        btConnect.setOnClickListener(this);
        btDisconnect.setOnClickListener(this);
        btSend.setOnClickListener(this);
        btStart.setOnClickListener(this);
        btStop.setOnClickListener(this);
        btSuspend.setOnClickListener(this);

        lvDevices.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                BLEDevice bleDevice = (BLEDevice) lvDevicesAdapter.getItem(i);
                BluetoothDevice bluetoothDevice = bleDevice.getBluetoothDevice();
                if(bleManager != null){
                    bleManager.stopDiscoveryDevice();
                }
                Message message = new Message();
                message.what = SELECT_DEVICE;
                message.obj = bluetoothDevice;
                mHandler.sendMessage(message);
            }
        });
    }

    /**
     * 初始化数据
     */
    private void initData() {
        //列表适配器
        lvDevicesAdapter = new LVDevicesAdapter(MainActivity.this);
        lvDevices.setAdapter(lvDevicesAdapter);

        //初始化ble管理器
        bleManager = new BLEManager();
        if(!bleManager.initBle(mContext)) {
            Log.d(TAG, "该设备不支持低功耗蓝牙");
            Toast.makeText(mContext, "该设备不支持低功耗蓝牙", Toast.LENGTH_SHORT).show();
        }else{
            if(!bleManager.isEnable()){
                //去打开蓝牙
                bleManager.openBluetooth(mContext,false);
            }
        }
    }


    /**
     * 注册广播
     */
    private void initBLEBroadcastReceiver() {
        //注册广播接收
        bleBroadcastReceiver = new BLEBroadcastReceiver();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED); //开始扫描
        intentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);//扫描结束
        intentFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);//手机蓝牙状态监听
        registerReceiver(bleBroadcastReceiver,intentFilter);
    }

    /**
     * 初始化权限
     */
    private void initPermissions() {
        //Android 6.0以上动态申请权限
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
            final PermissionRequest permissionRequest = new PermissionRequest();
            permissionRequest.requestRuntimePermission(MainActivity.this, requestPermissionArray, new PermissionListener() {
                @Override
                public void onGranted() {
                    Log.d(TAG,"所有权限已被授予");
                }

                //用户勾选“不再提醒”拒绝权限后，关闭程序再打开程序只进入该方法！
                @Override
                public void onDenied(List<String> deniedPermissions) {
                    deniedPermissionList = deniedPermissions;
                    for (String deniedPermission : deniedPermissionList) {
                        Log.e(TAG,"被拒绝权限：" + deniedPermission);
                    }
                }
            });
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        //注销广播接收
        unregisterReceiver(bleBroadcastReceiver);
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.bt_search:  //搜索蓝牙
                llDataSendReceive.setVisibility(View.GONE);
                llDeviceList.setVisibility(View.VISIBLE);
                searchBtDevice();
                break;

            case R.id.bt_connect: //连接蓝牙
                if(!curConnState) {
                    if(bleManager != null){
                        bleManager.connectBleDevice(mContext,curBluetoothDevice,15000,SERVICE_UUID,READ_UUID,WRITE_UUID,onBleConnectListener);
                    }
                }else{
                    Toast.makeText(this, "当前设备已连接", Toast.LENGTH_SHORT).show();
                }
                break;

            case R.id.bt_disconnect: //断开连接
                if(curConnState) {
                    if(bleManager != null){
                        if_update_value = false;
                        bleManager.disConnectDevice();
                    }
                }else{
                    Toast.makeText(this, "当前设备未连接", Toast.LENGTH_SHORT).show();
                }
                break;

            case R.id.bt_to_send: //发送数据
                if(curConnState){
                    String sendMsg = etSendMsg.getText().toString();
                    if(sendMsg.isEmpty()){
                        Toast.makeText(this, "发送数据为空！", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if(bleManager != null) {
                        bleManager.sendMessage(sendMsg);  //以16进制字符串形式发送数据
                    }
                }else{
                    Toast.makeText(this, "请先连接当前设备", Toast.LENGTH_SHORT).show();
                }
                break;
            case R.id.bt_to_start:  //开始接收数据
                if(curConnState) {
                    if_update_value = true;
                    Log.i(TAG, "onClick: bt_to_start");
                    if(bleManager != null) {
                        bleManager.sendMessage(sendControlBegin);  //以16进制字符串形式发送数据
                    }
                } else {
                    Toast.makeText(this, "请先连接当前设备", Toast.LENGTH_SHORT).show();
                }
                break;
            case R.id.bt_to_stop:   //停止接收数据
                if(curConnState) {
                    if_update_value = false;
                    Log.i(TAG, "onClick: bt_to_stop");
                    if(bleManager != null) {
                        bleManager.sendMessage(sendControlStop);  //以16进制字符串形式发送数据
                    }
                } else {
                    Toast.makeText(this, "请先连接当前设备", Toast.LENGTH_SHORT).show();
                }
                break;
            case R.id.bt_to_suspend:   //暂停接收数据
                if(curConnState) {
                    if_update_value = false;
                    Log.i(TAG, "onClick: bt_to_suspend");
                    if(bleManager != null) {
                        bleManager.sendMessage(sendControlPaused);  //以16进制字符串形式发送数据
                    }
                } else {
                    Toast.makeText(this, "请先连接当前设备", Toast.LENGTH_SHORT).show();
                }
                break;
        }
    }

    //////////////////////////////////  搜索设备  /////////////////////////////////////////////////
    private void searchBtDevice() {
        if(bleManager == null){
            Log.d(TAG, "searchBtDevice()-->bleManager == null");
            return;
        }

        if (bleManager.isDiscovery()) { //当前正在搜索设备...
            bleManager.stopDiscoveryDevice();
        }

        if(lvDevicesAdapter != null){
            lvDevicesAdapter.clear();  //清空列表
        }

        //开始搜索
        bleManager.startDiscoveryDevice(onDeviceSearchListener,5000);
    }

    //扫描结果回调
    private OnDeviceSearchListener onDeviceSearchListener = new OnDeviceSearchListener() {

        @Override
        public void onDeviceFound(BLEDevice bleDevice) {
            Message message = new Message();
            message.what = DISCOVERY_DEVICE;
            message.obj = bleDevice;
            mHandler.sendMessage(message);
        }

        @Override
        public void onDiscoveryOutTime() {
            Message message = new Message();
            message.what = DISCOVERY_OUT_TIME;
            mHandler.sendMessage(message);
        }
    };

    //连接回调
    private OnBleConnectListener onBleConnectListener = new OnBleConnectListener() {
        @Override
        public void onConnecting(BluetoothGatt bluetoothGatt, BluetoothDevice bluetoothDevice) {

        }

        @Override
        public void onConnectSuccess(BluetoothGatt bluetoothGatt, BluetoothDevice bluetoothDevice, int status) {
            //因为服务发现成功之后，才能通讯，所以在成功发现服务的地方表示连接成功
        }

        @Override
        public void onConnectFailure(BluetoothGatt bluetoothGatt, BluetoothDevice bluetoothDevice, String exception, int status) {
            Message message = new Message();
            message.what = CONNECT_FAILURE;
            mHandler.sendMessage(message);
        }

        @Override
        public void onDisConnecting(BluetoothGatt bluetoothGatt, BluetoothDevice bluetoothDevice) {

        }

        @Override
        public void onDisConnectSuccess(BluetoothGatt bluetoothGatt, BluetoothDevice bluetoothDevice, int status) {
            Message message = new Message();
            message.what = DISCONNECT_SUCCESS;
            message.obj = status;
            mHandler.sendMessage(message);
        }

        @Override
        public void onServiceDiscoverySucceed(BluetoothGatt bluetoothGatt, BluetoothDevice bluetoothDevice, int status) {
            //因为服务发现成功之后，才能通讯，所以在成功发现服务的地方表示连接成功
            Message message = new Message();
            message.what = CONNECT_SUCCESS;
            mHandler.sendMessage(message);
        }

        @Override
        public void onServiceDiscoveryFailed(BluetoothGatt bluetoothGatt, BluetoothDevice bluetoothDevice, String failMsg) {
            Message message = new Message();
            message.what = CONNECT_FAILURE;
            mHandler.sendMessage(message);
        }

        @Override
        public void onReceiveMessage(BluetoothGatt bluetoothGatt, BluetoothDevice bluetoothDevice, BluetoothGattCharacteristic characteristic, byte[] msg) {
            Message message = new Message();
            message.what = RECEIVE_SUCCESS;
            message.obj = msg;
            mHandler.sendMessage(message);
        }

        @Override
        public void onReceiveError(String errorMsg) {
            Message message = new Message();
            message.what = RECEIVE_FAILURE;
            mHandler.sendMessage(message);
        }

        @Override
        public void onWriteSuccess(BluetoothGatt bluetoothGatt, BluetoothDevice bluetoothDevice, byte[] msg) {
            Message message = new Message();
            message.what = SEND_SUCCESS;
            message.obj = msg;
            mHandler.sendMessage(message);
        }

        @Override
        public void onWriteFailure(BluetoothGatt bluetoothGatt, BluetoothDevice bluetoothDevice, byte[] msg, String errorMsg) {
            Message message = new Message();
            message.what = SEND_FAILURE;
            message.obj = msg;
            mHandler.sendMessage(message);
        }

        @Override
        public void onReadRssi(BluetoothGatt bluetoothGatt, int Rssi, int status) {

        }

        @Override
        public void onMTUSetSuccess(String successMTU, int newMtu) {

        }

        @Override
        public void onMTUSetFailure(String failMTU) {

        }
    };


    /**
     * 蓝牙广播接收器
     */
    private class BLEBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (TextUtils.equals(action, BluetoothAdapter.ACTION_DISCOVERY_STARTED)) { //开启搜索
                Message message = new Message();
                message.what = START_DISCOVERY;
                mHandler.sendMessage(message);

            } else if (TextUtils.equals(action, BluetoothAdapter.ACTION_DISCOVERY_FINISHED)) {//完成搜素
                Message message = new Message();
                message.what = STOP_DISCOVERY;
                mHandler.sendMessage(message);

            } else if(TextUtils.equals(action,BluetoothAdapter.ACTION_STATE_CHANGED)){   //系统蓝牙状态监听

                int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,0);
                if(state == BluetoothAdapter.STATE_OFF){
                    Message message = new Message();
                    message.what = BT_CLOSED;
                    mHandler.sendMessage(message);

                }else if(state == BluetoothAdapter.STATE_ON){
                    Message message = new Message();
                    message.what = BT_OPENED;
                    mHandler.sendMessage(message);

                }
            }
        }
    }

    public void createView()
    {
//        View v = inflater.inflate(R.layout.activity_main, container, false);

        GraphViewData[] data0 = new GraphViewData[101];
        GraphViewData[] data1 = new GraphViewData[101];
        GraphViewData[] data2 = new GraphViewData[101];

        for (int i = 0; i < 101; i++) { // Restore last data
            data0[i] = new GraphViewData(counter - 100 + i, buffer[0][i]);
            data1[i] = new GraphViewData(counter - 100 + i, buffer[1][i]);
            data2[i] = new GraphViewData(counter - 100 + i, buffer[2][i]);
        }

        adc1 = new GraphViewSeries("adc1", new GraphViewSeriesStyle(Color.RED, 2), data0);
        adc2 = new GraphViewSeries("adc2", new GraphViewSeriesStyle(Color.GREEN, 2), data1);
        adc3 = new GraphViewSeries("adc3", new GraphViewSeriesStyle(Color.BLUE, 2), data2);

//        graphView = new LineGraphView(getActivity(), "");
        graphView = new LineGraphView(mContext, "");
        if (mCheckBox1 != null) {
            if (mCheckBox1.isChecked())
                graphView.addSeries(adc1);
        } else
            graphView.addSeries(adc1);
        if (mCheckBox2 != null) {
            if (mCheckBox2.isChecked())
                graphView.addSeries(adc2);
        } else
            graphView.addSeries(adc2);
        if (mCheckBox3 != null) {
            if (mCheckBox3.isChecked())
                graphView.addSeries(adc3);
        } else
            graphView.addSeries(adc3);

        graphView.setManualYAxisBounds(4096, 0);
        graphView.setViewPort(0, 100);
        graphView.setScrollable(true);
        graphView.setDisableTouch(true);

        graphView.setShowLegend(true);
        graphView.setLegendAlign(LegendAlign.BOTTOM);
        graphView.scrollToEnd();

        LinearLayout layout = (LinearLayout) findViewById(R.id.linegraph);

        GraphViewStyle mGraphViewStyle = new GraphViewStyle();
        mGraphViewStyle.setNumHorizontalLabels(11);
        mGraphViewStyle.setNumVerticalLabels(9);
        mGraphViewStyle.setTextSize(15);
        mGraphViewStyle.setLegendWidth(140);
        mGraphViewStyle.setLegendMarginBottom(30);

        graphView.setGraphViewStyle(mGraphViewStyle);

        layout.addView(graphView);

        mCheckBox1 = (CheckBox) findViewById(R.id.checkBox1);
        mCheckBox1.setOnClickListener(new OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                if (((CheckBox) v).isChecked())
                    graphView.addSeries(adc1);
                else
                    graphView.removeSeries(adc1);
            }
        });
        mCheckBox2 = (CheckBox) findViewById(R.id.checkBox2);
        mCheckBox2.setOnClickListener(new OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                if (((CheckBox) v).isChecked())
                    graphView.addSeries(adc2);
                else
                    graphView.removeSeries(adc2);
            }
        });
        mCheckBox3 = (CheckBox) findViewById(R.id.checkBox3);
        mCheckBox3.setOnClickListener(new OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                if (((CheckBox) v).isChecked())
                    graphView.addSeries(adc3);
                else
                    graphView.removeSeries(adc3);
            }
        });

//        btStart = v.findViewById(R.id.bt_to_start);
//        btStart.setOnClickListener(new OnClickListener()
//        {
//            @Override
//            public void onClick(View v)
//            {
//                if (((ToggleButton) v).isChecked())
//                    mToggleButton.setText("Stop");
//                else
//                    mToggleButton.setText("Start");
//
//                if (MainActivity.mChatService != null) {
//                    if (MainActivity.mChatService.getState() == BluetoothChatService.STATE_CONNECTED && MainActivity.checkTab(ViewPagerAdapter.GRAPH_FRAGMENT)) {
//                        if (((ToggleButton) v).isChecked())
//                            MainActivity.mChatService.write(MainActivity.imuBegin); // Request data
//                        else
//                            MainActivity.mChatService.write(MainActivity.imuStop); // Stop sending data
//                    }
//                }
//            }
//        });

//        mQangle = (EditText) v.findViewById(R.id.editText1);
//        mQbias = (EditText) v.findViewById(R.id.editText2);
//        mRmeasure = (EditText) v.findViewById(R.id.editText3);
//        Button mButton = (Button) v.findViewById(R.id.updateButton);
//        mButton.setOnClickListener(new OnClickListener()
//        {
//            @Override
//            public void onClick(View v)
//            {
//                if (MainActivity.mChatService == null) {
//                    if (D)
//                        Log.e(TAG, "mChatService == null");
//                    return;
//                }
//                if (mQangle.getText() != null)
//                    MainActivity.Qangle = mQangle.getText().toString();
//                if (mQbias.getText() != null)
//                    MainActivity.Qbias = mQbias.getText().toString();
//                if (mRmeasure.getText() != null)
//                    MainActivity.Rmeasure = mRmeasure.getText().toString();
//                MainActivity.mChatService.write(MainActivity.setKalman + MainActivity.Qangle + "," + MainActivity.Qbias + "," + MainActivity.Rmeasure + ";");
//            }
//        });
//
//        if (MainActivity.mChatService != null) {
//            if (MainActivity.mChatService.getState() == BluetoothChatService.STATE_CONNECTED && MainActivity.checkTab(ViewPagerAdapter.GRAPH_FRAGMENT)) {
//                if (mToggleButton.isChecked())
//                    MainActivity.mChatService.write(MainActivity.imuBegin); // Request data
//                else
//                    MainActivity.mChatService.write(MainActivity.imuStop); // Stop sending data
//            }
//        }
//
//        return v;
    }

//    public static void updateKalmanValues()
//    {
//        if (mQangle != null && mQangle.getText() != null) {
//            if (!(mQangle.getText().toString().equals(MainActivity.Qangle)))
//                mQangle.setText(MainActivity.Qangle);
//        }
//        if (mQbias != null && mQbias.getText() != null) {
//            if (!(mQbias.getText().toString().equals(MainActivity.Qbias)))
//                mQbias.setText(MainActivity.Qbias);
//        }
//        if (mRmeasure != null && mRmeasure.getText() != null) {
//            if (!(mRmeasure.getText().toString().equals(MainActivity.Rmeasure)))
//                mRmeasure.setText(MainActivity.Rmeasure);
//        }
//    }

    public static void updateADCValues()
    {

        for (int i = 0; i < 3; i++)
            System.arraycopy(buffer[i], 1, buffer[i], 0, 100);

        try { // In some rare occasions the values can be corrupted
            buffer[0][100] = adc1Value;
            buffer[1][100] = adc2Value;
            buffer[2][100] = adc3Value;
        } catch (NumberFormatException e) {
            if (D)
                Log.e(TAG, "error in input", e);
            return;
        }

        boolean scroll = mCheckBox1.isChecked() || mCheckBox2.isChecked() || mCheckBox3.isChecked();

        counter++;
        adc1.appendData(new GraphViewData(counter, buffer[0][100]), scroll, 101);
//        if (buffer[1][100] <= 360 && buffer[1][100] >= 0) // Don't draw it if it would be larger than y-axis boundaries
        adc2.appendData(new GraphViewData(counter, buffer[1][100]), scroll, 101);
        adc3.appendData(new GraphViewData(counter, buffer[2][100]), scroll, 101);

        if (!scroll)
            graphView.redrawAll();
    }
//
//    @Override
//    public void onResume()
//    {
//        super.onResume();
//        if (mToggleButton.isChecked())
//            mToggleButton.setText("Stop");
//        else
//            mToggleButton.setText("Start");
//
//        if (MainActivity.mChatService != null) {
//            if (MainActivity.mChatService.getState() == BluetoothChatService.STATE_CONNECTED && MainActivity.checkTab(ViewPagerAdapter.GRAPH_FRAGMENT)) {
//                if (mToggleButton.isChecked())
//                    MainActivity.mChatService.write(MainActivity.imuBegin); // Request data
//                else
//                    MainActivity.mChatService.write(MainActivity.imuStop); // Stop sending data
//            }
//        }
//    }

}
