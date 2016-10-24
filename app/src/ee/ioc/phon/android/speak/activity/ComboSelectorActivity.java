package ee.ioc.phon.android.speak.activity;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.ListFragment;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.content.res.Resources;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.speech.RecognizerIntent;
import android.widget.ArrayAdapter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import ee.ioc.phon.android.speak.R;
import ee.ioc.phon.android.speak.adapter.ComboAdapter;
import ee.ioc.phon.android.speak.model.Combo;
import ee.ioc.phon.android.speechutils.Extras;
import ee.ioc.phon.android.speechutils.RecognitionServiceManager;
import ee.ioc.phon.android.speechutils.utils.PreferenceUtils;

public class ComboSelectorActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ComboSelectorFragment details = new ComboSelectorFragment();
        details.setArguments(getIntent().getExtras());
        getFragmentManager().beginTransaction().add(android.R.id.content, details).commit();
    }

    public static class ComboSelectorFragment extends ListFragment {

        int mKey = R.string.keyImeCombo;
        int mDefaultCombos = R.array.defaultImeCombos;
        int mDefaultCombosExcluded = R.array.defaultImeCombosExcluded;

        public void onCreate(Bundle icicle) {
            super.onCreate(icicle);
            Bundle args = getArguments();
            if (args != null && getString(R.string.keyCombo).equals(args.getString("key"))) {
                mKey = R.string.keyCombo;
                mDefaultCombos = R.array.defaultCombos;
                mDefaultCombosExcluded = R.array.defaultCombosExcluded;
            }
            initModel();
        }

        public void onPause() {
            super.onPause();
            ArrayAdapter<Combo> adapter = (ArrayAdapter<Combo>) getListAdapter();
            Set<String> selected = new HashSet<>();
            List<Combo> selectedCombos = new ArrayList<>();
            for (int i = 0; i < adapter.getCount(); i++) {
                Combo combo = adapter.getItem(i);
                if (combo != null && combo.isSelected()) {
                    selected.add(combo.getId());
                    selectedCombos.add(combo);
                }
            }
            PreferenceUtils.putPrefStringSet(PreferenceManager.getDefaultSharedPreferences(getActivity()), getResources(), mKey, selected);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N_MR1) {
                // The app shortcuts correspond to the voice panel combo settings
                if (mKey == R.string.keyCombo) {
                    publishShortcuts(getActivity().getApplicationContext(), selectedCombos);
                }
            }
        }

        private void initModel() {
            Resources res = getResources();
            SharedPreferences mPrefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
            Set<String> combos = PreferenceUtils.getPrefStringSet(mPrefs, res, mKey);
            if (combos == null) {
                combos = PreferenceUtils.getStringSetFromStringArray(res, mDefaultCombos);
            }
            RecognitionServiceManager mngr = new RecognitionServiceManager();
            mngr.setInitiallySelectedCombos(combos);
            mngr.setCombosExcluded(PreferenceUtils.getStringSetFromStringArray(res, mDefaultCombosExcluded));
            mngr.populateCombos(getActivity(), new RecognitionServiceManager.Listener() {

                @Override
                public void onComplete(List<String> combos, Set<String> selectedCombos) {
                    List<Combo> list = new ArrayList<>();
                    for (String comboAsString : combos) {
                        Combo combo = get(comboAsString);
                        if (selectedCombos.contains(comboAsString)) {
                            combo.setSelected(true);
                        }
                        list.add(combo);
                    }
                    Collections.sort(list, Combo.SORT_BY_SELECTED_BY_LANGUAGE);

                    ComboAdapter adapter = new ComboAdapter(ComboSelectorFragment.this, list);
                    setListAdapter(adapter);

                    // TODO: the fast scroll handle overlaps with the checkboxes
                    //getListView().setFastScrollEnabled(true);

                    // TODO: provide more info about the number of (selected) services and languages
                    //getActivity().getActionBar().setSubtitle("" + adapter.getCount());
                }
            });

        }

        private Combo get(String id) {
            return new Combo(getActivity(), id);
        }

        @TargetApi(Build.VERSION_CODES.N_MR1)
        private void publishShortcuts(Context context, List<Combo> selectedCombos) {
            ShortcutManager shortcutManager = context.getSystemService(ShortcutManager.class);
            List<ShortcutInfo> shortcuts = new ArrayList<>();
            for (Combo combo : selectedCombos) {
                Intent intent = new Intent(context, SpeechActionActivity.class);
                intent.setAction(RecognizerIntent.ACTION_WEB_SEARCH);
                intent.putExtra(Extras.EXTRA_COMBO, combo.getId());
                intent.putExtra(Extras.EXTRA_AUTO_START, true);
                // Add service label to short label
                shortcuts.add(new ShortcutInfo.Builder(context, combo.getId())
                        .setIntent(intent)
                        .setShortLabel(combo.getLanguage())
                        .setLongLabel(String.format(getResources().getString(R.string.labelComboItem), combo.getService(), combo.getLanguage()))
                        .setIcon(Icon.createWithResource(context, R.drawable.ic_voice_search_api_material))
                        .build());
            }
            shortcutManager.setDynamicShortcuts(shortcuts);
        }
    }
}