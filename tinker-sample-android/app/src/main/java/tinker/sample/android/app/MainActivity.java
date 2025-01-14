/*
 * Tencent is pleased to support the open source community by making Tinker available.
 *
 * Copyright (C) 2016 THL A29 Limited, a Tencent company. All rights reserved.
 *
 * Licensed under the BSD 3-Clause License (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 *
 * https://opensource.org/licenses/BSD-3-Clause
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */

package tinker.sample.android.app;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.tencent.tcgsdk.api.LogLevel;
import com.tencent.tcgsdk.api.ScaleType;
import com.tencent.tcgsdk.api.mobile.Configuration;
import com.tencent.tcgsdk.api.mobile.IMobileTcgSdk;
import com.tencent.tcgsdk.api.mobile.ITcgMobileListener;
import com.tencent.tcgsdk.api.mobile.MobileSurfaceView;
import com.tencent.tcgsdk.api.mobile.MobileTcgSdk;
import com.tencent.tinker.lib.library.TinkerLoadLibrary;
import com.tencent.tinker.lib.tinker.Tinker;
import com.tencent.tinker.lib.tinker.TinkerInstaller;
import com.tencent.tinker.loader.shareutil.ShareConstants;
import com.tencent.tinker.loader.shareutil.ShareTinkerInternals;

