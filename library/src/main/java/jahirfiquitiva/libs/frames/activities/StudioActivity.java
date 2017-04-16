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
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

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
import jahirfiquitiva.libs.frames.adapters.PagerAdapter;
import jahirfiquitiva.libs.frames.adapters.WallpapersAdapter;
import jahirfiquitiva.libs.frames.callbacks.JSONDownloadCallback;
import jahirfiquitiva.libs.frames.dialogs.FramesDialogs;
import jahirfiquitiva.libs.frames.fragments.CollectionFragment;
import jahirfiquitiva.libs.frames.holders.lists.FullListHolder;
import jahirfiquitiva.libs.frames.models.Collection;
import jahirfiquitiva.libs.frames.utils.ColorUtils;
import jahirfiquitiva.libs.frames.utils.FavoritesUtils;
import jahirfiquitiva.libs.frames.utils.Preferences;
import jahirfiquitiva.libs.frames.utils.ThemeUtils;
import jahirfiquitiva.libs.frames.utils.ToolbarColorizer;
import jahirfiquitiva.libs.frames.utils.Utils;

public class StudioActivity extends ThemedActivity {

    private TabLayout tabs;
    private ViewPager pager;
    private PagerAdapter pagerAdapter;
    private boolean hasFeaturedWallpapers = false;
    private int lastSelected = 0;

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

