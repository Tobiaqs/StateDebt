package tobiass.statedebt;

/**
 * @author Tobias Sytsma
 * @description
 * Well. I wrote this app as a school project for economics.
 * The main goal of the app is to get a quick insight in a country's
 * national debt, and to see how fast it is growing.
 * 
 * Technical details:
 * The information shown in the app is used from www.nationaldebtclocks.org.
 * No rights may be derived from the information shown in this app.
 */

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import org.json.JSONArray;
import org.json.JSONException;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.AnimationUtils;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewSwitcher;

import com.actionbarsherlock.ActionBarSherlock;
import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;
import com.actionbarsherlock.view.MenuItem.OnMenuItemClickListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;

public class Main extends SherlockActivity implements
		ActionBar.OnNavigationListener {

	// I keep all of these variables static because it makes referencing them
	// from Runnables possible.
	protected static final String TAG = "StateDebt";
	protected static final String defaultCountry = "netherlands";

	private static List<Country> cache;
	private static TextView t;
	private static TextView text1;
	private static TextView text2;
	private static TextView text3;
	private static TextView text4;
	private static TextView text5;
	private static TextView text6;
	private static LinearLayout stats;

	private static final AdRequest adRequest = new AdRequest.Builder()
			.addTestDevice("C07EB6547FB494ADCD3396E9FC9E27E8")
			.addTestDevice("68F186B3C5FA1E05E867F38D4AD24CC1").build();
	private AdView adView;

	private ViewSwitcher loading;

	private boolean portrait;

	private int initToCountry = -1;

	private CountryAdapter list;

	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		ActionBarSherlock abs = getSherlock();
		abs.setContentView(R.layout.main);

		// Making sure the ActionBar is created before accessing the Spinner
		// navigation-related stuff.
		setTheme(R.style.Theme_Sherlock_Light_DarkActionBar);

		setTitle(null);
		
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		if(prefs.getInt("version", 3) != 4) {
			deleteFile(LoadCountries.countriesFile);
		}
		prefs.edit().putInt("version", 4).commit();

		// Looking up all Views
		t = (TextView) findViewById(R.id.text);
		stats = (LinearLayout) findViewById(R.id.stats);
		text1 = (TextView) findViewById(R.id.text1);
		text2 = (TextView) findViewById(R.id.text2);
		text3 = (TextView) findViewById(R.id.text3);
		text4 = (TextView) findViewById(R.id.text4);
		text5 = (TextView) findViewById(R.id.text5);
		text6 = (TextView) findViewById(R.id.text6);

		loading = (ViewSwitcher) findViewById(R.id.root);

		adView = (AdView) findViewById(R.id.adview);
		adView.loadAd(adRequest);

		// If 'text1' doesn't exist in the current layout, the device is in
		// portrait mode
		portrait = text1 == null;
		cache = new ArrayList<Country>();

		if (savedInstanceState != null) {
			if (savedInstanceState.containsKey("cache")) {

				// Before the activity is destroyed, the country cache is
				// bundled into savedInstanceState.

				Bundle b = savedInstanceState.getBundle("cache");
				Iterator<String> i = b.keySet().iterator();
				while (i.hasNext()) {
					Country c = new Country(b.getBundle(i.next()));
					cache.add(c);
				}
			}

			// Variable defines which country should get selected as soon as
			// preperation is done.

			isConverted = savedInstanceState.getBoolean("isConverted", false);
			initToCountry = savedInstanceState.getInt("country", -1);
		}

		// Cache is empty? We need to get a list of all countries.
		if (cache.size() == 0) {

			// Run the asynchronous process of getting a list of countries.
			LoadCountries lc = new LoadCountries();
			lc.execute();
		} else {
			// Fill the navigation Spinner with countries
			setupActionbar();
		}
	}

	private void setBusy(boolean busy) {
		if (this.busy != busy) {
			this.busy = busy;
			this.loading.setDisplayedChild(busy ? 1 : 0);
			supportInvalidateOptionsMenu();
		}
	}

	protected void onResume() {
		super.onResume();

		if (adView != null) {
			adView.resume();
		}

		// If the Activity is resumed after being paused, the static variables
		// will all be intact.
		if (currentCountry != null) {
			loadCountry(currentCountry);
		}
	}

	protected void onPause() {

		if (adView != null) {
			adView.pause();
		}

		// Make sure the Timer won't waste resources in the background.
		if (timer != null) {
			timer.cancel();
		}
		super.onPause();

	}

	private class LoadCountry extends AsyncTask<Country, Void, Country> {

		protected void onPreExecute() {
			setBusy(true);
		}

		protected Country doInBackground(Country... params) {
			params[0].fill();

			// If the process succeeded, save the filled country in the JSON.
			// I know, this process is inefficient, but I didn't feel like
			// building an SQLite wrapper.
			if (params[0].isFilled) {
				File cf = getFileStreamPath(LoadCountries.countriesFile);
				if (cf.exists()) {
					try {
						JSONArray countries = new JSONArray(
								Country.convertStream(openFileInput(LoadCountries.countriesFile)));
						countries.put(params[0].index, params[0].json());
						OutputStream os = openFileOutput(
								LoadCountries.countriesFile, 0);
						os.write(countries.toString().getBytes());
					} catch (JSONException e) {
					} catch (IOException e) {
					}
				}
				return params[0];
			}
			return null;
		}

		protected void onPostExecute(Country result) {
			if (result == null) {
				Toast.makeText(Main.this, R.string.error_downloading_country,
						Toast.LENGTH_SHORT).show();
				setBusy(false);
			} else {
				loadCountry(result);
			}
		}
	}

	private class LoadCountries extends AsyncTask<Void, Void, Void> {
		protected void onPreExecute() {
			setBusy(true);
		}

		public static final String countriesFile = "countries";

		protected Void doInBackground(Void... params) {
			File cf = getFileStreamPath(countriesFile);
			if (cf.exists()) {
				try {
					JSONArray countries = new JSONArray(
							Country.convertStream(openFileInput(countriesFile)));
					for (int i = 0; i < countries.length(); i++) {
						Country c = new Country(countries.getJSONObject(i));
						if (initToCountry == -1 && c.urlName.equals(defaultCountry)) {
							initToCountry = i;
						}
						cache.add(c);
					}
				} catch (FileNotFoundException e) {
				} catch (JSONException e) {
				}
			} else {
				Country.getList(Main.this, cache);
				if (cache.size() > 0) {
					JSONArray arr = new JSONArray();
					Iterator<Country> i = cache.iterator();
					int a = 0;
					while (i.hasNext()) {
						Country c = i.next();
						if (initToCountry == -1 && c.urlName.equals(defaultCountry)) {
							initToCountry = a;
						}
						arr.put(c.json());
						a++;
					}
					try {
						OutputStream os = openFileOutput(countriesFile, 0);
						os.write(arr.toString().getBytes());
					} catch (IOException e) {
					}
				}
			}
			return null;
		}

		protected void onPostExecute(Void result) {
			if (cache.size() == 0) {
				Toast.makeText(Main.this, R.string.error_downloading_countries,
						Toast.LENGTH_SHORT).show();
				finish();
			} else {
				setupActionbar();
			}
		}
	}

	private void setupActionbar() {
		ActionBar bar = getSupportActionBar();
		Context context = bar.getThemedContext();

		Country.countryNames = getResources().getStringArray(R.array.countries);

		if (cache.size() != Country.countryNames.length) {
			Country.countryNames = null;
		}

		list = new CountryAdapter(context, R.layout.sherlock_spinner_item,
				cache);
		list.setDropDownViewResource(R.layout.sherlock_spinner_dropdown_item);
		bar.setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);
		bar.setListNavigationCallbacks(list, this);

		for (int i = 0; i < list.getCount(); i++) {
			Country c = list.getItem(i);
			if (initToCountry == c.index && initToCountry != -1) {
				bar.setSelectedNavigationItem(i);
				break;
			}
		}
	}

	private class CountryAdapter extends ArrayAdapter<Country> {
		public CountryAdapter(Context context, int resource, List<Country> list) {
			super(context, resource, list);
			sort(sorter);
		}
	}

	private static Comparator<Country> sorter = new Comparator<Country>() {
		public int compare(Country lhs, Country rhs) {
			return lhs.toString().compareToIgnoreCase(rhs.toString());
		}
	};

	private static double count;
	private static long currentTime;

	// currentCountry should only be used read-only. The Country should not be
	// altered regularly.
	private static Country currentCountry;
	private static Timer timer;

	private void loadCountry(final Country c) {
		if (timer != null) {
			timer.cancel();
		}

		setBusy(false);

		currentCountry = c;

		showConvertButton = !c.currency.equals(dollar);
		if (!showConvertButton) {
			isConverted = false;
		}
		supportInvalidateOptionsMenu();

		timer = new Timer();
		currentTime = System.currentTimeMillis();

		// 1000/100 = 0.1. 1/0.1 = 10 Hz
		// (millisecond - millisecond) = millisecond
		// millisecond / centisecond = second
		double timeDiff = (currentTime - c.startAt) / 100;
		c.start += c.per100MS * timeDiff;
		c.startAt = currentTime;
		setupStats(c);
		t.setVisibility(View.VISIBLE);
		t.setText(null);
		timer.scheduleAtFixedRate(new TimerTask() {
			public void run() {
				runOnUiThread(setStats);
			}
		}, 0, 5000);
		timer.scheduleAtFixedRate(new TimerTask() {
			public void run() {

				// Why don't I just add up the amount per 100ms every time the
				// timer runs? That's because the Java Timer is too inaccurate
				// for that. An even more accurate way would be to use
				// System.nanoTime(), but would require some more calculation
				// because that clock isn't based on the real time, it starts at
				// 0 when the device starts.

				currentTime = System.currentTimeMillis();

				// timeDiff: how many times 100ms has passed since the startAt
				// timestamp.
				double timeDiff = (currentTime - c.startAt) / 100;

				// count: the amount that has been added to the debt since the
				// startAt timestamp.
				count = timeDiff * c.per100MS;

				// Value that will be set in the UI.
				toSet = count + c.start;

				// start is in USD by default. Multiplying by usdRate converts
				// it to the local currency.
				if (!isConverted) {
					toSet *= c.usdRate;
				}

				runOnUiThread(setValue);
			}
		}, 0, 100);
	}

	
	// Sets up some static stuff, landscape
	private void setupStats(Country c) {
		if (!portrait) {
			text1.setText(getString(R.string.population) + "\n"
					+ formatNumber(c.population));
			text2.setText(getString(R.string.gdp) + "\n"
					+ addCurrency(formatNumber(convert(c.gdp)), c.currency));
			
			text4.setText(getString(R.string.debtperson)
						+ "\n"
						+ addCurrency(formatNumber(convert((c.start + count)) / c.population),
								c.currency));

			text6.setText(getString(R.string.increase_per_second)
					+ "\n"
					+ addCurrency(
							formatNumber(convert(c.per100MS * 10)),
							c.currency));
		}
	}

	// This method can be called before the activity is fully created!
	public void onSaveInstanceState(Bundle out) {
		Bundle b = new Bundle();
		Iterator<Country> i = cache.iterator();
		int c = 0;
		while (i.hasNext()) {
			b.putBundle(String.valueOf(c), i.next().bundle());
			c++;
		}
		out.putBundle("cache", b);
		out.putBoolean("isConverted", isConverted);
		int s = getSupportActionBar().getSelectedNavigationIndex();
		if(s > -1) {
			out.putInt("country", list.getItem(s).index);
		}
	}

	public void onDestroy() {
		if (timer != null) {
			timer.cancel();
		}
		currentCountry = null;
		super.onDestroy();
	}

	// When these characters are found in the currency string, it should be
	// placed in front of the amount.

	private static final String euro = "\u20ac";
	private static final String dollar = "\u0024";

	private double convert(double in) {
		return isConverted ? in : in * currentCountry.usdRate;
	}

	private String addCurrency(String in, String currency) {
		// dollar / euro
		if (isConverted) {
			currency = dollar;
		}
		if (currency.contains(dollar) || currency.contains(euro)) {
			in = currency + " " + in;
		} else {
			in += " " + currency;
		}
		return in;
	}

	private static NumberFormat numberFormatter = null;

	private static String formatNumber(double i) {
		if (numberFormatter == null) {
			numberFormatter = NumberFormat.getInstance();
		}
		numberFormatter.setMinimumFractionDigits(2);
		numberFormatter.setMaximumFractionDigits(2);
		return numberFormatter.format(i);
	}

	private static String formatNumber(long i) {
		if (numberFormatter == null) {
			numberFormatter = NumberFormat.getInstance();
		}

		// Using the same numberFormatter instance as in formatNumber(double),
		// so gotta set up the minimum and maximum fraction digits.

		numberFormatter.setMaximumFractionDigits(0);
		numberFormatter.setMinimumFractionDigits(0);
		return numberFormatter.format(i);
	}

	// Portrait mode has several states.
	// Possible values:
	// 0 = Debt/GDP and Debt/Person
	// 1 = Population and GDP
	// 2 = Debt/S
	// -1 = Nothing

	private int portState = -1;

	private final class SetStats implements Runnable {
		private Animation in = null;
		private Animation out = null;

		public void refresh() {
			String[] text = getPortraitText();
			if(portrait) {
				text3.setText(text[0]);
				text4.setText(text[1]);
			}
		}

		private String[] getPortraitText() {
			return getPortraitText(portState);
		}

		private String[] getPortraitText(int portState) {
			String[] s = new String[2];
			switch (portState) {
			case -1:
			case 0:
				s[0] = getString(R.string.debtgdp)
						+ "\n"
						+ formatNumber((currentCountry.start + count)
								/ currentCountry.gdp * 100) + "%";

				s[1] = getString(R.string.debtperson)
						+ "\n"
						+ addCurrency(formatNumber(convert((currentCountry.start + count)) / currentCountry.population),
								currentCountry.currency);
				break;

			case 2:
				s[0] = null;
				s[1] = getString(R.string.increase_per_second)
						+ "\n"
						+ addCurrency(
								formatNumber(convert(currentCountry.per100MS * 10)),
								currentCountry.currency);
				break;

			case 1:
				s[0] = getString(R.string.population) + "\n"
						+ formatNumber(currentCountry.population);
				s[1] = getString(R.string.gdp)
						+ "\n"
						+ addCurrency(
								formatNumber(convert(currentCountry.gdp)),
								currentCountry.currency);
				break;
			}
			return s;
		}

		private void runAnimation(final String t3, final String t4) {

			// Only load the animations once.
			if (in == null) {
				in = AnimationUtils.loadAnimation(Main.this,
						android.R.anim.fade_in);
				in.setDuration(500);
				in.setFillAfter(true);
			}
			if (out == null) {
				out = AnimationUtils.loadAnimation(Main.this,
						android.R.anim.fade_out);
				out.setDuration(500);
				out.setFillAfter(true);
			}

			// Listener needs to be reset every time, because the texts change.
			out.setAnimationListener(new AnimationListener() {
				public void onAnimationStart(Animation animation) {
				}

				public void onAnimationRepeat(Animation animation) {
				}

				public void onAnimationEnd(Animation animation) {
					text3.setText(t3);
					text4.setText(t4);
					stats.startAnimation(in);
				}
			});

			// Run the out animation on the 'stats' layout.
			stats.startAnimation(out);
		}

		public void run() {
			if (currentCountry == null) {
				return;
			}

			// Portrait mode
			if (portrait) {

				// Animations haven't started yet. Switch to state 0 without
				// animation.
				if (portState == -1) {
					portState = 0;
					String[] text = getPortraitText();
					text3.setText(text[0]);
					text4.setText(text[1]);

				} else {
					if (portState == 1) {
						portState = 2;
					} else if (portState == 0) {
						portState = 1;
					} else if (portState == 2) {
						portState = 0;
					}

					String[] text = getPortraitText();
					runAnimation(text[0], text[1]);
				}

				// Landscape mode, just set the TextViews to their landscape
				// text.
			} else {
				String[] text = getPortraitText(0);
				text5.setText(text[0]);
				text4.setText(text[1]);
			}
		}
	}

	private final SetStats setStats = new SetStats();

	private static double toSet = 0;
	private final Runnable setValue = new Runnable() {
		public void run() {
			if (currentCountry != null) {
				t.setText(addCurrency(formatNumber(toSet),
						currentCountry.currency));
			} else {
				t.setText(formatNumber(toSet));
			}
		}
	};

	private int lastItem = -1;
	private boolean busy;

	public boolean onNavigationItemSelected(int itemPosition, long itemId) {
		if (busy && lastItem != -1) {
			// Don't allow changes to the navigation Spinner when busy.
			getSupportActionBar().setSelectedNavigationItem(lastItem);
			return true;
		}

		// Moved to its current position (only happens when moving back because
		// busy, see above)
		if (itemPosition == lastItem) {
			return true;
		}

		lastItem = itemPosition;
		Country cached = cache.get(itemPosition);

		// If the country is filled, we don't need to clean everything up
		// because the country can be loaded right away and it'll overwrite all
		// fields anyways.
		if (cached.isFilled) {
			loadCountry(cached);

			// Cancel timer and clean up all fields. Load the country
			// asynchronously.
		} else {
			clearFields();
			LoadCountry lc = new LoadCountry();
			lc.execute(cached);
		}
		return true;
	}

	private void clearFields() {
		if (timer != null) {
			timer.cancel();
		}
		if (!portrait) {
			text1.setText(null);
			text2.setText(null);
			text5.setText(null);
			text6.setText(null);
		}
		t.setVisibility(View.INVISIBLE);
		text3.setText(null);
		text4.setText(null);
	}

	private MenuItem convert;
	private MenuItem clearCache;
	private MenuItem licenses;
	private MenuItem about;

	private boolean showConvertButton = true;
	private boolean isConverted;

	public boolean onCreateOptionsMenu(Menu menu) {
		if (!busy) {
			if (showConvertButton) {
				convert = menu.add(
						isConverted ? String.format(
								getString(R.string.convert_back),
								currentCountry == null ? "?"
										: currentCountry.currency)
								: getString(R.string.convert_to_dollar))
						.setOnMenuItemClickListener(menuButtonListener);
				convert.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS
						| MenuItem.SHOW_AS_ACTION_WITH_TEXT);
			} else {
				convert = null;
			}

			clearCache = menu.add(R.string.clear_cache).setOnMenuItemClickListener(
					menuButtonListener).setIcon(R.drawable.ic_action_refresh);
			clearCache.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
		}
		
		about = menu.add(R.string.about).setOnMenuItemClickListener(menuButtonListener).setIcon(R.drawable.ic_action_about);
		about.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
		licenses = menu.add(R.string.licenses).setOnMenuItemClickListener(menuButtonListener);
		licenses.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
		return true;
	}

	private OnMenuItemClickListener menuButtonListener = new OnMenuItemClickListener() {
		public boolean onMenuItemClick(MenuItem item) {
			if (item == convert) {
				isConverted = !isConverted;
				setupStats(currentCountry);
				setStats.refresh();
				supportInvalidateOptionsMenu();
			} else if (item == clearCache) {
				File f = getFileStreamPath(LoadCountries.countriesFile);
				f.delete();
				ActionBar bar = getSupportActionBar();
				initToCountry = list.getItem(bar.getSelectedNavigationIndex()).index;
				bar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
				lastItem = -1;
				clearFields();
				cache.clear();
				LoadCountries lc = new LoadCountries();
				lc.execute();
			} else if (item == licenses || item == about) {
				startActivity(new Intent(Main.this, item == licenses ? Licenses.class : About.class));
			}
			return true;
		}
	};
}
