/*
 * Copyright (C) 2026 The Android Open Source Project
 *
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

package com.android.packageinstaller;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.Process;
import android.os.UserHandle;
import android.os.UserManager;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.StyleSpan;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import java.text.DateFormat;
import java.util.Date;

/**
 * Builds the block of package details that is displayed underneath the question of the install
 * and the uninstall confirmation dialogs.
 *
 * <p>Only strings from the default (English) and the zh-rCN resources are used, every other
 * locale falls back to English.
 */
public final class InstallDetailsUtil {

    private InstallDetailsUtil() {
    }

    /**
     * Builds the details shown while confirming an installation or an update.
     *
     * @param context used to resolve strings and to query the {@link PackageManager}
     * @param newPackageInfo the package that is about to be installed, may be {@code null} when
     *                       only little is known about it (e.g. a pre-commit session)
     * @param installedPackageInfo the version of the package that is already installed or
     *                            {@code null} when this is a fresh install
     * @param user the user the package is installed for
     * @return the details, never {@code null}
     */
    @NonNull
    public static CharSequence buildInstallDetails(@NonNull Context context,
            @Nullable PackageInfo newPackageInfo,
            @Nullable PackageInfo installedPackageInfo,
            @NonNull UserHandle user) {
        final SpannableStringBuilder sb = new SpannableStringBuilder();
        appendHeader(sb, context);
        appendLine(sb, context, R.string.install_details_user, getUserLabel(context, user));

        if (newPackageInfo == null) {
            return sb;
        }

        appendLine(sb, context, R.string.install_details_package, newPackageInfo.packageName);

        if (installedPackageInfo != null) {
            appendLine(sb, context, R.string.install_details_installed_version, formatVersion(installedPackageInfo));
            appendLine(sb, context, R.string.install_details_new_version, formatVersion(newPackageInfo));
            appendLine(sb, context, R.string.install_details_target_sdk,
                    arrow(formatTargetSdk(installedPackageInfo), formatTargetSdk(newPackageInfo)));
            appendLine(sb, context, R.string.install_details_min_sdk,
                    arrow(formatMinSdk(installedPackageInfo), formatMinSdk(newPackageInfo)));
        } else {
            appendLine(sb, context, R.string.install_details_version, formatVersion(newPackageInfo));
            appendLine(sb, context, R.string.install_details_target_sdk, formatTargetSdk(newPackageInfo));
            appendLine(sb, context, R.string.install_details_min_sdk, formatMinSdk(newPackageInfo));
        }

        appendCommonDetails(sb, context, newPackageInfo);
        return sb;
    }

    /**
     * Builds the details shown while confirming an uninstallation.
     *
     * @param context used to resolve strings and to query the {@link PackageManager}
     * @param packageInfo the package that is about to be removed
     * @param user the user the package is removed from
     * @return the details, never {@code null}
     */
    @NonNull
    public static CharSequence buildUninstallDetails(@NonNull Context context,
            @NonNull PackageInfo packageInfo,
            @NonNull UserHandle user) {
        final SpannableStringBuilder sb = new SpannableStringBuilder();
        appendHeader(sb, context);
        appendLine(sb, context, R.string.install_details_user, getUserLabel(context, user));
        appendLine(sb, context, R.string.install_details_package, packageInfo.packageName);
        appendLine(sb, context, R.string.install_details_version, formatVersion(packageInfo));
        appendLine(sb, context, R.string.install_details_target_sdk, formatTargetSdk(packageInfo));
        appendLine(sb, context, R.string.install_details_min_sdk, formatMinSdk(packageInfo));
        appendCommonDetails(sb, context, packageInfo);
        return sb;
    }

