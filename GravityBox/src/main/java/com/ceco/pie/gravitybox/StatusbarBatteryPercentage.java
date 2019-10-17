/*
 * Copyright (C) 2019 Peter Gregus for GravityBox Project (C3C076@xda)
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ceco.pie.gravitybox;

import de.robv.android.xposed.XSharedPreferences;

import com.ceco.pie.gravitybox.ModStatusBar.ContainerType;
import com.ceco.pie.gravitybox.managers.SysUiStatusBarIconManager;
import com.ceco.pie.gravitybox.managers.SysUiManagers;
import com.ceco.pie.gravitybox.managers.SysUiBatteryInfoManager.BatteryData;
import com.ceco.pie.gravitybox.managers.SysUiBatteryInfoManager.BatteryStatusListener;
import com.ceco.pie.gravitybox.managers.SysUiStatusBarIconManager.ColorInfo;
import com.ceco.pie.gravitybox.managers.SysUiStatusBarIconManager.IconManagerListener;

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.res.Resources;
import android.graphics.Color;
import android.util.TypedValue;
import android.widget.TextView;

public class StatusbarBatteryPercentage implements IconManagerListener, BatteryStatusListener {
    private TextView mPercentage;
    private int mDefaultSizePx;
    private int mColor;
    private String mPercentSign;
    private BatteryData mBatteryData;
    private ValueAnimator mChargeAnim;
    private int mChargingStyle;
    private int mChargingColor;
    private BatteryStyleController mController;

    public static final int CHARGING_STYLE_NONE = 0;
    public static final int CHARGING_STYLE_STATIC = 1;
    public static final int CHARGING_STYLE_ANIMATED = 2;

    public StatusbarBatteryPercentage(TextView view, XSharedPreferences prefs,
            BatteryStyleController controller) {
        mPercentage = view;
        mController = controller;
        mColor = mPercentage.getCurrentTextColor();

        try {
            Resources res = mPercentage.getResources();
            int resId = res.getIdentifier("battery_level_text_size", "dimen",
                    BatteryStyleController.PACKAGE_NAME);
            mDefaultSizePx = res.getDimensionPixelSize(resId);
        } catch (Throwable t) {
            mDefaultSizePx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16,
                    mPercentage.getResources().getDisplayMetrics());
        }

        initPreferences(prefs);

        if (SysUiManagers.IconManager != null) {
            SysUiManagers.IconManager.registerListener(this);
        }
        if (SysUiManagers.BatteryInfoManager != null) {
            SysUiManagers.BatteryInfoManager.registerListener(this);
        }
    }

    public void destroy() {
        if (SysUiManagers.IconManager != null) {
            SysUiManagers.IconManager.unregisterListener(this);
        }
        if (SysUiManagers.BatteryInfoManager != null) {
            SysUiManagers.BatteryInfoManager.unregisterListener(this);
        }
        stopChargingAnimation();
        mBatteryData = null;
        mController = null;
        mPercentage = null;
    }

    private void initPreferences(XSharedPreferences prefs) {
        setTextSize(Integer.valueOf(prefs.getString(
                GravityBoxSettings.PREF_KEY_BATTERY_PERCENT_TEXT_SIZE, "16")));
        setPercentSign(prefs.getString(
                GravityBoxSettings.PREF_KEY_BATTERY_PERCENT_TEXT_STYLE, "%"));
        setChargingStyle(Integer.valueOf(prefs.getString(
                GravityBoxSettings.PREF_KEY_BATTERY_PERCENT_TEXT_CHARGING, "0")));
        setChargingColor(prefs.getInt(
                GravityBoxSettings.PREF_KEY_BATTERY_PERCENT_TEXT_CHARGING_COLOR, Color.GREEN));
    }

    private boolean startChargingAnimation() {
        if (mChargeAnim == null || !mChargeAnim.isRunning()) {
            mChargeAnim = ValueAnimator.ofObject(new ArgbEvaluator(),
                    mColor, mChargingColor);

            mChargeAnim.addUpdateListener(va -> mPercentage.setTextColor((Integer)va.getAnimatedValue()));
            mChargeAnim.addListener(new AnimatorListener() {
                @Override
                public void onAnimationCancel(Animator animation) {
                    mPercentage.setTextColor(mColor);
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    mPercentage.setTextColor(mColor);
                }

                @Override
                public void onAnimationRepeat(Animator animation) { }

                @Override
                public void onAnimationStart(Animator animation) { }
            });

            mChargeAnim.setDuration(1000);
            mChargeAnim.setRepeatMode(ValueAnimator.REVERSE);
            mChargeAnim.setRepeatCount(ValueAnimator.INFINITE);
            mChargeAnim.start();
            return true;
        }
        return false;
    }

    private boolean stopChargingAnimation() {
        if (mChargeAnim != null && mChargeAnim.isRunning()) {
            mChargeAnim.end();
            mChargeAnim.removeAllUpdateListeners();
            mChargeAnim.removeAllListeners();
            mChargeAnim = null;
            return true;
        }
        return false;
    }

    public TextView getView() {
        return mPercentage;
    }

    public void setTextColor(int color) {
        mColor = color;
        stopChargingAnimation();
        update();
    }

    public void setTextSize(int unit, int size) {
        mPercentage.setTextSize(unit, size);
    }

    public void setTextSize(int size) {
        if (size == 0) {
            setTextSize(TypedValue.COMPLEX_UNIT_PX, mDefaultSizePx);
        } else {
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, size);
        }
    }

    public void setPercentSign(String percentSign) {
        mPercentSign = percentSign;
        update();
    }

    public void setChargingStyle(int style) {
        mChargingStyle = style;
        update();
    }

    public void setChargingColor(int color) {
        mChargingColor = color;
        stopChargingAnimation();
        update();
    }

    @SuppressLint("SetTextI18n")
    public void updateText() {
        if (mBatteryData != null) {
            mPercentage.setText(mBatteryData.level + mPercentSign);
        }
    }

    public void update() {
        if (mBatteryData == null) return;

        updateText();

        if (mBatteryData.charging && mBatteryData.level < 100) {
            if (mChargingStyle == CHARGING_STYLE_STATIC) {
                stopChargingAnimation();
                mPercentage.setTextColor(mChargingColor);
            } else if (mChargingStyle == CHARGING_STYLE_ANIMATED) {
                startChargingAnimation();
            } else {
                stopChargingAnimation();
                mPercentage.setTextColor(mColor);
            }
        } else {
            stopChargingAnimation();
            mPercentage.setTextColor(mColor);
        }
    }

    public void setVisibility(int visibility) {
        mPercentage.setVisibility(visibility);
    }

    @Override
    public void onIconManagerStatusChanged(int flags, ColorInfo colorInfo) {
        if (mController.getContainerType() != ContainerType.HEADER &&
                (flags & SysUiStatusBarIconManager.FLAG_ICON_TINT_CHANGED) != 0) {
            setTextColor(colorInfo.iconTint);
        }
        if ((flags & SysUiStatusBarIconManager.FLAG_ICON_ALPHA_CHANGED) != 0) {
            mPercentage.setAlpha(colorInfo.alphaTextAndBattery);
        }
    }

    @Override
    public void onBatteryStatusChanged(BatteryData batteryData) {
        mBatteryData = batteryData;
        update();
    }
}