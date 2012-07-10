/* Pandoroid Radio - open source pandora.com client for android
 * Copyright (C) 2011  Andrew Regner <andrew@aregner.com>
 * 
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package com.pandoroid.android;

import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;
import com.actionbarsherlock.view.SubMenu;
import com.pandoroid.pandora.Song;
import com.pandoroid.android.R;

import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;


public class PandoroidPlayer extends SherlockActivity {

	public static final int REQUIRE_SELECT_STATION = 0x10;
	public static final int REQUIRE_LOGIN_CREDS = 0x20;
	public static final String RATING_BAN = "ban";
	public static final String RATING_LOVE = "love";
	public static final String RATING_NONE = null;

	private static ProgressDialog waiting;
	private PandoraRadioService pandora;
	private SharedPreferences prefs;
	private ImageDownloader imageDownloader = new ImageDownloader();

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		setTheme(R.style.Theme_Sherlock);
		super.onCreate(savedInstanceState);
		setContentView(R.layout.player);

		prefs = PreferenceManager.getDefaultSharedPreferences(this);
	}

	
	public boolean onCreateOptionsMenu(Menu menu) {
		SubMenu sub = menu.addSubMenu("Options");
		sub.add(0, R.id.menu_stations, Menu.NONE, R.string.menu_stations);
		sub.add(0, R.id.menu_settings, Menu.NONE, R.string.menu_settings);
		sub.add(0, R.id.menu_logout, Menu.NONE, R.string.menu_logout);
		sub.add(0, R.id.menu_about, Menu.NONE, R.string.menu_about);
		
		MenuItem subMenu = sub.getItem();
		subMenu.setIcon(R.drawable.ic_sysbar_menu);
		subMenu.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	protected void onStart() {
		super.onStart();
		pandora = PandoraRadioService.getInstance(false);
		
		if (pandora == null){
			PandoraRadioService.createPandoraRadioService(getBaseContext());
			PandoroidPlayer.this.startActivity(new Intent(PandoroidPlayer.this, PandoroidLogin.class));
		}
		else if (!pandora.isAlive()){
			PandoroidPlayer.this.startActivity(new Intent(PandoroidPlayer.this, PandoroidLogin.class));
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		pandora = PandoraRadioService.getInstance(false);

		if (pandora != null){
			
			pandora.setListener(OnNewSongListener.class, new OnNewSongListener() {
				public void onNewSong(Song song) {
					updateForNewSong(song);
				}
			});
			if (pandora.song_playback == null){
				String lastStationId = "";
				try {
					lastStationId = prefs.getString("lastStationId", "");
				}
				catch (ClassCastException e){
					prefs.edit().remove("lastStationId").commit();
				}
				
				if(!pandora.setCurrentStationId(lastStationId)){
					//Get a station
					startActivityForResult(new Intent(getApplicationContext(), PandoroidStationSelect.class), REQUIRE_SELECT_STATION);
				} else {	
					pandora.startPlayback();
				}
			}
			else{
				updateForNewSong(pandora.song_playback.getSong());
			}
		}
	}

	protected void updateForNewSong(Song song) {

		pandora.setNotification();
		this.getSupportActionBar().setTitle(String.format(""+song.getTitle()));
		TextView top = (TextView) findViewById(R.id.player_topText);
		//TextView bottom = (TextView) findViewById(R.id.player_bottomText);
		ImageView image = (ImageView) findViewById(R.id.player_image);

		//top.setText(String.format("%s by %s", song.getTitle(), song.getArtist()));
		imageDownloader.download(song.getAlbumCoverUrl(), image);
		top.setText(String.format("%s\n%s", song.getArtist(), song.getAlbum()));
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if(requestCode == REQUIRE_SELECT_STATION && resultCode == RESULT_OK) {
			pandora.setCurrentStationId(data.getStringExtra("stationId"));
			pandora.startPlayback();
		}
	}

	public void controlButtonPressed(View button) {
		switch(button.getId()) {
		case R.id.player_ban:
			pandora.rate(RATING_BAN);
			Toast.makeText(getApplicationContext(), getString(R.string.baned_song), Toast.LENGTH_SHORT).show();
			if(prefs.getBoolean("behave_nextOnBan", true)) {
				pandora.song_playback.skip();
			}
			break;

		case R.id.player_love:
			pandora.rate(RATING_LOVE);
			Toast.makeText(getApplicationContext(), getString(R.string.loved_song), Toast.LENGTH_SHORT).show();
			break;

		case R.id.player_pause:
			pandora.playPause();
			break;

		case R.id.player_next:
			pandora.song_playback.skip();
			break;
		}
	}

	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {

		case R.id.menu_stations:
			startActivityForResult(new Intent(getApplicationContext(), PandoroidStationSelect.class), REQUIRE_SELECT_STATION);
			return true;

		case R.id.menu_logout:
			pandora.signOut();
			PreferenceManager.getDefaultSharedPreferences(getBaseContext()).edit()
				.putString("pandora_username", null)
				.putString("pandora_password", null)
				.commit();
			startActivityForResult(new Intent(getApplicationContext(), PandoroidLogin.class), REQUIRE_LOGIN_CREDS);
			return true;

		case R.id.menu_settings:
			startActivity(new Intent(getApplicationContext(), PandoroidSettings.class));
			return true;
		
		case R.id.menu_about:
			startActivity(new Intent(getApplicationContext(), AboutDialog.class));
			return true;

		default:
			return super.onOptionsItemSelected(item);
		}
	}


	
	public static void dismissWaiting() {
		if(waiting != null && waiting.isShowing()) {
			waiting.dismiss();
		}
	}

	@Override
	protected void onPause() {
		super.onPause();
		dismissWaiting();
		// Another activity is taking focus (this activity is about to be "paused").
	}

	@Override
	protected void onStop() {
		super.onStop();
		dismissWaiting();
	}
}