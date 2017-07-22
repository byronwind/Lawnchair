package ch.deletescape.lawnchair.iconpack;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.UserHandle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ch.deletescape.lawnchair.AppInfo;
import ch.deletescape.lawnchair.LauncherAppState;
import ch.deletescape.lawnchair.R;
import ch.deletescape.lawnchair.Utilities;
import ch.deletescape.lawnchair.blur.BlurWallpaperProvider;
import ch.deletescape.lawnchair.compat.LauncherActivityInfoCompat;
import ch.deletescape.lawnchair.config.FeatureFlags;

public class EditIconActivity extends Activity implements CustomIconAdapter.Listener, IconPackAdapter.Listener {

    private static final int REQUEST_PICK_ICON = 0;
    private static final int REQUEST_GRANT_PERMISSION = 1;
    private LauncherActivityInfoCompat mInfo;
    private SharedPreferences mPrefs;
    private String mPendingData;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        FeatureFlags.applyDarkTheme(this);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_icon);

        mPrefs = Utilities.getPrefs(getApplicationContext());

        ComponentName componentName = getIntent().getExtras().getParcelable("componentName");
        UserHandle user = getIntent().getExtras().getParcelable("userHandle");
        Intent intent = new Intent();
        intent.setComponent(componentName);
        mInfo = LauncherActivityInfoCompat.create(this, user, intent);
        AppInfo appInfo = new AppInfo(this, mInfo, user, LauncherAppState.getInstance().getIconCache());
        List<IconPackInfo> iconPacks = new ArrayList<>(loadAvailableIconPacks().values());
        Collections.sort(iconPacks, new Comparator<IconPackInfo>() {
            @Override
            public int compare(IconPackInfo lhs, IconPackInfo rhs) {
                return lhs.label.toString().compareToIgnoreCase(rhs.label.toString());
            }
        });

        setTitle(appInfo.originalTitle);

        BlurWallpaperProvider.applyBlurBackground(this);

        RecyclerView iconRecyclerView = findViewById(R.id.iconRecyclerView);
        CustomIconAdapter iconAdapter = new CustomIconAdapter(this, mInfo, iconPacks);
        iconAdapter.setListener(this);
        iconRecyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        iconRecyclerView.setAdapter(iconAdapter);

        RecyclerView iconPackRecyclerView = findViewById(R.id.iconPackRecyclerView);
        IconPackAdapter iconPackAdapter = new IconPackAdapter(this, iconPacks);
        iconPackAdapter.setListener(this);
        iconPackRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        iconPackRecyclerView.setAdapter(iconPackAdapter);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_edit_icon, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_reset) {
            resetIcon();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private Map<String, IconPackInfo> loadAvailableIconPacks() {
        PackageManager pm = getPackageManager();
        Map<String, IconPackInfo> iconPacks = new HashMap<>();
        List<ResolveInfo> list;
        list = pm.queryIntentActivities(new Intent("com.novalauncher.THEME"), 0);
        list.addAll(pm.queryIntentActivities(new Intent("org.adw.launcher.icons.ACTION_PICK_ICON"), 0));
        list.addAll(pm.queryIntentActivities(new Intent("com.dlto.atom.launcher.THEME"), 0));
        list.addAll(pm.queryIntentActivities(new Intent("android.intent.action.MAIN").addCategory("com.anddoes.launcher.THEME"), 0));
        for (ResolveInfo info : list) {
            IconPack iconPack = IconPackProvider.loadAndGetIconPack(this, info.activityInfo.packageName);
            iconPacks.put(
                    info.activityInfo.packageName,
                    new IconPackInfo(iconPack, info, pm));
        }
        return iconPacks;
    }

    @Override
    public void onSelect(CustomIconAdapter.IconInfo iconInfo) {
        setAlternateIcon(iconInfo.toString());
    }

    private void setAlternateIcon(String alternateIcon) {
        String key = mInfo.getComponentName().flattenToString();
        mPrefs.edit().putString("alternateIcon_" + key, alternateIcon).apply();
        updateCache();
        finish();
    }

    private void resetIcon() {
        String key = mInfo.getComponentName().flattenToString();
        mPrefs.edit().remove("alternateIcon_" + key).apply();
        updateCache();
        finish();
    }

    private void updateCache() {
        Utilities.updatePackage(this, mInfo.getUser(), mInfo.getComponentName().getPackageName());
    }

    @Override
    public void startPicker(Intent intent) {
        startActivityForResult(intent, REQUEST_PICK_ICON);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_PICK_ICON) {
            if (resultCode == RESULT_OK) {
                try {
                    String dataUri = data.getData().toString();
                    if (dataUri.startsWith("file") && !isPermissionGranted()) {
                        mPendingData = dataUri;
                        ActivityCompat.requestPermissions(
                                this,
                                new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                                REQUEST_GRANT_PERMISSION);
                        return;
                    }
                    setAlternateIcon("uri/" + dataUri);
                } catch (Exception e) {
                    Toast.makeText(this, R.string.icon_pack_unsupported, Toast.LENGTH_SHORT).show();
                }
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_GRANT_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                setAlternateIcon("uri/" + mPendingData);
            } else {
                Toast.makeText(this, R.string.icon_pack_permission_denied, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private boolean isPermissionGranted() {
        return ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    static class IconPackInfo {
        ResolveInfo resolveInfo;
        IconPack iconPack;
        String packageName;
        CharSequence label;
        Drawable icon;

        IconPackInfo(IconPack ip, ResolveInfo r, PackageManager packageManager) {
            this(ip, r.loadIcon(packageManager), r.loadLabel(packageManager));
            resolveInfo = r;
        }

        IconPackInfo(IconPack ip, Drawable ic, CharSequence lb) {
            iconPack = ip;
            packageName = iconPack.getPackageName();
            icon = ic;
            label = lb;
        }

        IconPack getIconPack() {
            return iconPack;
        }
    }
}