import java.util.Locale;
import tinker.sample.android.Constant;
import tinker.sample.android.R;
import tinker.sample.android.api.CloudGameApi;
import tinker.sample.android.api.param.ServerResponse;
import tinker.sample.android.game.common.Shared;
import tinker.sample.android.game.engine.Engine;
import tinker.sample.android.game.engine.ScreenController;
import tinker.sample.android.game.engine.ScreenController.Screen;
import tinker.sample.android.game.events.EventBus;
import tinker.sample.android.game.events.ui.BackGameEvent;
import tinker.sample.android.game.ui.PopupManager;
import tinker.sample.android.game.utils.FontLoader;
import tinker.sample.android.game.utils.GameUtils;
import tinker.sample.android.util.Utils;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "Tinker.MainActivity";

    private TextView mTvMessage;
    private ImageView mBackgroundImage;

    private Button loadPatchButton;
    private Button loadLibraryButton;
    private Button cleanPatchButton;
    private Button killSelfButton;
    private Button buildInfoButton;
    private Button startCloudGameButton;
    private Button startLocalGameButton;

    // 手游视图
    private MobileSurfaceView mGameView;
    // 云手游SDK调用接口
    private IMobileTcgSdk mSDK;
    // 业务后台交互的API
    private CloudGameApi mCloudGameApi;
    // 从手游SDK获取的客户端session
    private String mClientSession;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate: ");
        super.onCreate(savedInstanceState);
        init();
        initWindow();
        initView();
        initSdk();
        askForRequiredPermissions();
    }

    @Override
    protected void onResume() {
        Log.d(TAG, "onResume: ");
        super.onResume();
        Utils.setBackground(false);

        if (hasRequiredPermissions()) {
            mTvMessage.setVisibility(View.GONE);
        } else {
            mTvMessage.setText(R.string.msg_no_permissions);
            mTvMessage.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
            mTvMessage.setVisibility(View.VISIBLE);
        }
    }

    @Override
    protected void onPause() {
        Log.d(TAG, "onPause: ");
        super.onPause();
        Utils.setBackground(true);
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy: ");
        super.onDestroy();
        Shared.engine.stop();
        mCloudGameApi.stopGame(Constant.MOBILE_GAME_CODE);
    }

    @Override
    public void onBackPressed() {
        if (PopupManager.isShown()) {
            PopupManager.closePopup();
            if (ScreenController.getLastScreen() == Screen.GAME) {
                Shared.eventBus.notify(new BackGameEvent());
            }
        } else if (ScreenController.getInstance().onBack()) {
            super.onBackPressed();
        }
    }

    private void init() {
        Log.d(TAG, "init: ");
        // 云游业务后台api
        mCloudGameApi = new CloudGameApi(this);
        // 本地游戏字体库
        FontLoader.loadFonts(this);
        // 本地游戏相关
        Shared.context = getApplicationContext();
        Shared.engine = Engine.getInstance();
        Shared.eventBus = EventBus.getInstance();
    }

    private void initWindow() {
        Log.d(TAG, "initWindow: ");
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
    }

    private void initView() {
        Log.d(TAG, "initView: ");
        setContentView(R.layout.activity_main);

        mGameView = findViewById(R.id.game_view);
        mTvMessage = findViewById(R.id.tv_message);
        mBackgroundImage = (ImageView) findViewById(R.id.background_image);

        // 加载patch，默认路径/storage/sdcard0/patch_signed_7zip.apk
        loadPatchButton = (Button) findViewById(R.id.loadPatch);
        loadPatchButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                TinkerInstaller.onReceiveUpgradePatch(getApplicationContext(),
                        Environment.getExternalStorageDirectory().getAbsolutePath()
                                + "/patch_signed_7zip.apk");
            }
        });

        // 加载so
        loadLibraryButton = findViewById(R.id.loadLibrary);
        loadLibraryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // #method 1, hack classloader library path
                TinkerLoadLibrary.installNavitveLibraryABI(getApplicationContext(), "armeabi");
                System.loadLibrary("stlport_shared");

                // #method 2, for lib/armeabi, just use TinkerInstaller.loadLibrary
                //TinkerLoadLibrary.loadArmLibrary(getApplicationContext(), "stlport_shared");

                // #method 3, load tinker patch library directly
                //TinkerInstaller.loadLibraryFromTinker(getApplicationContext(), "assets/x86", "stlport_shared");

            }
        });

        // 清除patch
        cleanPatchButton = findViewById(R.id.cleanPatch);
        cleanPatchButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Tinker.with(getApplicationContext()).cleanPatch();
            }
        });

        // 自杀
        killSelfButton = findViewById(R.id.killSelf);
        killSelfButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ShareTinkerInternals.killAllOtherProcess(getApplicationContext());
                android.os.Process.killProcess(android.os.Process.myPid());
            }
        });

        // 展示加载信息
        buildInfoButton = findViewById(R.id.showInfo);
        buildInfoButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showInfo(MainActivity.this);
            }
        });

        // 开启云游戏
        startCloudGameButton = findViewById(R.id.startCloudGame);
        startCloudGameButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d(TAG, "onClick: ");

                if (mClientSession != null) {
                    mGameView.setVisibility(View.VISIBLE);
                    startCloudGameButton.setVisibility(View.GONE);
                    startCloudGame(mClientSession);
                }
            }
        });

        // 开启本地游戏
        startLocalGameButton = findViewById(R.id.startLocalGame);
        startLocalGameButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                mGameView.setVisibility(View.GONE);
                mCloudGameApi.stopGame(Constant.MOBILE_GAME_CODE);
                startLocalGameButton.setVisibility(View.GONE);
                startCloudGameButton.setVisibility(View.GONE);
                startLocalGame();
            }
        });
    }

    private void initSdk() {
        Log.d(TAG, "initSdk: ");
        // 创建Builder
        MobileTcgSdk.Builder builder = new MobileTcgSdk.Builder(
                this,
                Constant.APP_ID,
                mTcgLifeCycleImpl, // 生命周期回调
                mGameView.getViewRenderer());

        // 设置日志级别
        builder.logLevel(LogLevel.VERBOSE);

        // 通过Builder创建SDK接口实例
        mSDK = builder.build();

        // 给游戏视图设置SDK实例
        mGameView.setSDK(mSDK);

        // 让画面和视图一样大,画面可能被拉伸
        mGameView.setScaleType(ScaleType.ASPECT_FILL);
    }

    public boolean showInfo(Context context) {
        Log.d(TAG, "showInfo: ");
        // add more Build Info
        final StringBuilder sb = new StringBuilder();
        Tinker tinker = Tinker.with(getApplicationContext());
        if (tinker.isTinkerLoaded()) {
            sb.append(String.format("[patch is loaded] \n"));
            sb.append(String.format("[buildConfig TINKER_ID] %s \n", BuildInfo.TINKER_ID));
            sb.append(String.format("[buildConfig BASE_TINKER_ID] %s \n", BaseBuildInfo.BASE_TINKER_ID));

            sb.append(String.format("[buildConfig MESSSAGE] %s \n", BuildInfo.MESSAGE));
            sb.append(String.format("[TINKER_ID] %s \n", tinker.getTinkerLoadResultIfPresent().getPackageConfigByName(ShareConstants.TINKER_ID)));
            sb.append(String.format("[packageConfig patchMessage] %s \n", tinker.getTinkerLoadResultIfPresent().getPackageConfigByName("patchMessage")));
            sb.append(String.format("[TINKER_ID Rom Space] %d k \n", tinker.getTinkerRomSpace()));

        } else {
            sb.append(String.format("[patch is not loaded] \n"));
            sb.append(String.format("[buildConfig TINKER_ID] %s \n", BuildInfo.TINKER_ID));
            sb.append(String.format("[buildConfig BASE_TINKER_ID] %s \n", BaseBuildInfo.BASE_TINKER_ID));

            sb.append(String.format("[buildConfig MESSSAGE] %s \n", BuildInfo.MESSAGE));
            sb.append(String.format("[TINKER_ID] %s \n", ShareTinkerInternals.getManifestTinkerID(getApplicationContext())));
        }
        sb.append(String.format("[BaseBuildInfo Message] %s \n", BaseBuildInfo.TEST_MESSAGE));

        final TextView v = new TextView(context);
        v.setText(sb);
        v.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        v.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 10);
        v.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        v.setTextColor(0xFF000000);
        v.setTypeface(Typeface.MONOSPACE);
        final int padding = 16;
        v.setPadding(padding, padding, padding, padding);

        final AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setCancelable(true);
        builder.setView(v);
        final AlertDialog alert = builder.create();
        alert.show();
        return true;
    }

    private void askForRequiredPermissions() {
        if (Build.VERSION.SDK_INT < 23) {
            return;
        }
        if (!hasRequiredPermissions()) {
            ActivityCompat.requestPermissions(this, new String[] {Manifest.permission.READ_EXTERNAL_STORAGE}, 0);
        }
    }

    private boolean hasRequiredPermissions() {
        if (Build.VERSION.SDK_INT >= 16) {
            final int res = ContextCompat.checkSelfPermission(this.getApplicationContext(), Manifest.permission.READ_EXTERNAL_STORAGE);
            return res == PackageManager.PERMISSION_GRANTED;
        } else {
            // When SDK_INT is below 16, READ_EXTERNAL_STORAGE will also be granted if WRITE_EXTERNAL_STORAGE is granted.
            final int res = ContextCompat.checkSelfPermission(this.getApplicationContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE);
            return res == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void setBackgroundImage() {
        Bitmap bitmap = GameUtils.scaleDown(R.drawable.background, GameUtils.screenWidth(), GameUtils.screenHeight());
        bitmap = GameUtils.crop(bitmap, GameUtils.screenHeight(), GameUtils.screenWidth());
        bitmap = GameUtils.downscaleBitmap(bitmap, 2);
        mBackgroundImage.setImageBitmap(bitmap);
    }

    private void startLocalGame() {
        Log.d(TAG, "startLocalGame: ");

        Shared.activity = this;
        Shared.engine.start();
        Shared.engine.setBackgroundImageView(mBackgroundImage);
        // set background
        setBackgroundImage();

        // set menu
        ScreenController.getInstance().openScreen(Screen.MENU);
    }

    /**
     * 开始请求业务后台启动游戏，获取服务端server session
     *
     * 注意：客户在接入时需要请求自己的业务后台，获取ServerSession
     * 业务后台实现请参考API：https://cloud.tencent.com/document/product/1162/40740
     *
     * @param clientSession sdk初始化成功后返回的client session
     */
    protected void startCloudGame(String clientSession) {
        Log.i(TAG, "startCloudGame");
        // 通过业务后台来启动游戏
        mCloudGameApi.startGame(Constant.MOBILE_GAME_CODE, clientSession, new CloudGameApi.IServerSessionListener() {
            @Override
            public void onSuccess(ServerResponse resp) {
                Log.d(TAG, "onSuccess: " + resp.toString());
                if (resp.code == 0) {
                    //　请求成功，从服务端获取到server session，启动游戏
                    mSDK.start(resp.serverSession);
                } else {
                    Toast.makeText(MainActivity.this, resp.toString(), Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailed(String msg) {
                Log.i(TAG, msg);
            }
        });
    }

    /**
     * TcgSdk生命周期回调
     */
    private final ITcgMobileListener mTcgLifeCycleImpl = new ITcgMobileListener() {
        @Override
        public void onConnectionTimeout() {
            // 云游戏连接超时, 用户无法使用, 只能退出
            Log.e(TAG, "onConnectionTimeout");
        }

        @Override
        public void onInitSuccess(String clientSession) {
            // 初始化成功，在此处请求业务后台
            Log.d(TAG, "onInitSuccess: ");
            Toast.makeText(MainActivity.this, "onInitSuccess", Toast.LENGTH_LONG).show();
            mClientSession = clientSession;
        }

        @Override
        public void onInitFailure(int errorCode) {
            // 初始化失败, 用户无法使用, 只能退出
            Log.e(TAG, String.format(Locale.ENGLISH, "onInitFailure:%d", errorCode));
        }

        @Override
        public void onConnectionFailure(int errorCode, String errorMsg) {
            // 云游戏连接失败
            Log.e(TAG, String.format(Locale.ENGLISH, "onConnectionFailure:%d %s", errorCode, errorMsg));
        }

        @Override
        public void onConnectionSuccess() {
            // 云游戏连接成功, 所有SDK的设置必须在这个回调之后进行
            Log.d(TAG, "onConnectionSuccess: ");
        }

        @Override
        public void onDrawFirstFrame() {
            // 游戏画面首帧回调
            Log.d(TAG, "onDrawFirstFrame: ");
        }

        @Override
        public void onConfigurationChanged(Configuration newConfig) {
            // 云端屏幕旋转时, 客户端需要同步旋转屏幕并固定下来
            Log.e(TAG, "onConfigurationChanged:" + newConfig);
            if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            } else {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            }
        }
    };
}
