/*
 * Copyright (c) 2017. Jahir Fiquitiva
 *
 * Licensed under the CreativeCommons Attribution-ShareAlike
 * 4.0 International License. You may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *    http://creativecommons.org/licenses/by-sa/4.0/legalcode
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jahirfiquitiva.libs.frames.activities;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.view.ViewPager;
import android.support.v7.widget.Toolbar;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.github.javiersantos.piracychecker.PiracyChecker;
import com.github.javiersantos.piracychecker.enums.InstallerID;
import com.github.javiersantos.piracychecker.enums.PiracyCheckerCallback;
import com.github.javiersantos.piracychecker.enums.PiracyCheckerError;
import com.github.javiersantos.piracychecker.enums.PirateApp;

import java.util.ArrayList;

import jahirfiquitiva.libs.frames.R;
import jahirfiquitiva.libs.frames.activities.base.ThemedActivity;
import jahirfiquitiva.libs.frames.adapters.CollectionsAdapter;
import jahirfiquitiva.libs.frames.adapters.PagerAdapter;
import jahirfiquitiva.libs.frames.adapters.WallpapersAdapter;
import jahirfiquitiva.libs.frames.callbacks.JSONDownloadCallback;
import jahirfiquitiva.libs.frames.dialogs.FramesDialogs;
import jahirfiquitiva.libs.frames.fragments.CollectionFragment;
import jahirfiquitiva.libs.frames.holders.lists.FullListHolder;
import jahirfiquitiva.libs.frames.models.Collection;
import jahirfiquitiva.libs.frames.models.Wallpaper;
import jahirfiquitiva.libs.frames.utils.ColorUtils;
import jahirfiquitiva.libs.frames.utils.FavoritesUtils;
import jahirfiquitiva.libs.frames.utils.Preferences;
import jahirfiquitiva.libs.frames.utils.ThemeUtils;
import jahirfiquitiva.libs.frames.utils.ToolbarColorizer;
import jahirfiquitiva.libs.frames.utils.Utils;

public class SearchActivity extends ThemedActivity {

    private TabLayout tabs;
    private ViewPager pager;
    private PagerAdapter pagerAdapter;
    private EditText searchField;
    private int lastSelected = 0;
    private String lastSearch = "";
    private String modified = "";

    private boolean check = true;
    private boolean allAma = false;
    private boolean checkLPF = true;
    private boolean checkStores = true;
    private String key = "";

    private MaterialDialog dialog;
    private PiracyChecker checker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        check = getIntent().getBooleanExtra("check", true);
        allAma = getIntent().getBooleanExtra("allAma", false);
        key = getIntent().getStringExtra("key");
        checkLPF = getIntent().getBooleanExtra("checkLPF", true);
        checkStores = getIntent().getBooleanExtra("checkStores", true);

        FavoritesUtils.init(this);

        if ((FullListHolder.get().getCollections() == null) || (FullListHolder.get()
                .getCollections().getList() == null) || (FullListHolder.get().getCollections()
                .getList().size() == 0)) {
            executeJsonTask();
        }

        setContentView(R.layout.activity_search);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        searchField = (EditText) findViewById(R.id.searchField);
        searchField.setHintTextColor(ColorUtils.getMaterialTertiaryColor(!(ColorUtils
                .isLightColor(ThemeUtils.darkOrLight(this, R.color.dark_theme_primary, R.color
                        .light_theme_primary)))));
        searchField.setHighlightColor(ColorUtils.getMaterialTertiaryColor(!(ColorUtils
                .isLightColor(ThemeUtils.darkOrLight(this, R.color.dark_theme_primary, R.color
                        .light_theme_primary)))));
        searchField.setTextColor(ColorUtils.getMaterialPrimaryTextColor(!(ColorUtils
                .isLightColor(ThemeUtils.darkOrLight(this, R.color.dark_theme_primary, R.color
                        .light_theme_primary)))));
        searchField.setFocusable(true);
        searchField.setHint(getResources().getString(R.string.search_x, getTabName(lastSelected)));
        searchField.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
                String search = textView.getText().toString();
                if (search.length() > 0) {
                    search(search, false);
                    lastSearch = search;
                }
                hideKeyboard();
                return false;
            }
        });
        searchField.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean b) {
                if (b) {
                    showKeyboard();
                } else {
                    hideKeyboard();
                }
            }
        });
        if (lastSearch.length() > 0) {
            searchField.setText(lastSearch);
        } else {
            searchField.requestFocus();
            forceShowKeyboard();
        }

        ToolbarColorizer.colorizeToolbar(toolbar, ColorUtils.getMaterialPrimaryTextColor(!
                (ColorUtils.isLightColor(ThemeUtils.darkOrLight(this, R.color.dark_theme_primary,
                        R.color.light_theme_primary)))));
        ToolbarColorizer.tintStatusBar(this);

        tabs = (TabLayout) findViewById(R.id.tabs);
        pager = (ViewPager) findViewById(R.id.pager);

        setupTabsAndPager();
    }

    @Override
    protected void onResume() {
        super.onResume();
        runLicenseChecker(check, key, allAma, checkLPF, checkStores);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        FavoritesUtils.destroy(this);
    }

    @SuppressWarnings("ConstantConditions")
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.search, menu);
        return true;
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putInt("lastSelected", lastSelected);
        outState.putString("lastSearch", lastSearch);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        lastSelected = savedInstanceState.getInt("lastSelected", 0);
        lastSearch = savedInstanceState.getString("lastSearch");
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
        } else if (item.getItemId() == R.id.close) {
            search(null, true);
            searchField.setText("");
            searchField.requestFocus();
            showKeyboard();
        }
        return true;
    }

    @Override
    public void onBackPressed() {
        search(null, true);
        hideKeyboard();
        forceHideKeyboard();
        finishAndSendData();
    }

    @Override
    public void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (data != null) {
            String modifiedItems = data.getStringExtra("modified");
            if (modifiedItems != null) {
                if (modifiedItems.length() > 0) {
                    String[] mis = modifiedItems.split(",");
                    if (modified.length() > 0) modified += ",";
                    for (int i = 0; i < mis.length; i++) {
                        modified += mis[i];
                        if ((mis.length > 1) && (i < (mis.length - 1))) {
                            modified += ",";
                        }
                    }
                    if (lastSelected == 0 && pager != null) {
                        if (pager.getAdapter() != null && pager.getAdapter().getCount() > 0) {
                            try {
                                if (((PagerAdapter) pager.getAdapter()).getFragmentAtPosition(0)
                                        != null) {
                                    if (((CollectionFragment) ((PagerAdapter) pager
                                            .getAdapter())
                                            .getFragmentAtPosition(0)).getRVAdapter() != null)
                                        if (((CollectionFragment) ((PagerAdapter) pager
                                                .getAdapter()).getFragmentAtPosition(0))
                                                .getRVAdapter()
                                                instanceof WallpapersAdapter) {
                                            ((WallpapersAdapter) ((CollectionFragment) (
                                                    (PagerAdapter) pager.getAdapter())
                                                    .getFragmentAtPosition(0)).getRVAdapter())
                                                    .updateItems(modifiedItems);
                                        }
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    protected JSONDownloadCallback getCallback() {
        return new JSONDownloadCallback() {
            @Override
            public void onSuccess(ArrayList<Collection> collections) {
                FullListHolder.get().getCollections().createList(collections);
            }
        };
    }

    private void onLicenseSuccess() {
        if ((FullListHolder.get().getCollections() == null) ||
                (FullListHolder.get().getCollections().getList() == null) ||
                (FullListHolder.get().getCollections().getList().size() == 0)) {
            executeJsonTask();
        }
    }

    public void setupTabsAndPager() {
        if (tabs == null) return;
        tabs.removeAllTabs();
        tabs.addTab(tabs.newTab().setText(R.string.wallpapers));
        tabs.addTab(tabs.newTab().setText(R.string.collections));
        tabs.setVisibility(View.VISIBLE);
        if (pager == null) return;
        pager.setVisibility(View.VISIBLE);
        tabs.addOnTabSelectedListener(new TabLayout.ViewPagerOnTabSelectedListener(pager));
        pager.addOnPageChangeListener(new TabLayout.TabLayoutOnPageChangeListener(tabs) {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                lastSelected = position;
                if (lastSearch.length() > 0)
                    search(lastSearch, false);
                searchField.setHint(getResources().getString(R.string.search_x,
                        getTabName(lastSelected)));
                invalidateOptionsMenu();
            }
        });
        setupPagerAdapter();
    }

    private void setupPagerAdapter() {
        pagerAdapter = new PagerAdapter(getSupportFragmentManager(), false, true);
        if (pager != null) {
            pager.setAdapter(pagerAdapter);
            pager.setCurrentItem(lastSelected, true);
        }
    }

    public PagerAdapter getPagerAdapter() {
        return pagerAdapter;
    }

    public int getCurrentFragmentPosition() {
        return lastSelected;
    }

    private void finishAndSendData() {
        Intent intent = new Intent();
        StringBuilder s = new StringBuilder("");
        if (getPagerAdapter() != null) {
            for (Fragment fragment : getPagerAdapter().getFragments()) {
                if (fragment instanceof CollectionFragment) {
                    if (((CollectionFragment) fragment).getRVAdapter() instanceof
                            WallpapersAdapter) {
                        ArrayList<String> list = ((WallpapersAdapter) ((CollectionFragment)
                                fragment).getRVAdapter()).getModifiedWallpapers();
                        for (int i = 0; i < list.size(); i++) {
                            s.append(list.get(i));
                            if (list.size() > 1 && i < (list.size() - 1)) {
                                s.append(",");
                            }
                        }
                    }
                }
            }
        }
        intent.putExtra("modified", modified + s.toString());
        setResult(13, intent);
        finish();
    }

    private void search(String s, boolean reset) {
        if (getCurrentFragmentPosition() == 0) {
            try {
                if (reset) {
                    if (getPagerAdapter() != null) {
                        ((WallpapersAdapter) ((CollectionFragment) getPagerAdapter()
                                .getFragmentAtPosition(0)).getRVAdapter()).changeWallpapers(null);
                    }
                } else {
                    if (s != null && s.length() > 0) {
                        ArrayList<Wallpaper> results = new ArrayList<>();
                        for (Collection c : FullListHolder.get().getCollections().getList()) {
                            for (Wallpaper w : c.getWallpapers()) {
                                if ((w.getName().toLowerCase().contains(s.toLowerCase())) ||
                                        (w.getAuthor().toLowerCase().contains(s.toLowerCase()))) {
                                    if (!(results.contains(w)))
                                        results.add(w);
                                }
                            }
                        }
                        if (getPagerAdapter() != null) {
                            ((WallpapersAdapter) ((CollectionFragment) getPagerAdapter()
                                    .getFragmentAtPosition(0)).getRVAdapter()).changeWallpapers
                                    (results);
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        } else if (getCurrentFragmentPosition() == 1) {
            try {
                if (reset) {
                    if (getPagerAdapter() != null) {
                        ((CollectionsAdapter) ((CollectionFragment) getPagerAdapter()
                                .getFragmentAtPosition(1)).getRVAdapter()).changeCollections(null);
                    }
                } else {
                    if (s != null && s.length() > 0) {
                        ArrayList<Collection> results = new ArrayList<>();
                        for (Collection c : FullListHolder.get().getCollections().getList()) {
                            if (c.getName().toLowerCase().contains(s.toLowerCase())) {
                                if (!(results.contains(c)))
                                    results.add(c);
                            }
                        }
                        if (getPagerAdapter() != null) {
                            ((CollectionsAdapter) ((CollectionFragment) getPagerAdapter()
                                    .getFragmentAtPosition(1)).getRVAdapter())
                                    .changeCollections(results);
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }
    }

    private String getTabName(int position) {
        if (position == 0) {
            return getResources().getString(R.string.wallpapers).toLowerCase();
        } else if (position == 1) {
            return getResources().getString(R.string.collections).toLowerCase();
        } else {
            return "";
        }
    }

    private void forceShowKeyboard() {
        InputMethodManager manager = (InputMethodManager) getSystemService(
                Context.INPUT_METHOD_SERVICE);
        manager.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
    }

    private void showKeyboard() {
        InputMethodManager manager = (InputMethodManager) getSystemService(
                Context.INPUT_METHOD_SERVICE);
        manager.showSoftInput(searchField, InputMethodManager.SHOW_IMPLICIT);
    }

    private void hideKeyboard() {
        InputMethodManager manager = (InputMethodManager) getSystemService(
                Context.INPUT_METHOD_SERVICE);
        manager.hideSoftInputFromWindow(searchField.getWindowToken(), 0);
    }

    private void forceHideKeyboard() {
        InputMethodManager manager = (InputMethodManager) getSystemService(
                Context.INPUT_METHOD_SERVICE);
        try {
            View focus = getWindow().getCurrentFocus();
            if (focus != null)
                manager.hideSoftInputFromWindow(focus.getWindowToken(), 0);
        } catch (Exception ignored) {
        }
    }

    private void runLicenseChecker(boolean ch, String lic, boolean allAma,
                                   boolean checkLPF, boolean checkStores) {
        Preferences mPrefs = new Preferences(this);
        if (ch) {
            if (Utils.isNewVersion(this) || (!(mPrefs.isDashboardWorking()))) {
                if (Utils.isConnected(this)) {
                    try {
                        checkLicense(lic, allAma, checkLPF, checkStores);
                    } catch (Exception e) {
                        showSimpleLicenseCheckErrorDialog();
                    }
                } else {
                    showSimpleLicenseCheckErrorDialog();
                }
            }
        } else {
            mPrefs.setDashboardWorking(true);
            onLicenseSuccess();
        }
    }

    private void checkLicense(final String lic, final boolean allAma, final boolean checkLPF,
                              final boolean checkStores) {
        destroyChecker();
        checker = new PiracyChecker(this);
        checker.enableInstallerId(InstallerID.GOOGLE_PLAY);
        if (lic != null && lic.length() > 50) checker.enableGooglePlayLicensing(lic);
        if (allAma) checker.enableInstallerId(InstallerID.AMAZON_APP_STORE);
        if (checkLPF) checker.enableUnauthorizedAppsCheck();
        if (checkStores) checker.enableStoresCheck();
        checker.enableEmulatorCheck(false)
                .enableDebugCheck()
                .callback(new PiracyCheckerCallback() {
                    @Override
                    public void allow() {
                        showLicensedDialog();
                    }

                    @Override
                    public void dontAllow(@NonNull PiracyCheckerError piracyCheckerError,
                                          @Nullable final PirateApp pirateApp) {
                        showNotLicensedDialog(pirateApp);
                    }

                    @Override
                    public void onError(@NonNull PiracyCheckerError error) {
                        showLicenseCheckErrorDialog(lic, allAma, checkLPF, checkStores);
                    }
                });
        checker.start();
    }

    private void showLicensedDialog() {
        clearDialog();
        final Preferences mPrefs = new Preferences(this);
        dialog = FramesDialogs.buildLicenseSuccessDialog(this,
                new MaterialDialog.SingleButtonCallback() {
                    @Override
                    public void onClick(@NonNull MaterialDialog materialDialog,
                                        @NonNull DialogAction dialogAction) {
                        mPrefs.setDashboardWorking(true);
                        onLicenseSuccess();
                    }
                }, new MaterialDialog.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        mPrefs.setDashboardWorking(true);
                        onLicenseSuccess();
                    }
                }, new MaterialDialog.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        mPrefs.setDashboardWorking(true);
                        onLicenseSuccess();
                    }
                });
        dialog.show();
    }

    private void showLicenseCheckErrorDialog(final String lic, final boolean allAma,
                                             final boolean checkLPF, final boolean checkStores) {
        clearDialog();
        dialog = FramesDialogs.buildLicenseErrorDialog(this,
                new MaterialDialog.SingleButtonCallback() {
                    @Override
                    public void onClick(@NonNull MaterialDialog dialog,
                                        @NonNull DialogAction which) {
                        dialog.dismiss();
                        checkLicense(lic, allAma, checkLPF, checkStores);
                    }
                }, new MaterialDialog.SingleButtonCallback() {
                    @Override
                    public void onClick(@NonNull MaterialDialog dialog,
                                        @NonNull DialogAction which) {
                        finish();
                    }
                }, new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialogInterface) {
                        finish();
                    }
                }, new MaterialDialog.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialogInterface) {
                        finish();
                    }
                });
        dialog.show();
    }

    private void showSimpleLicenseCheckErrorDialog() {
        clearDialog();
        dialog = FramesDialogs.buildLicenseErrorDialog(this, null,
                new MaterialDialog.SingleButtonCallback() {
                    @Override
                    public void onClick(@NonNull MaterialDialog dialog, @NonNull
                            DialogAction which) {
                        finish();
                    }
                }, new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialogInterface) {
                        finish();
                    }
                }, new MaterialDialog.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialogInterface) {
                        finish();
                    }
                });
        dialog.show();
    }

    private void showNotLicensedDialog(PirateApp app) {
        Preferences mPrefs = new Preferences(this);
        mPrefs.setDashboardWorking(false);
        clearDialog();
        dialog = FramesDialogs.buildShallNotPassDialog(this, app,
                new MaterialDialog.SingleButtonCallback() {
                    @Override
                    public void onClick(@NonNull MaterialDialog materialDialog, @NonNull
                            DialogAction
                            dialogAction) {
                        startActivity(new Intent(Intent.ACTION_VIEW,
                                Uri.parse("https://play.google.com/store/apps/details?id="
                                        + getPackageName())));
                    }
                }, new MaterialDialog.SingleButtonCallback() {

                    @Override
                    public void onClick(@NonNull MaterialDialog materialDialog, @NonNull
                            DialogAction
                            dialogAction) {
                        finish();
                    }
                }, new MaterialDialog.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        finish();
                    }
                }, new MaterialDialog.OnCancelListener() {

                    @Override
                    public void onCancel(DialogInterface dialog) {
                        finish();
                    }
                });
        dialog.show();
    }

    private void clearDialog() {
        if (dialog != null) {
            dialog.dismiss();
            dialog = null;
        }
    }

    private void destroyChecker() {
        if (checker != null) {
            checker.destroy();
            checker = null;
        }
    }

}