        setContentView(R.layout.activity_studio);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

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
        if (getPagerAdapter() != null) {
            if (getPagerAdapter().getFragments() != null) {
                for (Fragment fragment : getPagerAdapter().getFragments()) {
                    if (fragment instanceof CollectionFragment) {
                        ((CollectionFragment) fragment).setupRecyclerView();
                    }
                }
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        FavoritesUtils.destroy(this);
        clearDialog();
        destroyChecker();
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

    private void onLicenseSuccess() {
        if (((FullListHolder.get().getCollections() == null) ||
                (FullListHolder.get().getCollections().getList() == null) ||
                (FullListHolder.get().getCollections().getList().size() == 0))) {
            executeJsonTask();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        String installer = getPackageManager().getInstallerPackageName(getPackageName());
        if (installer != null) {
            if ((installer.matches("com.google.android.feedback") ||
                    installer.matches("com.android.vending"))) {
                if (((getResources().getStringArray(R.array.google_donations_catalog).length <=
                        0) ||
                        (getResources().getStringArray(R.array.google_donations_items).length <=
                                0))) {
                    menu.removeItem(R.id.donate);
                }
            } else if ((getResources().getString(R.string.paypal_email).length() <= 0) ||
                    (getResources().getString(R.string.paypal_currency_code).length() <= 0)) {
                menu.removeItem(R.id.donate);
            }
        } else {
            menu.removeItem(R.id.donate);
        }
        if (getResources().getBoolean(R.bool.show_popup_icons))
            ToolbarColorizer.makeMenuIconsVisible(menu);
        ToolbarColorizer.tintMenu(menu, ColorUtils.getMaterialPrimaryTextColor(
                !(ColorUtils.isLightColor(
                        ThemeUtils.darkOrLight(this, R.color.dark_theme_primary,
                                R.color.light_theme_primary)))));
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        super.onOptionsItemSelected(item);
        int i = item.getItemId();
        if (i == R.id.search) {
            Intent search = new Intent(this, SearchActivity.class);
            search.putExtra("key", key);
            search.putExtra("check", check);
            search.putExtra("allAma", allAma);
            search.putExtra("checkLPF", checkLPF);
            search.putExtra("checkStores", checkStores);
            startActivityForResult(search, 13);
        } else if (i == R.id.favs) {
            Intent favs = new Intent(this, FavoritesActivity.class);
            favs.putExtra("key", key);
            favs.putExtra("check", check);
            favs.putExtra("allAma", allAma);
            favs.putExtra("checkLPF", checkLPF);
            favs.putExtra("checkStores", checkStores);
            startActivityForResult(favs, 14);
        } else if (i == R.id.refresh) {
            executeJsonTask();
        } else if (i == R.id.about) {
            startActivity(new Intent(this, CreditsActivity.class));
        } else if (i == R.id.settings) {
            startActivityForResult(new Intent(this, SettingsActivity.class), 15);
        } else if (i == R.id.donate) {
            Intent donate = new Intent(this, DonateActivity.class);
            donate.putExtra("key", getIntent().getStringExtra("key"));
            startActivity(donate);
        }
        return true;
    }

    @Override
    public void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (data != null) {
            String modifiedItems = data.getStringExtra("modified");
            if (modifiedItems != null) {
                if (modifiedItems.length() > 0) {
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
                                            if (modifiedItems.equals("::clean::")) {
                                                ((CollectionFragment) (
                                                        (PagerAdapter) pager.getAdapter())
                                                        .getFragmentAtPosition(0)).getRVAdapter()
                                                        .notifyDataSetChanged();
                                            } else {
                                                ((WallpapersAdapter) ((CollectionFragment) (
                                                        (PagerAdapter) pager.getAdapter())
                                                        .getFragmentAtPosition(0)).getRVAdapter())
                                                        .updateItems(modifiedItems);
                                            }
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
    public void onBackPressed() {
        if (lastSelected > 0) {
            lastSelected = 0;
            pager.setCurrentItem(lastSelected, true);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected JSONDownloadCallback getCallback() {
        return new JSONDownloadCallback() {
            @Override
            public void onPreExecute() {
                if (getPagerAdapter() != null) {
                    if (getPagerAdapter().getFragmentAtPosition(getCurrentFragmentPosition()) !=
                            null) {
                        if (getPagerAdapter().getFragmentAtPosition(getCurrentFragmentPosition())
                                instanceof CollectionFragment) {
                            ((CollectionFragment) getPagerAdapter().getFragmentAtPosition
                                    (getCurrentFragmentPosition())).refreshContent();
                        }
                    }
                }
            }

            @Override
            public void onSuccess(ArrayList<Collection> collections) {
                FullListHolder.get().getCollections().createList(collections);
                setupTabsAndPager();
                if (getPagerAdapter() != null) {
                    if (getPagerAdapter().getFragmentAtPosition(getCurrentFragmentPosition()) !=
                            null) {
                        if (getPagerAdapter().getFragmentAtPosition(getCurrentFragmentPosition())
                                instanceof CollectionFragment) {
                            ((CollectionFragment) getPagerAdapter().getFragmentAtPosition
                                    (getCurrentFragmentPosition())).setupContent();
                        }
                    }
                }
            }
        };
    }

    public void setupTabsAndPager() {
        if (tabs == null) return;
        tabs.removeAllTabs();
        if ((FullListHolder.get() != null) &&
                (!(FullListHolder.get().getCollections().isEmpty()))) {
            int index = FullListHolder.get().getCollections().getIndexForCollectionWithName
                    ("featured");
            if ((index >= 0) && (!(FullListHolder.get().getCollections().getList().isEmpty())) &&
                    (FullListHolder.get().getCollections().getList().get(index).getWallpapers()
                            != null) && (!(FullListHolder.get().getCollections().getList().get
                    (index).getWallpapers().isEmpty()))) {
                hasFeaturedWallpapers = true;
                tabs.addTab(tabs.newTab().setText(R.string.featured));
                tabs.addTab(tabs.newTab().setText(R.string.collections));
                tabs.setVisibility(View.VISIBLE);
            }
        }
        if (pager == null) return;
        tabs.addOnTabSelectedListener(new TabLayout.ViewPagerOnTabSelectedListener(pager));
        pager.addOnPageChangeListener(new TabLayout.TabLayoutOnPageChangeListener(tabs) {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                lastSelected = position;
                invalidateOptionsMenu();
            }
        });
        pager.setOffscreenPageLimit(hasFeaturedWallpapers ? 2 : 1);
        setupPagerAdapter();
    }

    private void setupPagerAdapter() {
        pagerAdapter = new PagerAdapter(getSupportFragmentManager(), hasFeaturedWallpapers);
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

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putInt("lastSelected", lastSelected);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        lastSelected = savedInstanceState.getInt("lastSelected", 0);
    }

}