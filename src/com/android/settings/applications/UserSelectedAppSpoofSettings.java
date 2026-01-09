/*
 * SPDX-FileCopyrightText: 2025 Neoteric OS
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.applications;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.SearchView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.AppBarLayout;
import androidx.core.view.ViewCompat;

import com.android.settings.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class UserSelectedAppSpoofSettings extends Fragment {

    public UserSelectedAppSpoofSettings() {
        super(R.layout.user_selected_app_spoof);
    }

    private static final String SPOOFED_APPS_SETTING = "neoteric_spoofed_apps";

    private ActivityManager mActivityManager;
    private PackageManager mPackageManager;
    private RecyclerView mRecyclerView;
    private AppListAdapter mAdapter;
    private List<PackageInfo> mPackageList;
    private AppBarLayout mAppBarLayout;

    private String mSearchText = "";
    private boolean mShowSystem = false;
    private Menu mOptionsMenu;

    private String[] mProfiles;
    private String[] mProfileLabels;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        requireActivity().setTitle(R.string.user_selectable_app_spoofing_title);
        mAppBarLayout = requireActivity().findViewById(R.id.app_bar);

        Context context = requireContext();
        mActivityManager = context.getSystemService(ActivityManager.class);
        mPackageManager = context.getPackageManager();
        mPackageList = mPackageManager.getInstalledPackages(PackageManager.MATCH_ANY_USER);

        mProfiles = getResources().getStringArray(R.array.neoteric_spoof_profile_values);
        mProfileLabels = getResources().getStringArray(R.array.neoteric_spoof_profile_labels);
    }



    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        mAdapter = new AppListAdapter(requireContext());
        mRecyclerView = view.findViewById(R.id.apps_list);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        mRecyclerView.setAdapter(mAdapter);

        refreshList();
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        mOptionsMenu = menu;
        inflater.inflate(R.menu.user_selected_app_spoof_menu, menu);

        final MenuItem searchMenuItem = menu.findItem(R.id.search);
        final SearchView searchView = (SearchView) searchMenuItem.getActionView();
        searchView.setQueryHint(getString(R.string.search_apps));
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                mSearchText = newText;
                refreshList();
                return true;
            }
        });

        searchMenuItem.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(MenuItem item) {
                if (mAppBarLayout != null) {
                    mAppBarLayout.setExpanded(false, false);
                }
                ViewCompat.setNestedScrollingEnabled(mRecyclerView, false);
                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(MenuItem item) {
                if (mAppBarLayout != null) {
                    mAppBarLayout.setExpanded(false, false);
                }
                ViewCompat.setNestedScrollingEnabled(mRecyclerView, true);
                return true;
            }
        });
        updateOptionsMenu();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.show_system || itemId == R.id.hide_system) {
            mShowSystem = !mShowSystem;
            refreshList();
            updateOptionsMenu();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void updateOptionsMenu() {
        if (mOptionsMenu != null) {
            mOptionsMenu.findItem(R.id.show_system).setVisible(!mShowSystem);
            mOptionsMenu.findItem(R.id.hide_system).setVisible(mShowSystem);
        }
    }

    private void onListUpdate(String packageName, String profile) {
        String stored = Settings.Secure.getString(
            requireContext().getContentResolver(),
            SPOOFED_APPS_SETTING
        );
        if (stored == null) stored = "";

        Map<String, String> map = new HashMap<>();
        if (!stored.isEmpty()) {
            for (String entry : stored.split(",")) {
                String[] parts = entry.split(":");
                if (parts.length == 2) {
                    map.put(parts[0], parts[1]);
                }
            }
        }

        if (profile != null && !profile.equals("None")) {
            map.put(packageName, profile);
        } else {
            map.remove(packageName);
        }

        String newStored = map.entrySet().stream()
                .map(entry -> entry.getKey() + ":" + entry.getValue())
                .collect(Collectors.joining(","));

        Settings.Secure.putString(
            requireContext().getContentResolver(),
            SPOOFED_APPS_SETTING,
            newStored
        );

        try {
            mActivityManager.forceStopPackage(packageName);
        } catch (Exception e) {
            // Ignore
        }
    }

    private Map<String, String> getInitialProfiles() {
        String stored = Settings.Secure.getString(
            requireContext().getContentResolver(),
            SPOOFED_APPS_SETTING
        );
        if (stored == null || stored.isEmpty()) {
            return new HashMap<>();
        }

        Map<String, String> map = new HashMap<>();
        for (String entry : stored.split(",")) {
            String[] pair = entry.split(":");
            if (pair.length == 2) {
                map.put(pair[0], pair[1]);
            }
        }
        return map;
    }

    private void refreshList() {
        List<AppInfo> appInfos = new ArrayList<>();
        for (PackageInfo pi : mPackageList) {
            ApplicationInfo ai = pi.applicationInfo;
            if (ai == null) continue;

            if (!mShowSystem && ai.isSystemApp()) continue;
            
            String label = ai.loadLabel(mPackageManager).toString();
            if (mSearchText.isEmpty() || label.toLowerCase().contains(mSearchText.toLowerCase())) {
                 appInfos.add(new AppInfo(pi.packageName, label, ai.loadIcon(mPackageManager)));
            }
        }

        Collections.sort(appInfos, Comparator.comparing(info -> info.label));
        mAdapter.submitList(appInfos);
    }

    private static class AppInfo {
        final String packageName;
        final String label;
        final Drawable icon;

        AppInfo(String packageName, String label, Drawable icon) {
            this.packageName = packageName;
            this.label = label;
            this.icon = icon;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            AppInfo appInfo = (AppInfo) o;
            return packageName.equals(appInfo.packageName);
        }

        @Override
        public int hashCode() {
            return packageName.hashCode();
        }
    }

    private static final DiffUtil.ItemCallback<AppInfo> ITEM_CALLBACK = new DiffUtil.ItemCallback<AppInfo>() {
        @Override
        public boolean areItemsTheSame(@NonNull AppInfo oldItem, @NonNull AppInfo newItem) {
            return oldItem.packageName.equals(newItem.packageName);
        }

        @Override
        public boolean areContentsTheSame(@NonNull AppInfo oldItem, @NonNull AppInfo newItem) {
            return oldItem.equals(newItem);
        }
    };
    
    private class AppListAdapter extends ListAdapter<AppInfo, AppListViewHolder> {
        private final Map<String, String> mSelectedMap;
        private final Context mContext;

        AppListAdapter(Context context) {
            super(ITEM_CALLBACK);
            mContext = context;
            mSelectedMap = getInitialProfiles();
        }

        @NonNull
        @Override
        public AppListViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new AppListViewHolder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.user_selected_app_spoof_item, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull AppListViewHolder holder, int position) {
            AppInfo info = getItem(position);
            holder.icon.setImageDrawable(info.icon);
            holder.packageName.setText(info.packageName);

            String currentProfile = mSelectedMap.get(info.packageName);
            if (currentProfile != null) {
                holder.label.setText(info.label + " (" + currentProfile + ")");
            } else {
                holder.label.setText(info.label);
            }

            holder.itemView.setOnClickListener(v -> {
                String initialProfile = mSelectedMap.get(info.packageName);
                int selectedIndex = Arrays.asList(mProfiles).indexOf(initialProfile);
                if (selectedIndex == -1) {
                    selectedIndex = Arrays.asList(mProfiles).indexOf("None");
                }

                new AlertDialog.Builder(mContext)
                        .setTitle(R.string.user_select_spoofing_profile_title)
                        .setSingleChoiceItems(mProfileLabels, selectedIndex, (dialog, which) -> {
                            String selectedValue = mProfiles[which];
                            if (!selectedValue.equals("None")) {
                                mSelectedMap.put(info.packageName, selectedValue);
                                onListUpdate(info.packageName, selectedValue);
                            } else {
                                mSelectedMap.remove(info.packageName);
                                onListUpdate(info.packageName, null);
                            }
                            notifyItemChanged(position);
                            dialog.dismiss();
                        })
                        .show();
            });
        }
    }

    private static class AppListViewHolder extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView label;
        final TextView packageName;

        AppListViewHolder(View view) {
            super(view);
            icon = view.findViewById(R.id.icon);
            label = view.findViewById(R.id.label);
            packageName = view.findViewById(R.id.packageName);
        }
    }
}
