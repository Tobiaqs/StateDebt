package tobiass.statedebt;

/**
 * Data structure for countries. They can either be filled or not filled.
 * Filled means that the exact variables of a Country have been populated.
 */

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONException;
import org.json.JSONObject;

import tobiass.statedebt.util.StringEscapeUtils;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Build;
import android.os.Bundle;

public class Country {

	protected static final String TAG = "Country";
	public String abbreviation;
	public String urlName;
	public String fullName;
	public String currency;
	public boolean isFilled;
	public int index = -1;

	// USD
	public double per100MS = 0;
	public double start = 0;

	// USD*usdRate = amount in currency
	public double usdRate = 0;

	// Timestamp bound to start
	public long startAt = 0;
	public long population = 0;
	public long gdp = 0;
	
	public static String[] countryNames = null;
	public String toString() {
		if(countryNames != null && index != -1) {
			return countryNames[index];
		}
		return fullName;
	}

	public Country(String jsonstr) throws JSONException {
		this(new JSONObject(jsonstr));
	}

	public Country(JSONObject obj) throws JSONException {
		abbreviation = obj.optString("abbreviation", null);
		urlName = obj.optString("urlName", null);
		fullName = obj.optString("fullName", null);
		currency = obj.optString("currency", null);
		isFilled = obj.optBoolean("isFilled", false);
		index = obj.optInt("index", -1);
		per100MS = obj.optDouble("per100MS", 0);
		start = obj.optDouble("start", 0);
		usdRate = obj.optDouble("usdRate", 0);
		startAt = obj.optLong("startAt", 0);
		population = obj.optLong("population", 0);
		gdp = obj.optLong("gdp", 0);
	}

	public Country() {

	}

	public Country(Bundle b) {
		abbreviation = b.getString("abbreviation");
		urlName = b.getString("urlName");
		fullName = b.getString("fullName");
		currency = b.getString("currency");
		isFilled = b.getBoolean("isFilled");
		index = b.getInt("index");
		per100MS = b.getDouble("per100MS");
		start = b.getDouble("start");
		usdRate = b.getDouble("usdRate");
		startAt = b.getLong("startAt");
		population = b.getLong("population");
		gdp = b.getLong("gdp");
	}

	public Bundle bundle() {
		Bundle b = new Bundle();
		bundle(b);
		return b;
	}

	public void bundle(Bundle b) {
		b.putString("abbreviation", abbreviation);
		b.putString("urlName", urlName);
		b.putString("fullName", fullName);
		b.putString("currency", currency);
		b.putBoolean("isFilled", isFilled);
		b.putInt("index", index);
		b.putDouble("per100MS", per100MS);
		b.putDouble("start", start);
		b.putDouble("usdRate", usdRate);
		b.putLong("startAt", startAt);
		b.putLong("population", population);
		b.putLong("gdp", gdp);
	}

	public JSONObject json() {
		JSONObject json = new JSONObject();
		try {
			json.put("abbreviation", abbreviation).put("urlName", urlName)
					.put("fullName", fullName).put("currency", currency)
					.put("isFilled", isFilled).put("index", index)
					.put("per100MS", per100MS)
					.put("start", start).put("usdRate", usdRate)
					.put("startAt", startAt).put("population", population)
					.put("gdp", gdp);

		} catch (JSONException e) {
			return null;
		}
		return json;
	}

	// Static so all of these are only compiled once.
	private static final Pattern startIncrementPattern = Pattern
			.compile("setDebtClockFast\\(([0-9\\.]+), ([0-9\\.]+)\\);");
	private static final Pattern GDPPattern = Pattern
			.compile("<span id=\"GDP\">.+?;(.+?)</span>");
	private static final Pattern currencyPattern = Pattern
			.compile("\\$\\('span#currLeftHouse'\\).html\\('(.+?)'\\);");
	private static final Pattern populationPattern = Pattern
			.compile("<strong>Population:</strong>\n<p>([0-9,]+)</p>");
	private static final Pattern ratePattern = Pattern
			.compile("Math\\.round\\(houseHoldShare\\*([0-9\\.]+)");

	// See top
	public boolean fill() {
		if (!isFilled) {
			try {
				HttpURLConnection conn = (HttpURLConnection) new URL(
						"http://www.nationaldebtclocks.org/debtclock/"
								+ urlName).openConnection();
				conn.addRequestProperty("User-Agent", "NationalDebtClock");
				InputStream is = conn.getInputStream();
				String s = convertStream(is);
				Matcher m = startIncrementPattern.matcher(s);
				if (m.find()) {
					start = Double.parseDouble(m.group(1));
					startAt = System.currentTimeMillis();
					per100MS = Double.parseDouble(m.group(2)) / 10;
				} else {
					return false;
				}

				m = GDPPattern.matcher(s);
				if (m.find()) {
					gdp = Long.valueOf(m.group(1).replace(",", ""));
				} else {
					return false;
				}

				m = populationPattern.matcher(s);
				if (m.find()) {
					population = Long.parseLong(m.group(1).replace(",", ""));
				} else {
					return false;
				}

				m = currencyPattern.matcher(s);
				if (m.find()) {
					currency = StringEscapeUtils.unescapeHtml(m.group(1));
				} else {
					return false;
				}

				m = ratePattern.matcher(s);
				if (m.find()) {
					usdRate = Double.parseDouble(m.group(1));
				} else {
					return false;
				}

				isFilled = true;
			} catch (IOException e) {
			}
		}
		return isFilled;
	}

	private static final Pattern countriesPattern = Pattern
			.compile("href=\"/debtclock/([a-z]+)\">\n<img src=\"http://www.nationaldebtclocks.org/img/flags/([a-z]+).png\" alt=\"(.+?)\" title=\".+?\">");

	public static void getList(Context context, List<Country> populate) {
		try {
			HttpURLConnection conn = (HttpURLConnection) new URL(
					"http://www.nationaldebtclocks.org/").openConnection();
			conn.addRequestProperty("User-Agent", getUserAgent(context));
			InputStream is = conn.getInputStream();
			String s = convertStream(is);
			int i = 0;
			Matcher m = countriesPattern.matcher(s);
			while (m.find()) {
				Country c = new Country();
				c.urlName = m.group(1);
				c.abbreviation = m.group(2);
				c.fullName = m.group(3);
				c.index = i;
				populate.add(c);
				i++;
			}
		} catch (IOException e) {
			populate.clear();
		}
	}

	public static List<Country> getList(Context context) {
		List<Country> list = new ArrayList<Country>();
		getList(context, list);
		return list;
	}

	public static String convertStream(InputStream is) {
		Scanner s = new Scanner(is, "UTF-8");
		s.useDelimiter("\\A");
		String a = s.hasNext() ? s.next() : "";
		s.close();
		return a;
	}

	public static String getUserAgent(Context c) {
		try {
			PackageInfo pInfo = c.getPackageManager().getPackageInfo(
					c.getPackageName(), 0);
			return "StateDebt/" + pInfo.versionName + " (" + Build.MANUFACTURER
					+ " " + Build.PRODUCT + " @ Android "
					+ Build.VERSION.RELEASE + ")";
		} catch (NameNotFoundException e) {
			return null;
		}
	}
}
