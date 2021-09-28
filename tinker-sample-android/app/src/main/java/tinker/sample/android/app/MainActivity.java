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
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.arialyy.annotations.Download;
import com.arialyy.aria.core.Aria;
import com.arialyy.aria.core.task.DownloadTask;
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
import pub.devrel.easypermissions.EasyPermissions;
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

    private String mSavedPatchPath;
    private long downloadTaskId = -1;
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
    private int currentPercent;
    private boolean isdownloadSlience;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate: ");
        super.onCreate(savedInstanceState);
        initWindow();
        init();
        initPermissions();
        initView();
        initDialog();
        initTimer();
        initSdk();
    }

    @Override
    protected void onResume() {
        Log.d(TAG, "onResume: ");
        super.onResume();
        if (mSDK != null) {
            mSDK.setVolume(1);
        }
        Utils.setBackground(false);
    }

    @Override
    protected void onPause() {
        Log.d(TAG, "onPause: ");
        super.onPause();
        Utils.setBackground(true);
    }

    @Override
    protected void onStop() {
        Log.d(TAG, "onStop: ");
        super.onStop();
        mHandler.removeMessages(RESTART_MSG);
        if (mSDK != null) {
            mSDK.setVolume(0);
        }
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy: ");
        super.onDestroy();
        unregisterReceiver(msgReceiver);
        mCloudGameApi.stopGame();
        cancleTimer();
        if (mSDK != null) {
            mSDK.stop();
        }
        Aria.download(this).unRegister();
    }

    @Override
    public void onBackPressed() {
        Log.d(TAG, "onBackPressed: ");
        if (mSDK == null) {
            super.onBackPressed();
        } else {
            mSDK.sendKey(IMobileTcgSdk.KEY_BACK, true);
            mSDK.sendKey(IMobileTcgSdk.KEY_BACK, false);
        }
    }

    private void init() {
        Log.d(TAG, "init: ");
        mCloudGameApi = new CloudGameApi(this);
        mHandler = new MyHandler();
        mSavedPatchPath = getExternalCacheDir() + File.separator + "game.patch";
        Aria.download(this).register();
        initReceiver();
    }

    private void initPermissions() {
        Log.d(TAG, "initPermissions: ");
        EasyPermissions.requestPermissions(this, "下载安装包需要读写权限", 0,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE);
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

    private void cancleTimer() {
        mDownLoadTimerTask.cancel();
        mRestartTimerTask.cancel();
        mTimer.cancel();
        mDownLoadTimerTask = null;
        mRestartTimerTask = null;
        mTimer = null;
    }

    private void initDownLoadTimer() {
        mDownLoadTimerTask = new TimerTask() {
            @Override
            public void run() {
                Log.d(TAG, "run: time is up, start download");
                isStartDownload = true;
                if (!paused && !isDownloadSuccess) {
                    isdownloadSlience = true;
                    startDownload();
                }
            }
        };
        mTimer.schedule(mDownLoadTimerTask, 2 * 60 * 1000);
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
                }
            }

            @Override
            public void onExit() {
                Log.d(TAG, "onExit: ");
                finish();
            }
        });
        settingBarView.setViewShow(false);
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
                            Aria.download(this).load(downloadTaskId).resume();
                            downloadProcess.getButton(DialogInterface.BUTTON_NEUTRAL).setText("暂停");
                            paused = false;
                        } else {
                            Aria.download(this).load(downloadTaskId).stop();
                            downloadProcess.getButton(DialogInterface.BUTTON_NEUTRAL).setText("开始");
                            paused = true;
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
                                mTimer.schedule(mRestartTimerTask, 5 * 60 * 1000 - 10 * 1000);
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
                    Toast.makeText(MainActivity.this, "启动云游戏", Toast.LENGTH_SHORT).show();
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
        Log.d(TAG, "SavedAPKPath: " + mSavedPatchPath);
        if (downloadTaskId == -1) {
            downloadTaskId = Aria.download(this)
                    .load(Constant.patchUrl)
                    .setFilePath(mSavedPatchPath)
                    .create();
            Log.d(TAG, "downloadTaskId: " + downloadTaskId);
        } else {
            Aria.download(this).load(downloadTaskId).resume();
        }
    }

    @Download.onTaskStart
    protected void start(DownloadTask task) {
        Log.d(TAG, "download start: ");
        downloadProcess.setMessage("下载中...");
        if (!isFinishing() && !downloadProcess.isShowing() && !isdownloadSlience) {
            downloadProcess.show();
        }
    }

    @Download.onTaskStop
    protected void stop(DownloadTask task) {
        Log.d(TAG, "download stop: ");
        downloadProcess.show();
    }

    @Download.onTaskResume
    protected void resume (DownloadTask task) {
        Log.d(TAG, "download resume: ");
        downloadProcess.show();
    }

    @Download.onTaskRunning
    protected void running(DownloadTask task) {
        Log.d(TAG, "download running: " + task.getPercent() + ", " + task.getSpeed());
        currentPercent = task.getPercent();
        if (!isFinishing() && downloadProcess.isShowing()) {
            downloadProcess.setProgress(currentPercent);
        }
        if (!isStartDownload) {
            isStartDownload = true;
        }
    }

    @Download.onTaskComplete
    protected void complete(DownloadTask task) {
        Log.d(TAG, "download complete: ");
        isDownloadSuccess = true;
        if (isFinishing()) {
            return;
        }
        downloadProcess.setMessage("下载成功！");
        Toast.makeText(MainActivity.this, "安装包下载已完成！", Toast.LENGTH_SHORT).show();
        downloadProcess.dismiss();
        loadPatch();
    }

    @Download.onTaskFail
    protected void failed(DownloadTask task, Exception e) {
        Log.d(TAG, "failed: ");
        downloadProcess.setMessage("下载失败 " + e.getMessage());
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
            startCloudGame(clientSession, Constant.GAME_ID);
            settingBarView.setViewShow(true);
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

    private void loadPatch() {
        Log.d(TAG, "loadPatch: ");
        if (!isFinishing()) {
            downloadProcess.dismiss();
            loadPatchProcess.show();
            loadPatchProcess.setMessage("游戏本地包正在加载中，大概需要一分钟左右，请稍后！！！");
        }
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
        if (!isFinishing()) {
            downloadProcess.dismiss();
            loadPatchProcess.show();
        }
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
                    if (!isFinishing()) {
                        restartDialog.show();
                    }
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