    /**
     * Returns the {@link PackageInfo} of the currently installed version of {@code packageName},
     * or {@code null} when the package is not installed (or only exists as data).
     */
    @Nullable
    public static PackageInfo getInstalledPackageInfo(@NonNull Context context,
            @NonNull String packageName) {
        try {
            final PackageInfo info = context.getPackageManager().getPackageInfo(packageName,
                    PackageManager.PackageInfoFlags.of(
                            PackageManager.MATCH_UNINSTALLED_PACKAGES
                                    | PackageManager.MATCH_ARCHIVED_PACKAGES));
            final ApplicationInfo appInfo = info.applicationInfo;
            if (appInfo != null && !appInfo.isArchived
                    && (appInfo.flags & ApplicationInfo.FLAG_INSTALLED) == 0) {
                return null;
            }
            return info;
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    private static void appendCommonDetails(@NonNull SpannableStringBuilder sb,
            @NonNull Context context, @NonNull PackageInfo packageInfo) {
        appendLine(sb, context, R.string.install_details_uid, formatUid(packageInfo));
    }

    private static void appendHeader(@NonNull SpannableStringBuilder sb,
            @NonNull Context context) {
        final int start = sb.length();
        sb.append(context.getString(R.string.install_details_header));
        sb.setSpan(new StyleSpan(Typeface.BOLD), start, sb.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private static void appendLine(@NonNull SpannableStringBuilder sb, @NonNull Context context,
            @StringRes int labelRes, @Nullable String value) {
        if (TextUtils.isEmpty(value)) {
            return;
        }
        if (sb.length() > 0) {
            sb.append("\n");
        }
        sb.append(context.getString(labelRes)).append(": ").append(value);
    }

    /** {@code "1.2.3 (10203)"} */
    @Nullable
    private static String formatVersion(@Nullable PackageInfo packageInfo) {
        if (packageInfo == null) {
            return null;
        }
        final String versionName = packageInfo.versionName;
        final long versionCode = packageInfo.getLongVersionCode();
        if (TextUtils.isEmpty(versionName)) {
            return versionCode > 0 ? String.valueOf(versionCode) : null;
        }
        return versionCode > 0 ? versionName + " (" + versionCode + ")" : versionName;
    }

    @Nullable
    private static String formatTargetSdk(@Nullable PackageInfo packageInfo) {
        final ApplicationInfo appInfo = packageInfo != null ? packageInfo.applicationInfo : null;
        if (appInfo == null || appInfo.targetSdkVersion <= 0) {
            return null;
        }
        return String.valueOf(appInfo.targetSdkVersion);
    }

    @Nullable
    private static String formatMinSdk(@Nullable PackageInfo packageInfo) {
        final ApplicationInfo appInfo = packageInfo != null ? packageInfo.applicationInfo : null;
        if (appInfo == null || appInfo.minSdkVersion <= 0) {
            return null;
        }
        return String.valueOf(appInfo.minSdkVersion);
    }

    /** The numeric uid the package runs as, or {@code null} when it is not known yet. */
    @Nullable
    private static String formatUid(@Nullable PackageInfo packageInfo) {
        final ApplicationInfo appInfo = packageInfo != null ? packageInfo.applicationInfo : null;
        if (appInfo == null || appInfo.uid <= 0) {
            return null;
        }
        return String.valueOf(appInfo.uid);
    }

    @Nullable
    private static String arrow(@Nullable String oldValue, @Nullable String newValue) {
        if (TextUtils.isEmpty(oldValue)) {
            return newValue;
        }
        if (TextUtils.isEmpty(newValue)) {
            return oldValue;
        }
        return oldValue + " \u2192 " + newValue;
    }

    @Nullable
    private static String formatTime(long timeMillis) {
        if (timeMillis <= 0) {
            return null;
        }
        return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                .format(new Date(timeMillis));
    }

    /** {@code "Owner (0)"}, falling back to the user id when the name is not available. */
    @NonNull
    private static String getUserLabel(@NonNull Context context, @Nullable UserHandle user) {
        final UserHandle targetUser = user != null ? user : Process.myUserHandle();
        String name = null;
        try {
            final UserManager userManager = context.createContextAsUser(targetUser, 0)
                    .getSystemService(UserManager.class);
            if (userManager != null) {
                name = userManager.getUserName();
            }
        } catch (RuntimeException e) {
            // The user may not be readable from here, fall back to the id below.
        }
        if (TextUtils.isEmpty(name)) {
            name = context.getString(R.string.install_details_unknown);
        }
        return name + " (" + targetUser.getIdentifier() + ")";
    }
}
