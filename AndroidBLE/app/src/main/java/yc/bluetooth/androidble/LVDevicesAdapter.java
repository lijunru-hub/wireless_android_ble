package yc.bluetooth.androidble;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * 搜索到的设备列表适配器
 */
public class LVDevicesAdapter extends BaseAdapter {

    private Context context;
    private List<BLEDevice> list;

    public LVDevicesAdapter(Context context) {
        this.context = context;
        list = new ArrayList<>();
    }

    @Override
    public int getCount() {
        return list == null ?  0 : list.size();
    }

    @Override
    public Object getItem(int i) {
        if(list == null){
            return null;
        }
        return list.get(i);
    }

    @Override
    public long getItemId(int i) {
        return i;
    }

    @SuppressLint("SetTextI18n")
    @Override
    public View getView(int i, View view, ViewGroup viewGroup) {
        DeviceViewHolder viewHolder;
        if(view == null){
            view = LayoutInflater.from(context).inflate(R.layout.layout_lv_devices_item,null);
            viewHolder = new DeviceViewHolder();
            viewHolder.tvDeviceName = view.findViewById(R.id.tv_device_name);
            viewHolder.tvDeviceAddress = view.findViewById(R.id.tv_device_address);
            viewHolder.tvDeviceRSSI = view.findViewById(R.id.tv_device_rssi);
            view.setTag(viewHolder);
        }else{
            viewHolder = (DeviceViewHolder) view.getTag();
        }

        if(list.get(i).getBluetoothDevice().getName() == null){
            viewHolder.tvDeviceName.setText("NULL");
        }else{
            viewHolder.tvDeviceName.setText(list.get(i).getBluetoothDevice().getName());
        }

        viewHolder.tvDeviceAddress.setText(list.get(i).getBluetoothDevice().getAddress());
        viewHolder.tvDeviceRSSI.setText("RSSI：" + list.get(i).getRSSI());

        return view;
    }

    /**
     * 初始化所有设备列表
     * @param bluetoothDevices
     */
    public void addAllDevice(List<BLEDevice> bluetoothDevices){
        if(list != null){
            list.clear();
            list.addAll(bluetoothDevices);
            notifyDataSetChanged();
        }

    }

    /**
     * 添加列表子项
     * @param bleDevice
     */
    public void addDevice(BLEDevice bleDevice){
        if(list == null){
            return;
        }
        if(!list.contains(bleDevice)){
            list.add(bleDevice);
        }
        notifyDataSetChanged();   //刷新
    }

    /**
     * 清空列表
     */
    public void clear(){
        if(list != null){
            list.clear();
        }
        notifyDataSetChanged(); //刷新
    }

    class DeviceViewHolder {

        TextView tvDeviceName;
        TextView tvDeviceAddress;
        TextView tvDeviceRSSI;
    }

}
