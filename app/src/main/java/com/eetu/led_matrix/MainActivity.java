package com.eetu.led_matrix;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.google.android.material.snackbar.Snackbar;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final UUID My_UUID = UUID.fromString("d6767d6b-5b23-44eb-954a-f15668a1e8e8");
    int REQUEST_ENABLE_BT = 1;

    BluetoothAdapter bluetoothAdapter;
    Intent enableBTIntent = null;
    ColorControl colorControl;

    connectThread ct = null;

    private RecyclerView RV;

    ArrayList<String> device_names;
    ArrayList<String> device_addr;
    ArrayList<BluetoothDevice> device_list;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        BluetoothManager bluetoothManager = (BluetoothManager) this.getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();
        final Button SearchBtn = findViewById(R.id.Search_btn);
        RV = findViewById(R.id.device_list);

        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(this);
        RV.setLayoutManager(layoutManager);

        checkBTPermissions();

        /*else{
            SearchBtn.setText(R.string.src_dev);
        }*/
        SearchBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                makeSearch();
            }
        });
    }

    protected void onActivityResult(int requestCode, int result, Intent data) {
        final Button SearchBtn = findViewById(R.id.Search_btn);
        if (requestCode == REQUEST_ENABLE_BT) {
            if (result == RESULT_CANCELED) {
                enableBTIntent = new Intent((BluetoothAdapter.ACTION_REQUEST_ENABLE));
                startActivity(enableBTIntent);
            } else {
                SearchBtn.setText(R.string.src_dev);
            }
        }
        super.onActivityResult(requestCode, result, data);
    }

    public class MyAdapter extends RecyclerView.Adapter<MyAdapter.MyViewHolder> {
        private ArrayList<String> mData;

        public class MyViewHolder extends RecyclerView.ViewHolder {
            public TextView tv;

            public MyViewHolder(View v) {
                super(v);
                tv = v.findViewById(R.id.dev_names);
            }
        }

        public MyAdapter(ArrayList<String> myData) {
            mData = myData;
        }

        @NonNull
        @Override
        public MyAdapter.MyViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.layout_listitem, parent, false);
            return new MyViewHolder(v);
        }

        @Override
        public void onBindViewHolder(MyViewHolder holder, int position) {
            position = holder.getAbsoluteAdapterPosition();
            holder.tv.setText(mData.get(position));
            holder.tv.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    final int pos = holder.getAbsoluteAdapterPosition();
                    Log.d(TAG, Integer.toString(pos));

                    ct = new connectThread(device_list.get(pos));
                    ct.run();
                }
            });
        }

        @Override
        public int getItemCount() {
            return mData.size();
        }


    }

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                assert device != null;
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT}, 1);
                    return;
                }
                String deviceName = device.getName();
                String deviceHWAddress = device.getAddress();
                if(!device_names.contains(deviceName) && deviceName != null) {
                    device_names.add(deviceName);
                    device_addr.add(deviceHWAddress);
                    RV.setAdapter(new MyAdapter(device_names));
                }
            }
            else if(BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED.equals(action)){
                toHome();
            }
            else if(BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)){
                toHome();
            }
        }
    };

    protected void toHome(){
        setContentView(R.layout.activity_main);
        final Button SearchBtn = findViewById(R.id.Search_btn);
        RV =  findViewById(R.id.device_list);

        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(this);
        RV.setLayoutManager(layoutManager);
        SearchBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                makeSearch();
            }
        });
        checkBTPermissions();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (ct != null) {
            if (!isConnected(ct.BTSS.getRemoteDevice())) {
                toHome();
            }
        }
        checkBTPermissions();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Don't forget to unregister the ACTION_FOUND receiver.
        if(ct != null) {
            ct.cancel();
        }
        unregisterReceiver(receiver);
        if(bluetoothAdapter.getState()==BluetoothAdapter.STATE_ON){
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT}, 1);
                return;
            }
            bluetoothAdapter.cancelDiscovery();
        }
    }

    public static boolean isConnected(BluetoothDevice device) {
        try {
            Method m = device.getClass().getMethod("isConnected", (Class[]) null);
            boolean connected = (boolean) m.invoke(device, (Object[]) null);
            return connected;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public class connectThread extends Thread {

        public BluetoothSocket BTSS;

        public connectThread(BluetoothDevice btDevice) {
            BluetoothSocket tmp = null;
            try {
                if (ActivityCompat.checkSelfPermission(getBaseContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT}, 1);
                    return;
                }
                tmp = btDevice.createRfcommSocketToServiceRecord(btDevice.getUuids()[0].getUuid());
            } catch (IOException e) {
                Log.e(TAG, "Socket's create() method failed", e);
            }
            BTSS = tmp;
        }

        public void run(){
            if (ActivityCompat.checkSelfPermission(getBaseContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT}, 1);
                return;
            }
            bluetoothAdapter.cancelDiscovery();

            try {
                BTSS.connect();
                Snackbar.make(findViewById(R.id.Info), R.string.connected, Snackbar.LENGTH_SHORT).show();
                colorControl = new ColorControl(BTSS);
            } catch (IOException connectException) {
                try {
                    BTSS.close();
                } catch (IOException closeException) {
                    Log.e(TAG, "Could not close the client socket", closeException);
                }
            }
        }
        public void cancel(){
            try {
                BTSS.close();
            } catch (IOException e) {
                Log.e(TAG, "Could not close the client socket", e);
            }

        }

    }

    private void makeSearch(){
        int permission = checkBTPermissions();
        if (permission == 1) {
            Log.d(TAG, "makeSearch: 1");
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT}, 1);
                return;
            }
            Log.d(TAG, "MakeSearch: 2");
            Set<BluetoothDevice> paired = bluetoothAdapter.getBondedDevices();

            if (paired.size() > 0) {
                device_names = new ArrayList<>();
                device_addr = new ArrayList<>();
                device_list = new ArrayList<>();
                for (BluetoothDevice device : paired) {
                    String deviceName = device.getName();
                    String deviceHWAddress = device.getAddress();
                    device_names.add(deviceName);
                    device_addr.add(deviceHWAddress);
                    device_list.add(device);
                    Log.d(TAG, deviceName);
                    if (deviceName.equals("HC-05")) {
                        ct = new connectThread(device);
                        ct.run();
                    }
                }
                RV.setAdapter(new MyAdapter(device_names));
            }
            if (bluetoothAdapter.getState() == BluetoothAdapter.STATE_ON) {
                bluetoothAdapter.cancelDiscovery();
            }
            IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
            filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED);
            filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
            registerReceiver(receiver, filter);
            bluetoothAdapter.startDiscovery();
        }
    }

    // Register the permissions callback, which handles the user's response to the
    // system permissions dialog. Save the return value, an instance of
    // ActivityResultLauncher, as an instance variable.
    private ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    // Permission is granted. Continue the action or workflow in your
                    // app.

                }
            });


    private int checkBTPermissions(){

        /*int permissionCheck = +this.checkSelfPermission("Manifest.permission.ACCESS_COARSE_LOCATION");
        if (permissionCheck != 0) {
            this.requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 1001);
        }*/
        int allPermissions = 1;
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED) {
            // You can use the API that requires the permission.
            Log.d(TAG, "Fine location granted");
        }
        else {
            // You can directly ask for the permission.
            // The registered ActivityResultCallback gets the result of this request.
            requestPermissionLauncher.launch(
                    Manifest.permission.ACCESS_FINE_LOCATION);
            allPermissions = 0;
        }
        if(!bluetoothAdapter.isEnabled()){
            enableBTIntent = new Intent((BluetoothAdapter.ACTION_REQUEST_ENABLE));
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT}, 1);
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                return 0;
            }
            startActivity(enableBTIntent);
            allPermissions = 0;
        }
        return allPermissions;

    }

    public class ColorControl {
        private final OutputStream os;
        byte[] writeBuf;

        String OFF = "0,0,0,";
        int Fr = 7; int Fg = 7; int Fb = 7;
        long prev_time = System.currentTimeMillis();
        double color_levels = 15.0;
        double scaler = 1.0;
        int Fr_ss, Fb_ss, Fg_ss;
        int Fr_end = Fr; int Fb_end = Fb; int Fg_end = Fg;




        @SuppressLint("ClickableViewAccessibility")
        public ColorControl(BluetoothSocket Socket){
            setContentView(R.layout.color_circle);
            Log.d(TAG, "setContentView called for circle");

            OutputStream temp_os = null;
            try {
                temp_os = Socket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "Error occurred when creating input stream", e);
            }
            os = temp_os;

            final SwitchCompat OnOff = findViewById(R.id.switch1);
            OnOff.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                    if (ct != null) {
                        if (!isConnected(ct.BTSS.getRemoteDevice())) {
                            toHome();
                            return;
                        }
                    }

                    if (!b ){
                        writeBuf = OFF.getBytes();
                        OnOff.setText(R.string.OFF);
                    }
                    else{
                        String color = String.format(Locale.getDefault(),"%d,%d,%d,",Fr,Fg,Fb);
                        writeBuf = color.getBytes();
                        OnOff.setText(R.string.ON);
                    }
                    try {
                        assert os != null;
                        os.write(writeBuf);
                    } catch (IOException e) {
                        Log.e(TAG, "Error occurred when sending data");
                    }
                }
            });

            SeekBar bright = findViewById(R.id.bright_bar);
            bright.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int brightness, boolean b) {
                    if (ct != null) {
                        if (!isConnected(ct.BTSS.getRemoteDevice())) {
                            toHome();
                            return;
                        }
                    }
                    scaler = (double) (brightness + 1) /15;
                    Fr = (int) Math.rint(scaler*Fr_ss);
                    if (Fr > color_levels) {
                        Fr = (int) color_levels;

                    }
                    Fb = (int) Math.rint(scaler*Fb_ss);
                    if (Fb > color_levels) {
                        Fb = (int) color_levels;

                    }
                    Fg = (int) Math.rint(scaler*Fg_ss);
                    if (Fg > color_levels) {
                        Fg = (int) color_levels;

                    }


                    String color = String.format(Locale.getDefault(),"%d,%d,%d,",Fr,Fg,Fb);
                    writeBuf = color.getBytes();
                    try {
                        assert os != null;
                        os.write(writeBuf);
                    } catch (IOException e) {
                        Log.e(TAG, "Error occurred when sending data");
                    }

                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                    if (ct != null) {
                        if (!isConnected(ct.BTSS.getRemoteDevice())) {
                            toHome();
                            return;
                        }
                    }
                    if (Fr_end != Fr || Fb_end != Fb || Fg_end != Fg){
                        double scal = 1.0/scaler;
                        Fr_ss = (int) Math.rint(scal*Fr);
                        Fb_ss = (int) Math.rint(scal*Fb);
                        Fg_ss = (int) Math.rint(scal*Fg);
                    }

                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    if (ct != null) {
                        if (!isConnected(ct.BTSS.getRemoteDevice())) {
                            toHome();
                            return;
                        }
                    }
                    Fr_end = Fr;
                    Fb_end = Fb;
                    Fg_end = Fg;
                }
            });

            //MyDrawable colorCircleDrawable = new MyDrawable();
            ImageView colorCircleImageView = findViewById(R.id.colorwheel);
            //colorCircleImageView.setImageResource(findViewById(R.mipmap.rgb_circle_round));

            colorCircleImageView.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View view, MotionEvent motionEvent) {
                    if (ct != null) {
                        if (!isConnected(ct.BTSS.getRemoteDevice())) {
                            toHome();
                            return false;
                        }
                    }
                    if ((motionEvent.getAction() == MotionEvent.ACTION_DOWN || motionEvent.getAction() == MotionEvent.ACTION_MOVE) && prev_time + 150 < System.currentTimeMillis()){
                        if (motionEvent.getPointerCount() == 1){
                            double x = motionEvent.getX();
                            double y = motionEvent.getY();
                            double cx = view.getWidth()/2.0;
                            double cy = view.getHeight()/2.0;
                            double width = view.getWidth();
                            double height = view.getHeight();

                            double dx = 2*(x-cx)/width;
                            double dy = 2*(cy-y)/height;
                            double ax = 1.0/2.0*dx;
                            double by = Math.sqrt(3)/2*dy ;

                            double dist = Math.sqrt(dx*dx+dy*dy);

                            Fr = 0;
                            Fb = 0;
                            Fg = 0;

                            if(dist <= 1.0) {
                                if (dx >= 0.5) {
                                    Fr = (int) color_levels;
                                    Fr = (int) color_levels;

                                }
                                else if (dx < 0.5 && dx >= -0.5){
                                    Fr = (int) Math.rint((0.5 + dx)*color_levels);
                                }
                                else{
                                    Fr = 0;
                                }

                                if (-(ax + by) > 0.5){
                                    Fb = (int) color_levels;
                                }
                                else if (-(ax + by) < 0.5 && -(ax + by) >= -0.5){
                                    Fb = (int) Math.rint((0.5 - (ax + by))*color_levels);
                                }
                                else{
                                    Fb = 0;
                                }

                                if (-ax + by >= 0.5){
                                    Fg = (int) color_levels;
                                }
                                else if (-ax + by < 0.5 && -ax + by >= -0.5){
                                    Fg = (int) Math.rint((0.5 - ax + by)*color_levels);
                                }
                                else{
                                    Fg = 0;
                                }

                                Fr = (int) Math.rint(Fr + color_levels/4.0*Math.exp(-10*dist));
                                Fb = (int) Math.rint(Fb + color_levels/4.0*Math.exp(-10*dist));
                                Fg = (int) Math.rint(Fg + color_levels/4.0*Math.exp(-10*dist));

                                Fr = (int) Math.rint(Fr * scaler);
                                Fb = (int) Math.rint(Fb * scaler);
                                Fg = (int) Math.rint(Fg * scaler);

                                String color = String.format(Locale.getDefault(),"%d,%d,%d,",Fr,Fg,Fb);
                                writeBuf = color.getBytes();
                                try {
                                    assert os != null;
                                    os.write(writeBuf);
                                } catch (IOException e) {
                                    Log.e(TAG, "Error occurred when sending data");
                                }
                                prev_time = System.currentTimeMillis();
                                if (!OnOff.isChecked()){
                                    OnOff.setChecked(true);
                                }
                            }
                        }
                    }
                    return true;
                }

            });

        }

        /*public void ColorControl_old(BluetoothSocket Socket){
            setContentView(R.layout.color_control);
            final BluetoothSocket BTSS = Socket;
            OutputStream temp_os = null;
            try {
                temp_os = BTSS.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "Error occurred when creating input stream", e);
            }
            os = temp_os;
            Button ON_button = (Button) findViewById(R.id.SEND_button);
            ON_button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    EditText inputEditText = findViewById(R.id.Input);
                    writeBuf = inputEditText.getText().toString().getBytes();
                    if(writeBuf.length == 3) {
                        inputEditText.setText("");
                        try {
                            os.write(writeBuf);
                        } catch (IOException e) {
                            Log.e(TAG, "Error occurred when sending data");
                        }
                    }
                    else if (writeBuf.length == 0){
                        writeBuf = ON.getBytes();
                        inputEditText.setText("");
                        try {
                            os.write(writeBuf);
                        } catch (IOException e) {
                            Log.e(TAG, "Error occurred when sending data");
                        }
                    }
                }
            });
            Button OFF_button = (Button) findViewById(R.id.OFF_button);
            OFF_button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    writeBuf = OFF.getBytes();
                    try {
                        os.write(writeBuf);
                    } catch (IOException e) {
                        Log.e(TAG, "Error occurred when sending data");
                    }
                }
            });
        }*/
    }

    public static class MyDrawable extends Drawable{

        @Override
        public void draw(@NonNull Canvas canvas) {
            double cx = getBounds().centerX();
            double cy = getBounds().centerY();
            double width = getBounds().width();
            double height = getBounds().height();
            double onePerWidth = 1.0 / width;
            double onePerHeight = 1.0 / height;
            Log.d(TAG, String.format("cx = %f, cy = %f, width = %f, height = %f",cx,cy,width,height));
            int Fr;
            int Fb;
            int Fg;
            double dx, dy, ax, by, dist;
            Paint paint = new Paint();
            for(double x = 0; x < (width)/2; x++){
                for(double y = 0; y < height/2; y++){
                    int[] F = colorAtPixel(x,y,cx,cy,onePerWidth,onePerHeight);
                    paint.setARGB(255, F[0], F[1], F[2]);
                    canvas.drawPoint((int) (x), (int) (y),paint);

                    //if (y == height - 1) {
                        Log.d(TAG, String.format("Set for x = %f, y = %f", x, y));
                    //}
                }

            }
            /*
            Thread t1 = new Thread(new Runnable() {
                @Override
                public void run() {
                    Paint paint = new Paint();
                    for(double x = 0; x < (width); x++){
                        for(double y = 0; y < height; y++){
                            int[] F = colorAtPixel(x,y,cx,cy,onePerWidth,onePerHeight);
                            paint.setARGB(255, F[0], F[1], F[2]);
                            canvas.drawPoint((int) x, (int) y,paint);
                            if (y == height - 1) {
                                Log.d(TAG, String.format("Set for x = %f, y = %f", x, y));
                            }
                        }

                    }
                }

            });*/
            /*Thread t2 = new Thread(new Runnable() {
                @Override
                public void run() {
                    Paint paint = new Paint();
                    for(double x = Math.round(width/3) + 1; x < Math.round(2*width/3); x++){
                        for(double y = 0; y < height; y++){
                            int[] F = colorAtPixel(x,y,cx,cy,onePerWidth,onePerHeight);
                            paint.setARGB(255, F[0], F[1], F[2]);
                            canvas.drawPoint((int) x, (int) y,paint);
                            if (y == height - 1) {
                                Log.d(TAG, String.format("Set for x = %f, y = %f", x, y));
                            }
                        }

                    }
                }

            });
            Thread t3 = new Thread(new Runnable() {
                @Override
                public void run() {
                    Paint paint = new Paint();
                    for(double x = Math.round(2*width/3) + 1; x < Math.round(width); x++){
                        for(double y = 0; y < height; y++){
                            int[] F = colorAtPixel(x,y,cx,cy,onePerWidth,onePerHeight);
                            paint.setARGB(255, F[0], F[1], F[2]);
                            canvas.drawPoint((int) x, (int) y,paint);
                            if (y == height - 1) {
                                Log.d(TAG, String.format("Set for x = %f, y = %f", x, y));
                            }
                        }

                    }
                }

            });*/
            //t1.start(); //t2.start(); t3.start();
            Log.d(TAG, "Drawable calculated");

        }

        @Override
        public void setAlpha(int i) {

        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {

        }

        @Override
        public int getOpacity() {
            return PixelFormat.OPAQUE;
        }
    }
    private static int[] colorAtPixel(double x, double y, double cx, double cy, double onePerWidth, double onePerHeight){
        int Fr = 0;
        int Fb = 0;
        int Fg = 0;
        double dx = 2*(x-cx)*onePerWidth;
        double dy = 2*(cy-y)*onePerHeight;
        double dist = Math.sqrt(dx*dx+dy*dy);

        if(dist <= 1.0) {
            double ax = 1.0/2.0*dx;
            double by = Math.sqrt(3)/2*dy ;
            if (dx >= 0.5) {
                Fr = 255;
            }
            else if (dx < 0.5 && dx >= -0.5){
                Fr = (int) Math.round((0.5 + dx)*255);
            }
            if (-(ax + by) > 0.5){
                Fb = 255;
            }
            else if (-(ax + by) < 0.5 && -(ax + by) >= -0.5){
                Fb = (int) Math.round((0.5 - (ax + by))*255);
            }
            if (-ax + by >= 0.5){
                Fg = 255;
            }
            else if (-ax + by < 0.5 && -ax + by >= -0.5){
                Fg = (int) Math.round((0.5 - ax + by)*255);
            }
            double fade = 130*Math.exp(-2*dist);
            Fr = (int) Math.round(Fr + fade);
            Fb = (int) Math.round(Fb + fade);
            Fg = (int) Math.round(Fg + fade);

            if (Fr > 255){
                Fr = 255;
            }
            if (Fb > 255){
                Fb = 255;
            }
            if (Fg > 255){
                Fg = 255;
            }
        }
        else{
            Fr = 255;
            Fg = 255;
            Fb = 255;
        }
        return new int[]{Fr, Fg, Fb};
    }
}