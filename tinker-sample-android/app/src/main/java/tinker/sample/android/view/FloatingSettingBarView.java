package tinker.sample.android.view;

import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import tinker.sample.android.R;

public class FloatingSettingBarView {

    private static final String TAG = "FloatingSettingBarView";

    private LeftSideSetting leftSide;

    public FloatingSettingBarView(View parent) {
        View leftSideContainer = parent.findViewById(R.id.floating_setting_left);
        leftSide = new LeftSideSetting(leftSideContainer);
    }

    public void setViewShow(boolean show) {
        int visibility = show ? View.VISIBLE : View.GONE;
        leftSide.getRootView().setVisibility(visibility);
    }

    /**
     * 设置事件响应
     */
    public void setEventListener(SettingEventListener settingEventListener) {
        leftSide.setEventListener(settingEventListener);
    }



    public interface SettingEventListener {

        /**
         * 点击互动云游
         */
        void onInteractiveGame();

        void onExit();

    }

    /**
     * 左侧悬浮设置
     */
    private static class LeftSideSetting {

        private final View arrowHolder;
        private final ImageView arrow;
        private final View splitLine;
        private final View splitLine2;
        private final View interactiveGameHolder;
        private final View restartHolder;
        private final ImageView interactiveGameIcon;
        private final ImageView restartIcon;
        private final TextView interactiveGameText;
        private final TextView restartText;
        private final View rightPadding;
        private final View rightPadding2;
        private boolean isExpand = false;
        private View rootView;
        private SettingEventListener eventListener;

        LeftSideSetting(View parent) {
            rootView = parent;
            arrowHolder = parent.findViewById(R.id.floating_setting_left_arrow_holder);
            arrow = parent.findViewById(R.id.floating_setting_left_arrow_icon);
            arrowHolder.setOnClickListener(view -> {
                isExpand = !isExpand;
                setExpand(isExpand);
            });

            splitLine = parent.findViewById(R.id.floating_setting_left_line);
            splitLine2 = parent.findViewById(R.id.floating_setting_left_line_2);

            interactiveGameHolder = parent.findViewById(R.id.floating_setting_interactive_game_holder);
            restartHolder = parent.findViewById(R.id.floating_setting_restart_holder);

            interactiveGameIcon = parent.findViewById(R.id.floating_setting_interactive_game_icon);
            restartIcon = parent.findViewById(R.id.floating_setting_restart_icon);

            interactiveGameText = parent.findViewById(R.id.floating_setting_interactive_game_text);
            restartText = parent.findViewById(R.id.floating_setting_restart_text);

            rightPadding = parent.findViewById(R.id.floating_setting_left_right_padding_2);
            rightPadding2 = parent.findViewById(R.id.floating_setting_left_right_padding_3);

            setExpand(isExpand);
        }

        public void setEventListener(SettingEventListener settingEventListener) {
            this.eventListener = settingEventListener;

            interactiveGameHolder.setOnClickListener(view -> {
                Log.i(TAG, "interactive game onClick");
                if (eventListener != null) {
                    eventListener.onInteractiveGame();
                }
            });

            restartHolder.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Log.i(TAG, "exit onClick");
                    if (eventListener != null) {
                        eventListener.onExit();
                    }
                }
            });
        }

        public View getRootView() {
            return rootView;
        }

        /**
         * 展开和折叠的样式
         */
        private void setExpand(boolean expand) {
            View[] views = {
                    splitLine, splitLine2,
                    interactiveGameHolder, interactiveGameIcon, interactiveGameText,
                    restartHolder, restartIcon, restartText,
                    rightPadding, rightPadding2
            };

            if (expand) {
                // 展开的样式箭头向左
                arrow.setImageResource(R.drawable.icon_left_arrow);
                for (View view : views) {
                    view.setVisibility(View.VISIBLE);
                }
            } else {
                // 折叠的样式箭头向右
                arrow.setImageResource(R.drawable.icon_right_arrow);
                for (View view : views) {
                    view.setVisibility(View.GONE);
                }
            }
        }
    }
}
