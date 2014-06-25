package tobiass.statedebt;

import java.io.IOException;

import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.view.animation.Animation;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.ViewSwitcher;

import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.MenuItem;

public class Licenses extends SherlockActivity implements OnItemClickListener {
	
	ViewSwitcher switcher;
	TextView text;
	ScrollView scrollView;
	String[] files;
	int showing = -1;
	
	public void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.licenses);
		
		setTitle(R.string.licenses);
		ListView listView = (ListView) findViewById(R.id.licensesList);
		listView.setAdapter(ArrayAdapter.createFromResource(this, R.array.licenses, android.R.layout.simple_list_item_1));
		listView.setOnItemClickListener(this);
		getSupportActionBar().setDisplayHomeAsUpEnabled(true);
		
		scrollView = (ScrollView) findViewById(R.id.scrollView);
		switcher = (ViewSwitcher) findViewById(R.id.switcher);
		text = (TextView) findViewById(R.id.license);
		files = getResources().getStringArray(R.array.licenses_files);
		
		text.setMovementMethod(LinkMovementMethod.getInstance());
		
		if(savedInstanceState != null) {
			showing = savedInstanceState.getInt("showing", -1);
			if(showing > -1) {
				onItemClick(showing, listView.getItemAtPosition(showing).toString(), false);
			}
		}
	}
	
	private final Runnable scrollTop = new Runnable() {
		public void run() {
			scrollView.scrollTo(0, 0);
		}
	};

	public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
		onItemClick(position, ((TextView) view).getText().toString(), true);
	}
	
	public void onItemClick(int position, String title, boolean animated) {
		if(animated) {
			switcher.setDisplayedChild(1);
		}
		else {
			Animation in = switcher.getInAnimation();
			Animation out = switcher.getOutAnimation();
			switcher.setInAnimation(null);
			switcher.setOutAnimation(null);
			switcher.setDisplayedChild(1);
			switcher.setInAnimation(in);
			switcher.setOutAnimation(out);
		}
		showing = position;
		scrollView.post(scrollTop);
		try {
			getSupportActionBar().setSubtitle(title);
			text.setText(Country.convertStream(getAssets().open("license/"+files[position])));
		} catch (IOException e) {
			text.setText("File unreadable.");
		}
	}
	
	public boolean onOptionsItemSelected(MenuItem item) {
		if(item.getItemId() == android.R.id.home) {
			onBackPressed();
		}
		return true;
	}
	
	public void onSaveInstanceState(Bundle outState) {
		outState.putInt("showing", showing);
	}
	
	public void onBackPressed() {
		if(switcher.getDisplayedChild() == 1) {
			showing = -1;
			switcher.setDisplayedChild(0);
			getSupportActionBar().setSubtitle(null);
		}
		else {
			finish();
		}
	}
}
