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
import android.app.AlertDialog.Builder;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnShowListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.liulishuo.filedownloader.BaseDownloadTask;
import com.liulishuo.filedownloader.FileDownloadListener;
import com.liulishuo.filedownloader.FileDownloader;
import com.tencent.tcgsdk.api.LogLevel;
import com.tencent.tcgsdk.api.ScaleType;
import com.tencent.tcgsdk.api.mobile.Configuration;
import com.tencent.tcgsdk.api.mobile.IMobileTcgSdk;
import com.tencent.tcgsdk.api.mobile.ITcgMobileListener;
import com.tencent.tcgsdk.api.mobile.MobileSurfaceView;
import com.tencent.tcgsdk.api.mobile.MobileTcgSdk;
import com.tencent.tinker.lib.tinker.TinkerInstaller;
import com.tencent.tinker.loader.shareutil.ShareTinkerInternals;

import java.io.File;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
import tinker.sample.android.Constant;
import tinker.sample.android.R;
import tinker.sample.android.api.CloudGameApi;
import tinker.sample.android.api.param.ServerResponse;
import tinker.sample.android.util.Utils;
import tinker.sample.android.view.FloatingSettingBarView;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "Tinker.MainActivity";

    private static final int RESTART_MSG = 666;

    private ProgressDialog downloadProcess;
    private ProgressDialog loadPatchProcess;
    private AlertDialog reminderDialog;
    private AlertDialog restartDialog;
    private FloatingSettingBarView settingBarView;
    private FrameLayout gameContainer;

    // 手游视图
    private MobileSurfaceView mGameView;
    // 云手游SDK调用接口
    private IMobileTcgSdk mSDK;
    // 业务后台交互的API
    private CloudGameApi mCloudGameApi;

    private String mClientSession;
    private String mSavedPatchPath;
    private int downloadTaskId;
    private int currentTimeLeft;
    private boolean paused;
    private volatile boolean isStartDownload;
    private volatile boolean isEnableRestartTimer;
    private boolean isDownloadSuccess;
    private boolean isStartLoadPatch;
    private boolean isLoadSuccess;


    private MsgReceiver msgReceiver;
    private Handler mHandler;
    private Timer mTimer;
    private TimerTask mDownLoadTimerTask;
    private TimerTask mRestartTimerTask;
    private double currentPercent;

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
        unregisterReceiver(msgReceiver);
        mCloudGameApi.stopGame(Constant.GAME_ID);
    }

    private void init() {
        Log.d(TAG, "init: ");
        mCloudGameApi = new CloudGameApi(this);
        mHandler = new MyHandler();
        mSavedPatchPath = getExternalCacheDir() + File.separator + "game.patch";
        Utils.deleteFile(mSavedPatchPath);
        FileDownloader.setup(this);
        initTimer();
        initReceiver();
    }

    private void initReceiver() {
        Log.d(TAG, "initReceiver: ");
        msgReceiver = new MsgReceiver();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("com.tinker.patch.LOADRESULT");
        registerReceiver(msgReceiver, intentFilter);
    }

    private void initTimer() {
        mTimer = new Timer();
        initDownLoadTimer();
        initRestartTimer();
    }

    private void initDownLoadTimer() {
        mDownLoadTimerTask = new TimerTask() {
            @Override
            public void run() {
                Log.d(TAG, "run: time is up, start download");
                isStartDownload = true;
                if (!paused && !isDownloadSuccess) {
                    startDownload();
                }
            }
        };
    }

    private void initRestartTimer() {
        mRestartTimerTask = new TimerTask() {
            @Override
            public void run() {
                Log.d(TAG, "run: time is up, start restart ");
                currentTimeLeft = 10;
                mHandler.sendEmptyMessage(RESTART_MSG);
            }
        };
    }


    private void initWindow() {
        Log.d(TAG, "initWindow: ");
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
    }


    private void initView() {
        Log.d(TAG, "initView: ");
        setContentView(R.layout.activity_main);
        gameContainer = findViewById(R.id.tiyan_game_container);
        mGameView = new MobileSurfaceView(this);
        mGameView.setVisibility(View.GONE);
        gameContainer.addView(mGameView);

        settingBarView = new FloatingSettingBarView(findViewById(R.id.game_setting));
        settingBarView.setEventListener(new FloatingSettingBarView.SettingEventListener() {
            @Override
            public void onInteractiveGame() {
                if (!isStartDownload) {
                    reminderDialog.show();
                } else if (isStartLoadPatch) {
                    reminderDialog.dismiss();
                    downloadProcess.dismiss();
                    loadPatchProcess.show();
                } else {
                    downloadProcess.show();
                    downloadProcess.setProgress((int) Math.ceil(currentPercent));
                }
            }
        });
        settingBarView.setViewShow(false);

        initDialog();

        Button startCloudGameButton = findViewById(R.id.startCloudGame);
        startCloudGameButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mClientSession != null) {
                    startCloudGame(mClientSession, Constant.GAME_ID);
                    mTimer.schedule(mDownLoadTimerTask, 2 * 60 * 1000);
                    mGameView.setVisibility(View.VISIBLE);
                    settingBarView.setViewShow(true);
                    startCloudGameButton.setVisibility(View.GONE);
                }
            }
        });
    }

    private void initDialog() {
        initReminderDialog();
        initDownloadDialog();
        initLoadPatchDialog();
        initRestartDialog();
    }

    private void initReminderDialog() {
        Log.d(TAG, "initReminderDialog: ");
        AlertDialog.Builder builder = new Builder(this);
        builder.setTitle("点击开始下载体验完整游戏！");
        builder.setPositiveButton("开始下载", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                isStartDownload = true;
                startDownload();
                if (!downloadProcess.isShowing()) {
                    downloadProcess.show();
                }
            }
        });

        builder.setNegativeButton("取消", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
            }
        });
        reminderDialog = builder.create();
    }

    private void initDownloadDialog() {
        Log.d(TAG, "initDownloadDialog: ");
        downloadProcess = new ProgressDialog(this);
        downloadProcess.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        downloadProcess.setCancelable(true);
        downloadProcess.setCanceledOnTouchOutside(true);
        downloadProcess.setTitle("更新下载进度");
        downloadProcess.setMax(100);
        downloadProcess.setButton(DialogInterface.BUTTON_POSITIVE, "继续游戏",
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        Log.d(TAG, "onClick: ok");
                    }
                });

        downloadProcess.setButton(DialogInterface.BUTTON_NEUTRAL, "暂停",
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        Log.d(TAG, "onClick: pause");
                        if (paused) {
                            startDownload();
                            downloadProcess.getButton(DialogInterface.BUTTON_NEUTRAL).setText("暂停");
                        } else {
                            FileDownloader.getImpl().pause(downloadTaskId);
                            downloadProcess.getButton(DialogInterface.BUTTON_NEUTRAL).setText("开始");
                        }
                    }
                });
    }

    private void initLoadPatchDialog() {
        Log.d(TAG, "initLoadPatchDialog: ");
        loadPatchProcess = new ProgressDialog(this);
        loadPatchProcess.setCancelable(true);
        loadPatchProcess.setCanceledOnTouchOutside(false);
        loadPatchProcess.setTitle("游戏加载");
        loadPatchProcess.setButton(DialogInterface.BUTTON_POSITIVE, "继续游戏",
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        Log.d(TAG, "onClick: ok");
                        if (isLoadSuccess) {
                            killSelf();
                        }
                    }
                });

        loadPatchProcess.setButton(DialogInterface.BUTTON_NEGATIVE, "取消",
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        Log.d(TAG, "onClick: pause");
                        if (isLoadSuccess) {
                            downloadProcess.dismiss();
                            if (!isEnableRestartTimer) {
                                mTimer.schedule(mRestartTimerTask, 20 * 1000 - 10 * 1000);
                                isEnableRestartTimer = true;
                            }
                        }
                    }
                });
    }

    private void initRestartDialog() {
        AlertDialog.Builder builder = new Builder(this);
        builder.setTitle("游戏自动重启");
        builder.setCancelable(false);
        builder.setPositiveButton("立即重启", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                killSelf();
                mHandler.removeMessages(RESTART_MSG);
            }
        });
        restartDialog = builder.create();
    }

    private void killSelf() {
        Log.d(TAG, "killSelf: ");
        ShareTinkerInternals.killAllOtherProcess(getApplicationContext());
        android.os.Process.killProcess(android.os.Process.myPid());
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
        builder.logLevel(LogLevel.DEBUG);

        // 通过Builder创建SDK接口实例
        mSDK = builder.build();

        // 给游戏视图设置SDK实例
        mGameView.setSDK(mSDK);

        // 让画面和视图一样大,画面可能被拉伸
        mGameView.setScaleType(ScaleType.ASPECT_FILL);
    }

    private void askForRequiredPermissions() {
        if (Build.VERSION.SDK_INT < 23) {
            return;
        }
        if (!hasRequiredPermissions()) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 0);
        }
    }

    private boolean hasRequiredPermissions() {
        if (Build.VERSION.SDK_INT >= 16) {
            final int res = ContextCompat
                    .checkSelfPermission(this.getApplicationContext(), Manifest.permission.READ_EXTERNAL_STORAGE);
            return res == PackageManager.PERMISSION_GRANTED;
        } else {
            // When SDK_INT is below 16, READ_EXTERNAL_STORAGE will also be granted if WRITE_EXTERNAL_STORAGE is granted.
            final int res = ContextCompat
                    .checkSelfPermission(this.getApplicationContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE);
            return res == PackageManager.PERMISSION_GRANTED;
        }
    }

    /**
     * 开始请求业务后台启动游戏，获取服务端server session
     *
     * 注意：客户在接入时需要请求自己的业务后台，获取ServerSession
     * 业务后台实现请参考API：https://cloud.tencent.com/document/product/1162/40740
     *
     * @param clientSession sdk初始化成功后返回的client session
     */
    protected void startCloudGame(String clientSession, String gameId) {
        Log.d(TAG, "startCloudGame: ");
        // 通过业务后台来启动游戏
        mCloudGameApi.startGame(gameId, clientSession, new CloudGameApi.IServerSessionListener() {
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


    private void startDownload() {
        Log.d(TAG, "startDownload: ");
        Log.d(TAG, "SavedPatchPath: " + mSavedPatchPath);
        downloadTaskId = FileDownloader.getImpl().create(Constant.patchUrl)
                .setPath(mSavedPatchPath)
                .setListener(mDownloadLister)
                .start();
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

    private final FileDownloadListener mDownloadLister = new FileDownloadListener() {
        @Override
        protected void pending(BaseDownloadTask task, int soFarBytes, int totalBytes) {
            Log.d(TAG, "pending: " + totalBytes);
            downloadProcess.setMessage("下载中...");
            if (paused && !downloadProcess.isShowing()) {
                downloadProcess.show();
                paused = false;
            }
        }

        @Override
        protected void progress(BaseDownloadTask task, int soFarBytes, int totalBytes) {
            currentPercent = soFarBytes / (double) totalBytes * 100;
            Log.d(TAG, "progress: " + soFarBytes + "/" + totalBytes
                    + " percent " + (int) Math.ceil(currentPercent) + "%");
            downloadProcess.setProgress((int) Math.ceil(currentPercent));
        }

        @Override
        protected void completed(BaseDownloadTask task) {
            Log.d(TAG, "completed: ");
            downloadProcess.setMessage("下载成功！");
            isDownloadSuccess = true;
            loadPatch();
        }

        @Override
        protected void paused(BaseDownloadTask task, int soFarBytes, int totalBytes) {
            Log.d(TAG, "paused: ");
            paused = true;
            downloadProcess.setMessage("暂停");
            if (!downloadProcess.isShowing()) {
                downloadProcess.show();
            }
        }

        @Override
        protected void error(BaseDownloadTask task, Throwable e) {
            Log.d(TAG, "error: ", e);
            downloadProcess.setMessage("下载失败 " + e.getMessage());
        }

        @Override
        protected void warn(BaseDownloadTask task) {
            Log.d(TAG, "warn: ");
        }
    };

    private void loadPatch() {
        Log.d(TAG, "loadPatch: ");
        downloadProcess.dismiss();
        loadPatchProcess.show();
        loadPatchProcess.setMessage("游戏本地包正在加载中，大概需要一分钟左右，请稍后！！！");
        isStartLoadPatch = true;
        TinkerInstaller.onReceiveUpgradePatch(getApplicationContext(), mSavedPatchPath);
    }

    public void onPatchLoaded(boolean isSuccess) {
        if (!isSuccess) {
            loadPatchProcess.setMessage("patch 加载失败！！！请联系开发人员提供新的patch～");
            isStartDownload = false;
            Utils.deleteFile(mSavedPatchPath);
            return;
        }
        loadPatchProcess.setMessage("已为您准备好完整包体，点击\"立即重启\"即可体验");
        isLoadSuccess = true;
        Button positiveButton = loadPatchProcess.getButton(DialogInterface.BUTTON_POSITIVE);
        Button NegativeButton = loadPatchProcess.getButton(DialogInterface.BUTTON_NEGATIVE);
        if (NegativeButton != null && positiveButton != null) {
            positiveButton.setText("立即重启");
            NegativeButton.setText("5min后重启");
        }
        loadPatchProcess.setOnShowListener(new OnShowListener() {
            @Override
            public void onShow(DialogInterface dialogInterface) {
                positiveButton.setText("立即重启");
                NegativeButton.setText("5min后重启");
            }
        });

        downloadProcess.dismiss();
        loadPatchProcess.show();
    }

    public class MsgReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            boolean isSuccess = intent.getBooleanExtra("result", false);
            Log.d(TAG, "onReceive: " + isSuccess);
            onPatchLoaded(isSuccess);
        }
    }

    private class MyHandler extends Handler {

        @Override
        public void handleMessage(Message msg) {
            if (msg.what == RESTART_MSG) {
                restartDialog.setMessage(currentTimeLeft + "s之后将会自动重启!");
                if (!restartDialog.isShowing()) {
                    loadPatchProcess.dismiss();
                    restartDialog.show();
                }
                if (currentTimeLeft > 0) {
                    mHandler.sendEmptyMessageDelayed(RESTART_MSG, 1000);
                    currentTimeLeft--;
                } else {
                    killSelf();
                }
            }
        }
    }
}
