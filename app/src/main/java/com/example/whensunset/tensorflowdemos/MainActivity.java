package com.example.whensunset.tensorflowdemos;

import android.Manifest;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.widget.ImageView;

import com.tbruyelle.rxpermissions2.Permission;
import com.tbruyelle.rxpermissions2.RxPermissions;

import org.tensorflow.Operation;
import org.tensorflow.contrib.android.TensorFlowInferenceInterface;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;

import io.reactivex.functions.Consumer;
import io.reactivex.functions.Predicate;

public class MainActivity extends AppCompatActivity {

    private static final String MODEL_FILE = "file:///android_asset/real.pb";
    private static final String INPUT_NODE = "padsss:0";
    private static final String OUTPUT_NODE = "squeezesss:0";
    private TensorFlowInferenceInterface inferenceInterface;
    private float[] floatValues;
    private float[] floatValuess;
    private int[] intValues;
    private int[] intValuess;
    private ImageView mImageView;
    private ImageView mImageView1;
    private int mInWidth = 800;
    private int mInHeight = 600;

    private int mOutWidth = 780;
    private int mOutHeight = 580;

    String CACHE_DIRECTORY;
    String FONT_EDIT_VIEW_IMAGE;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 申请权限
        RxPermissions rxPermissions = new RxPermissions(this);
        rxPermissions.requestEach(
                Manifest.permission.READ_EXTERNAL_STORAGE ,
                Manifest.permission.WRITE_EXTERNAL_STORAGE)
                .filter(new Predicate<Permission>() {
                    @Override
                    public boolean test(Permission permission) throws Exception {
                        return permission.granted;
                    }
                }).subscribe(new Consumer<Permission>() {
                    @Override
                    public void accept(Permission permission) throws Exception {
                        //权限申请成功后 将assets中准备好的图片写到sd卡中，以便后面使用
                        CACHE_DIRECTORY = getCacheDirectory(getBaseContext(), "").getPath();
                        FONT_EDIT_VIEW_IMAGE =  CACHE_DIRECTORY + "/test.jpg";
                        assetToFile("test.jpg", FONT_EDIT_VIEW_IMAGE);

                        //开始处理图片
                        make();
                    }
                });


    }

    private void make() {
        // 从文件中读取需要处理的图片为 bitmap
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        options.outHeight = mInHeight;
        options.outWidth = mInWidth;
        Bitmap bitmap = BitmapFactory.decodeFile(FONT_EDIT_VIEW_IMAGE, options);
        mImageView = (ImageView) findViewById(R.id.image);
        mImageView.setImageBitmap(bitmap);

        // 因为 神经网络需要传入float数组，所以需要将bitmap中所有的像素的真实值传入到 float数组中
        floatValues = new float[mInHeight * mInWidth * 3];
        floatValuess = new float[mOutHeight * mOutWidth * 3];
        intValues = new int[mInHeight * mInWidth];
        intValuess = new int[mOutHeight * mOutWidth];

        // 将 mInHeight * mInWidth 这么大的图片像素传入到intValues中
        bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0, mInWidth, mInHeight);

        // intValues中的每一个值都是一个 将 A R G B四个通道整合之后的值，所以这里需要将四个通道分离，然后将其中的R G B三个通道写入到floatValues中
        // 所以floatValues的大小是 mInHeight * mInWidth * 3
        for (int i = 0; i < intValues.length; ++i) {
            final int val = intValues[i];
            floatValues[i * 3] = ((val >> 16) & 0xFF);
            floatValues[i * 3 + 1] = ((val >> 8) & 0xFF);
            floatValues[i * 3 + 2] = (val & 0xFF);
        }

        // 创建一个TensorFlow在Java下的实例，这里只要将 神经网络文件 放到assets中，然后让Tensorflow自动读取就好了
        inferenceInterface = new TensorFlowInferenceInterface(getAssets(), MODEL_FILE);

        // 获取一下 神经网络的每个节点 然后输出一下名字，有助于确认输入和输出节点
        Iterator<Operation> operationIterator = inferenceInterface.graph().operations();
        while (operationIterator.hasNext()){
            Operation operation = operationIterator.next();
            Log.d("MainActivitysss", operation.name());
        }

        // 将刚刚获取到的floatValues数组当做输入节点输入到Tensorflow实例中，参数依次是 节点名字，数据，dims：表示将floatValues转换成 1 * mInHeight * mInWidth * 3的张量
        inferenceInterface.feed(INPUT_NODE, floatValues, 1, mInHeight, mInWidth, 3);
        // 输入输出节点的名字，并运行，这里是阻塞当前线程的，所以在正式项目中需要在其他线程运行，这里我就简单一点在主线程运行。
        inferenceInterface.run(new String[] {OUTPUT_NODE}, true);
        // 运行完毕之后，取出经过神经网络处理的数据
        inferenceInterface.fetch(OUTPUT_NODE, floatValuess);

        // 将floatValuess 整合成Bitmap中需要的像素值
        for (int i = 0; i < intValuess.length; ++i) {
            intValuess[i] =
                    0xFF000000
                            | (((int) (floatValuess[i * 3])) << 16)
                            | (((int) (floatValuess[i * 3 + 1])) << 8)
                            | ((int) (floatValuess[i * 3 + 2]));
        }

        // 将构建好的像素值 存回Bitmap中
        Bitmap b = Bitmap.createBitmap(mOutWidth,  mOutHeight, Bitmap.Config.ARGB_8888);
        b.setPixels(intValuess, 0, b.getWidth(), 0, 0, b.getWidth(), b.getHeight());
        mImageView1 = (ImageView) findViewById(R.id.image1);
        mImageView1.setImageBitmap(b);
    }

    public void assetToFile(String imageName, String filePath){
        InputStream ims = null;
        FileOutputStream fileOutputStream = null;
        try {
            // get input stream
            ims = getAssets().open(imageName);
            File file = new File(filePath);
            if(file.exists()){
                file.delete();
            }
            fileOutputStream = new FileOutputStream(filePath);
            byte[] buffer = new byte[512];
            int count = 0;
            while((count = ims.read(buffer)) > 0){
                fileOutputStream.write(buffer, 0 ,count);
            }
            fileOutputStream.flush();
        } catch (IOException e) {
            throw new RuntimeException("将asset写入文件失败");
        } finally {
            try {
                if (fileOutputStream != null && ims != null) {
                    fileOutputStream.close();
                    ims.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

    public static File getCacheDirectory(Context context, String type) {
        File appCacheDir = getExternalCacheDirectory(context,type);
        if (appCacheDir == null){
            appCacheDir = getInternalCacheDirectory(context,type);
        }

        if (appCacheDir == null){
            Log.e("getCacheDirectory","getCacheDirectory fail ,the reason is mobile phone unknown exception !");
        }else {
            if (!appCacheDir.exists()&&!appCacheDir.mkdirs()){
                Log.e("getCacheDirectory","getCacheDirectory fail ,the reason is make directory fail !");
            }
        }
        return appCacheDir;
    }

    private static File getExternalCacheDirectory(Context context,String type) {
        File appCacheDir = null;
        if( Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
            if (TextUtils.isEmpty(type)){
                appCacheDir = context.getExternalCacheDir();
            }else {
                appCacheDir = context.getExternalFilesDir(type);
            }

            if (appCacheDir == null){// 有些手机需要通过自定义目录
                appCacheDir = new File(Environment.getExternalStorageDirectory(),"Android/data/"+context.getPackageName()+"/cache/"+type);
            }

            if (appCacheDir == null){
                Log.e("getExternalDirectory","getExternalDirectory fail ,the reason is sdCard unknown exception !");
            }else {
                if (!appCacheDir.exists()&&!appCacheDir.mkdirs()){
                    Log.e("getExternalDirectory","getExternalDirectory fail ,the reason is make directory fail !");
                }
            }
        }else {
            Log.e("getExternalDirectory","getExternalDirectory fail ,the reason is sdCard nonexistence or sdCard mount fail !");
        }
        return appCacheDir;
    }

    private static File getInternalCacheDirectory(Context context,String type) {
        File appCacheDir = null;
        if (TextUtils.isEmpty(type)){
            appCacheDir = context.getCacheDir();// /data/data/app_package_name/cache
        }else {
            appCacheDir = new File(context.getFilesDir(),type);// /data/data/app_package_name/files/type
        }

        if (!appCacheDir.exists()&&!appCacheDir.mkdirs()){
            Log.e("getInternalDirectory","getInternalDirectory fail ,the reason is make directory fail !");
        }
        return appCacheDir;
    }


}
