package jp.saka.micapp;

import android.content.Context;
import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.TextView;
import android.widget.Button;
import android.media.MediaRecorder;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.AudioTrack.OnPlaybackPositionUpdateListener;
import android.media.AudioRecord;
import android.media.AudioFormat;
import android.util.Log;
import java.lang.InterruptedException;
import android.os.Handler;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import java.util.Random;

import android.content.BroadcastReceiver;
import android.content.Intent;
import android.view.KeyEvent;
import android.widget.Toast;
import android.widget.CheckBox;

import android.content.IntentFilter;

public class MicApp extends Activity
{
	private TextView status_view;
	private TextView volume_seek_bar_status_view;
	private Button start_stop_button;
	private Button white_noise_on_off_button;
	private Button speaker_on_off_button;
	private Button update_view_button;
	private CheckBox auto_speaker_off_checkbox;
	private CheckBox auto_speaker_on_checkbox;
	private CheckBox audio_io_auto_restart_speaker_checkbox;
	private CheckBox audio_io_auto_restart_headset_checkbox;
	private CheckBox mic_mute_checkbox;
	private CheckBox bluetooth_sco_on_checkbox;
	private RadioGroup audio_mode_radio_group;
	private RadioGroup audio_stream_type_radio_group;
	private RadioGroup headset_monitering_method_radio_group;
	private SeekBar volume_seek_bar;
	private final int sample_rate = 16000;
	private AudioTrack audio_track = null;
	private AudioRecord audio_record = null;
	private int in_buffsize;
	private int out_buffsize;
	private short[] buf_short;
	private byte[] buf_byte;
	private boolean audio_io_running = false;
	private boolean white_noise_on = false;
	private AudioManager audio_manager;
	private Handler handler;
	private boolean audio_io_thread_loop;
	private static BroadcastReceiver headset_monitering_bc_recver = null;
	private MicApp micapp_activity;
	private String toast_msg;
	private boolean is_wired_headset_plugged = false;
	private boolean headset_moniter_thread_loop = false;
	private int audio_read_bytes = 0;
	private int audio_write_bytes = 0;


	///////////////////////////////////////////////////////
	// ボリュームキーの監視
	///////////////////////////////////////////////////////

	@Override
	public boolean onKeyDown(int code, KeyEvent event)
	{
		boolean ret = super.onKeyDown(code, event);
		return ret;
	}

	@Override
	public boolean onKeyUp(int code, KeyEvent event)
	{
		return super.onKeyUp(code, event);
	}

	@Override
	public boolean dispatchKeyEvent(KeyEvent event)
	{
		boolean ret = super.dispatchKeyEvent(event);
		// ボリュームキーを監視して自分でオーディオストリームの音量を変更するのは邪道。
		// setVolumeControlStream()を使うのがスマートなやり方。
		// なのでここではボリュームキー押下されたらボリュームシークバーの表示を更新するだけ。
		update_view();
		return ret;
	}

	///////////////////////////////////////////////////////
	// テキストビュー、ボタン、ラジオボタン、シークバーの表示を更新
	///////////////////////////////////////////////////////

	private void update_status_view()
	{
		String status = "";

		if (audio_io_running) {
			status += "オーディオ入出力：動作中 ";
			if (white_noise_on) {
				status += " (ホワイトノイズ出力中) ";
			}
			status += " \n";
		} else {
			status += "オーディオ入出力：停止中 \n";
		}

		status += "バッファサイズ：入力 " + in_buffsize + "B  出力 " + out_buffsize + "B \n";

		status += "入出力総バイト数：入力 " + audio_read_bytes + "B  出力 " + audio_write_bytes + "B \n";

		if (audio_manager.isSpeakerphoneOn()) {
			status += "本体スピーカー：ON \n";
		} else {
			status += "本体スピーカー：OFF \n";
		}

		if (audio_manager.isWiredHeadsetOn()) {
			status += "有線ヘッドセット：接続 \n";
		} else {
			status += "有線ヘッドセット：未接続 \n";
		}

		if (audio_manager.isBluetoothScoOn()) {
			status += "Bluetooth SCO：ON \n";
		} else {
			status += "Bluetooth SCO：OFF \n";
		}

		if (audio_manager.isBluetoothA2dpOn()) {
			status += "Bluetooth A2DP：ON \n";
		} else {
			status += "Bluetooth A2DP：OFF \n";
		}

		//        if (audio_manager.isSpeakerphoneOn()) {
		//            status += "オーディオ出力先：本体スピーカー \n";
		//        } else {
		//            if (audio_manager.isWiredHeadsetOn()) {
		//                status += "オーディオ出力先：有線ヘッドセット \n";
		//            } else {
		//                status += "オーディオ出力先：受話器 \n";
		//            }
		//        }

		status_view.setText(status);
	}

	private void update_button()
	{
		if (audio_io_running) {
			start_stop_button.setText("オーディオ入出力を終了する");
			white_noise_on_off_button.setEnabled(true);
		} else {
			start_stop_button.setText("オーディオ入出力を開始する");
			white_noise_on_off_button.setEnabled(false);
		}

		if (!white_noise_on) {
			white_noise_on_off_button.setText("ホワイトノイズを出力する");
		} else {
			white_noise_on_off_button.setText("ホワイトノイズを停止する");
		}

		if (audio_manager.isSpeakerphoneOn()) {
			speaker_on_off_button.setText("スピーカをOFFする");
		} else {
			speaker_on_off_button.setText("スピーカをONする");
		}

		update_view_button.setText("スピーカやヘッドセットを確認する");
	}

	private void update_view()
	{
		// 現在のオーディオモードに合わせてラジオボタンを更新
		int audio_mode = audio_manager.getMode();

		if (audio_mode == AudioManager.MODE_NORMAL) {
			audio_mode_radio_group.check(R.id.AudioModeRadioButton_Normal);
		} else if (audio_mode == AudioManager.MODE_RINGTONE) {
			audio_mode_radio_group.check(R.id.AudioModeRadioButton_Ringtone);
		} else if (audio_mode == AudioManager.MODE_IN_CALL) {
			audio_mode_radio_group.check(R.id.AudioModeRadioButton_InCall);
		} else if (audio_mode == AudioManager.MODE_IN_COMMUNICATION) {
			audio_mode_radio_group.check(R.id.AudioModeRadioButton_InCommunication);
		}

		// 現在のマイクミュート状態に合わせてチェックボックスを更新
		mic_mute_checkbox.setChecked(audio_manager.isMicrophoneMute());

		// 現在のBluetoothScoOn状態に合わせてチェックボックスを更新
		bluetooth_sco_on_checkbox.setChecked(audio_manager.isBluetoothScoOn());

		// 現在のオーディオストリームタイプやボリュームに合わせてボリュームスライダーを更新
		update_volume_seek_bar();

		update_status_view();

		update_button();
	}

	/**
	 * サポートされているSampleRate/Format/Channelをチェックする
	 */
	private static int[] mSampleRates = new int[] { 8000, 11025, 22050, 44100 };
	public AudioRecord findAudioRecord() {
		for (int rate : mSampleRates) {
			for (short audioFormat : new short[] { AudioFormat.ENCODING_PCM_8BIT, AudioFormat.ENCODING_PCM_16BIT }) {
				for (short channelConfig : new short[] { AudioFormat.CHANNEL_IN_MONO, AudioFormat.CHANNEL_IN_STEREO }) {
					try {
						Log.d("sakalog", "Attempting rate " + rate + "Hz, bits: " + audioFormat + ", channel: "
								+ channelConfig);
						int bufferSize = AudioRecord.getMinBufferSize(rate, channelConfig, audioFormat);

						if (bufferSize != AudioRecord.ERROR_BAD_VALUE) {
							// check if we can instantiate and have a success
							AudioRecord recorder = new AudioRecord(MediaRecorder.AudioSource.MIC, rate, channelConfig, audioFormat, bufferSize);

							if (recorder.getState() == AudioRecord.STATE_INITIALIZED) {
								// return recorder;
								Log.d("sakalog", "ok!");
							}
						}
					} catch (Exception e) {
						Log.e("sakalog", rate + "Exception, keep trying.",e);
					}
				}
			}
		}
		return null;
	}

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		micapp_activity = this;

		// これは必要なんか？
		// android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);

		// オーディオ入出力で必要なバッファサイズの下限値を求める
		in_buffsize = AudioRecord.getMinBufferSize(sample_rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
		if ((in_buffsize == AudioRecord.ERROR_BAD_VALUE) || (in_buffsize == AudioRecord.ERROR)) {
			Toast.makeText(micapp_activity, "AudioRecordエラー", Toast.LENGTH_SHORT).show();
			return;
		}
		out_buffsize = AudioTrack.getMinBufferSize(sample_rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
		if ((out_buffsize == AudioTrack.ERROR_BAD_VALUE) || (out_buffsize == AudioTrack.ERROR)) {
			Toast.makeText(micapp_activity, "AudioTrackエラー", Toast.LENGTH_SHORT).show();
			return;
		}

		// バッファサイズは大きい方に合わせる
		if (in_buffsize < out_buffsize) {
			in_buffsize = out_buffsize;
		} else {
			out_buffsize = in_buffsize;
		}
		buf_short = new short[out_buffsize/2];
		buf_byte = new byte[out_buffsize];

		// オーディオマネージャのオブジェクトを取得
		audio_manager = ((AudioManager) getSystemService(Context.AUDIO_SERVICE));

		// ここでsetMode(MODE_IN_CALL)をするとAudioRecorderがなぜか使えないなぁ
		// audio_manager.setMode(AudioManager.MODE_IN_CALL);

		// テキストビュー、ボタン、ラジオボタン、シークバーのオブジェクトを取得
		handler = new Handler();
		start_stop_button = (Button) findViewById(R.id.ToggleStartStopButton);
		white_noise_on_off_button = (Button) findViewById(R.id.ToggleNoiseOnButton);
		speaker_on_off_button = (Button) findViewById(R.id.ToggleSpeakerOnButton);
		update_view_button = (Button) findViewById(R.id.UpdateUIButton);
		status_view = (TextView) findViewById(R.id.StatusView);
		audio_mode_radio_group = (RadioGroup) findViewById(R.id.AudioModeRadioGroup);
		audio_stream_type_radio_group = (RadioGroup) findViewById(R.id.AudioStreamTypeRadioGroup);
		headset_monitering_method_radio_group = (RadioGroup) findViewById(R.id.HeadsetMoniteringMethodRadioGroup);
		volume_seek_bar_status_view = (TextView) findViewById(R.id.VolumeSeekBarStatus);
		volume_seek_bar = (SeekBar) findViewById(R.id.VolumeSeekBar);
		auto_speaker_off_checkbox = (CheckBox) findViewById(R.id.AutoSpeakerOffCheckbox);
		auto_speaker_on_checkbox = (CheckBox) findViewById(R.id.AutoSpeakerOnCheckbox);
		audio_io_auto_restart_speaker_checkbox = (CheckBox) findViewById(R.id.AudioIOAutoRestart_Speaker_Checkbox);
		audio_io_auto_restart_headset_checkbox = (CheckBox) findViewById(R.id.AudioIOAutoRestart_Headset_Checkbox);
		mic_mute_checkbox = (CheckBox) findViewById(R.id.MicMuteCheckbox);
		bluetooth_sco_on_checkbox = (CheckBox) findViewById(R.id.BluetoothScoOnCheckbox);

		// ヘッドセット接続時に、自動的に本体スピーカーをOFFするかどうかのチェックボックス
		auto_speaker_off_checkbox.setChecked(true);
		auto_speaker_off_checkbox.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
			}
		});

		// ヘッドセット取外し時に、自動的に本体スピーカーをONするかどうかのチェックボックス
		auto_speaker_on_checkbox.setChecked(true);
		auto_speaker_on_checkbox.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
			}
		});

		// 本体スピーカーON/OFF変更時に、自動的にオーディオ入出力をリスタートするかどうかのチェックボックス
		// リスタートとはAudioRecordクラスとAudioTrackクラスのオブジェクトをreleaseしてnewしなおすこと
		audio_io_auto_restart_speaker_checkbox.setChecked(true);
		audio_io_auto_restart_speaker_checkbox.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
			}
		});

		// ヘッドセット接続状態変化時に、自動的にオーディオ入出力をリスタートするかどうかのチェックボックス
		audio_io_auto_restart_headset_checkbox.setChecked(true);
		audio_io_auto_restart_headset_checkbox.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
			}
		});

		// マイクミュートするかどうかのチェックボックス
		mic_mute_checkbox.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				CheckBox check_box = (CheckBox) v;
				boolean bl = check_box.isChecked();
				Log.d("sakalog", "setMicrohponeMute(" + bl + ")");
				audio_manager.setMicrophoneMute(bl);
				update_view();
			}
		});

		// Bluetooth SCOをONするかどうかのチェックボックス
		bluetooth_sco_on_checkbox.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				CheckBox check_box = (CheckBox) v;
				boolean bl = check_box.isChecked();
				Log.d("sakalog", "setBluetoothScoOn(" + bl + ")");
				if (bl) {
					audio_manager.startBluetoothSco();
				} else {
					audio_manager.stopBluetoothSco();
				}
				update_view();
			}
		});

		// 本体スピーカーON/OFFボタンが押された時の処理
		speaker_on_off_button.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				boolean bl = !audio_manager.isSpeakerphoneOn();
				Log.d("sakalog", "setSpeakerphoneOn(" + bl + ") 3");
				boolean restart = false;
				// 本体スピーカーの状態を変えるときは、
				// いったんAudioRecordやAudioTrackを停止して
				// 状態を変えて、再び開始する
				if (audio_io_auto_restart_speaker_checkbox.isChecked()) {
					if (audio_io_running) {
						toggle_start_stop();
						while (audio_io_running) {
							try {
								Thread.sleep(0, 1000);  //wait for 1msec
							} catch (InterruptedException e) {
							}
						}
						restart = true;
					}
				}
				audio_manager.setSpeakerphoneOn(bl);
				if (audio_io_auto_restart_speaker_checkbox.isChecked() && restart) {
					toggle_start_stop();
				}
				update_view();
			}
		});

		// オーディオ動作中にホワイトノイズ開始・終了ボタンが押された時の処理
		white_noise_on_off_button.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				toggle_white_noise_on_off();
				update_view();
			}
		});

		// 本体スピーカーやヘッドセットの確認をするボタンが押された時の処理
		update_view_button.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				update_view();
			}
		});

		// オーディオ開始・終了ボタンが押された時の処理
		start_stop_button.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				toggle_start_stop();
			}
		});

		// オーディオストリームを選択するラジオボタンの初期値をSTREAM_VOICE_CALLにする
		// ラジオボタンが変更されたときはオーディオストリームを変更する
		// オーディオ入出力が動作中は、オーディオストリームのラジオボタンは変更不可とする
		audio_stream_type_radio_group.check(R.id.AudioStreamTypeRadioButton_VoiceCall);
		audio_stream_type_radio_group.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
			public void onCheckedChanged(RadioGroup group, int id) { 
				Log.d("sakalog", "setVolumeControlStream()");
				micapp_activity.setVolumeControlStream(get_audio_stream_type());
				update_view();
			}
		});

		// このアプリが使用するオーディオストリームを指定する
		// 指定すると、このアプリがフォアグラウンドにあるときにボリュームキーが押された時は、
		// 指定したオーディオストリームの音量が変更されるようになる
		this.setVolumeControlStream(get_audio_stream_type());

		// オーディオモードを選択するラジオボタンの初期値をMODE_NORAMLにする
		// ラジオボタンが変更されたときはオーディオモードを変更する
		audio_mode_radio_group.check(R.id.AudioModeRadioButton_Normal);
		audio_mode_radio_group.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
			public void onCheckedChanged(RadioGroup group, int id) { 
				int radio_id = audio_mode_radio_group.getCheckedRadioButtonId();
				if (radio_id == R.id.AudioModeRadioButton_Normal) {
					Log.d("sakalog", "setMode(MODE_NORMAL)");
					audio_manager.setMode(AudioManager.MODE_NORMAL);
				} else if (radio_id == R.id.AudioModeRadioButton_Ringtone) {
					Log.d("sakalog", "setMode(MODE_RINGTONE)");
					audio_manager.setMode(AudioManager.MODE_RINGTONE);
				} else if (radio_id == R.id.AudioModeRadioButton_InCall) {
					Log.d("sakalog", "setMode(MODE_IN_CALL)");
					audio_manager.setMode(AudioManager.MODE_IN_CALL);
				} else if (radio_id == R.id.AudioModeRadioButton_InCommunication) {
					Log.d("sakalog", "setMode(MODE_IN_COMMUNICATION)");
					audio_manager.setMode(AudioManager.MODE_IN_COMMUNICATION);
				}
				update_view();
			}
		});

		// アプリ起動時のWiredヘッドセット接続状態に合わせて本体スピーカーのON/OFFをセットする
		is_wired_headset_plugged = audio_manager.isWiredHeadsetOn();
		// update_speakerphone();

		// ヘッドセット監視方法を選択するラジオボタンの初期値をBroadcastReceiverにする
		headset_monitering_method_radio_group.check(R.id.HeadsetMoniteringMethodRadioButton_BroadcastReceiver1);
		update_headset_monitering_method();
		headset_monitering_method_radio_group.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
			public void onCheckedChanged(RadioGroup group, int id) { 
				update_headset_monitering_method();
			}
		});

		// ボリュームのシークバーが変更されたときの処理
		volume_seek_bar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {
			}
		@Override
		public void onProgressChanged(SeekBar seekBar, int progress, boolean fromTouch) {
			if (fromTouch) {
				int audio_stream_type = get_audio_stream_type();
				Log.d("sakalog", "setStreamVolume(" + progress + ")");
				audio_manager.setStreamVolume(audio_stream_type, progress, 0);
				update_view();
			}
		}
		@Override
		public void onStopTrackingTouch(SeekBar seekBar) {
		}
		});
		volume_seek_bar.setEnabled(true);

		BroadcastReceiver bluetooth_sco_monitor_bc_recver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				update_view();
			}
		};
		IntentFilter intent_filter = new IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_CHANGED);
		registerReceiver(bluetooth_sco_monitor_bc_recver, intent_filter);

		// テキストビュー、ボタン、ラジオボタン、シークバーの表示を更新する
		update_view();
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		if (headset_monitering_bc_recver != null) {
			unregisterReceiver(headset_monitering_bc_recver);
			headset_monitering_bc_recver = null;
		}
		headset_moniter_thread_loop = false;
		audio_io_thread_loop = false;
	}

	private void update_headset_monitering_method()
	{
		int radio_id = headset_monitering_method_radio_group.getCheckedRadioButtonId();
		if ((radio_id == R.id.HeadsetMoniteringMethodRadioButton_BroadcastReceiver1) ||
				(radio_id == R.id.HeadsetMoniteringMethodRadioButton_BroadcastReceiver2)) {

			// Wiredヘッドセットの挿抜を監視して、本体スピーカーのON/OFFを切り替える
			headset_moniter_thread_loop = false;
			if (headset_monitering_bc_recver == null) {
				headset_monitering_bc_recver = new BroadcastReceiver() {
					@Override
					public void onReceive(Context context, Intent intent) {

						boolean is_wired_headset_on = audio_manager.isWiredHeadsetOn();
						int state = intent.getIntExtra("state", 0);

						boolean is_headset_plugged_now = false;
						int radio_id = headset_monitering_method_radio_group.getCheckedRadioButtonId();
						if (radio_id == R.id.HeadsetMoniteringMethodRadioButton_BroadcastReceiver1) {
							// BroadcastReceiverを信じる
							is_headset_plugged_now = (state > 0);
						} else if (radio_id == R.id.HeadsetMoniteringMethodRadioButton_BroadcastReceiver2) {
							// isWiredHeadsetOnを信じる
							is_headset_plugged_now = is_wired_headset_on;
						}

						if (!is_wired_headset_plugged && is_headset_plugged_now) {
							// 接続された
							is_wired_headset_plugged = true;
							Toast.makeText(micapp_activity, "ヘッドセットイベント：接続されました (State=" + state + " HeadsetOn=" + is_wired_headset_on + ")", Toast.LENGTH_SHORT).show();
						} else if (is_wired_headset_plugged && !is_headset_plugged_now) {
							// 取り外された
							is_wired_headset_plugged = false;
							Toast.makeText(micapp_activity, "ヘッドセットイベント：取り外されました (State=" + state + " HeadsetOn=" + is_wired_headset_on + ")", Toast.LENGTH_SHORT).show();
						} else {
							// 状態変化なし
							Toast.makeText(micapp_activity, "ヘッドセットイベント：状態変化なし (State=" + state + " HeadsetOn=" + is_wired_headset_on + ")", Toast.LENGTH_SHORT).show();
							return;
						}

						update_speakerphone();
					}
				};

				IntentFilter intent_filter = new IntentFilter(Intent.ACTION_HEADSET_PLUG);
				registerReceiver(headset_monitering_bc_recver, intent_filter);
			}
		} else if (radio_id == R.id.HeadsetMoniteringMethodRadioButton_Polling) {
			if (headset_monitering_bc_recver != null) {
				unregisterReceiver(headset_monitering_bc_recver);
				headset_monitering_bc_recver = null;

				// ヘッドセット監視スレッドを開始する
				if (!headset_moniter_thread_loop) {

					headset_moniter_thread_loop = true;

					(new Thread(new Runnable() {
						@Override
						public void run() {
							Log.d("sakalog", "headset monitoring thread: hello.");

							while (headset_moniter_thread_loop) {

								boolean is_wired_headset_on = audio_manager.isWiredHeadsetOn();

								if (!is_wired_headset_plugged && is_wired_headset_on) {
									// 接続された
									is_wired_headset_plugged = true;

									handler.post( new Runnable() {
										public void run() {
											Log.d("sakalog", "headset monitoring thread: headset connected.");
											Toast.makeText(micapp_activity, "ヘッドセットポーリング：接続されました", Toast.LENGTH_SHORT).show();
											update_speakerphone();
										}
									});
								} else if (is_wired_headset_plugged && !is_wired_headset_on) {
									// 取り外された
									is_wired_headset_plugged = false;

									handler.post( new Runnable() {
										public void run() {
											Log.d("sakalog", "headset monitoring thread: headset disconnected.");
											Toast.makeText(micapp_activity, "ヘッドセットポーリング：取り外されました", Toast.LENGTH_SHORT).show();
											update_speakerphone();
										}
									});
								} else {
									// 状態変化なし
								}

								try {
									Thread.sleep(200, 0);
								} catch (InterruptedException e) {
								}
							}

							Log.d("sakalog", "headset monitoring thread: bye.");
						}
					})).start();
				}
			}
		}
	}

	private void update_speakerphone()
	{
		boolean speaker_on = audio_manager.isSpeakerphoneOn();
		boolean restart = false;

		if ((auto_speaker_off_checkbox.isChecked() && is_wired_headset_plugged && speaker_on && audio_io_auto_restart_speaker_checkbox.isChecked()) ||
				(auto_speaker_on_checkbox.isChecked() && !is_wired_headset_plugged && !speaker_on && audio_io_auto_restart_speaker_checkbox.isChecked()) ||
				audio_io_auto_restart_headset_checkbox.isChecked()) {
			if (audio_io_running) {
				toggle_start_stop();
				while (audio_io_running) {
					try {
						Thread.sleep(0, 1000);  //wait for 1msec
					} catch (InterruptedException e) {
					}
				}
				restart = true;
			}
				}
		if (auto_speaker_off_checkbox.isChecked() && is_wired_headset_plugged && speaker_on) {
			Log.d("sakalog", "setSpeakerphoneOn(false) 1");
			audio_manager.setSpeakerphoneOn(false);
		}
		if (auto_speaker_on_checkbox.isChecked() && !is_wired_headset_plugged && !speaker_on) {
			Log.d("sakalog", "setSpeakerphoneOn(true) 2");
			audio_manager.setSpeakerphoneOn(true);
		}
		if (restart) {
			toggle_start_stop();
		}

		update_view();
	}

	/**
	 * ラジオボタンで現在選択されているオーディオストリームを取得する
	 */
	private int get_audio_stream_type() 
	{
		int audio_stream_type = -1;
		int radio_id = audio_stream_type_radio_group.getCheckedRadioButtonId();
		if (radio_id == R.id.AudioStreamTypeRadioButton_VoiceCall) {
			audio_stream_type = AudioManager.STREAM_VOICE_CALL;
		} else if (radio_id == R.id.AudioStreamTypeRadioButton_Music) {
			audio_stream_type = AudioManager.STREAM_MUSIC;
		} else {
			audio_stream_type = AudioManager.STREAM_RING;
		}
		return audio_stream_type;
	}

	/**
	 * ラジオボタンで現在選択されているオーディオストリームに合わせて、
	 * ボリュームのシークバーの表示を更新する
	 */
	private void update_volume_seek_bar() 
	{
		int audio_stream_type = get_audio_stream_type();
		int max_volume = audio_manager.getStreamMaxVolume(audio_stream_type);
		int volume = audio_manager.getStreamVolume(audio_stream_type);
		//audio_manager.setStreamVolume(audio_stream_type, volume, 0);
		volume_seek_bar.setMax(max_volume);
		volume_seek_bar.setProgress(volume);
		volume_seek_bar_status_view.setText("音量：現在値 " + volume + "  最大値 " + max_volume + " ");
	}

	/**
	 * ホワイトノイズの出力を開始・終了する
	 * ホワイトノイズの出力は、オーディオ入出力が動作中のときのみ可能
	 */
	private void toggle_white_noise_on_off()
	{
		synchronized (this) {
			if (audio_io_running) {
				white_noise_on = !white_noise_on;
			}
		}
	}

	/**
	 * オーディオ入出力を開始・終了する
	 */
	private void toggle_start_stop()
	{
		synchronized (this) {
			if (!audio_io_running) {

				// 内部フラグを更新して、テキストビュー、ボタン、ラジオボタン、シークバーの表示を更新する
				audio_io_running = true;
				audio_io_thread_loop = true;
				// オーディオ入出力が動作中は、オーディオストリーム選択のラジオボタンを変更不可にする
				((RadioButton) findViewById(R.id.AudioStreamTypeRadioButton_Music)).setEnabled(false); 
				((RadioButton) findViewById(R.id.AudioStreamTypeRadioButton_VoiceCall)).setEnabled(false); 
				((RadioButton) findViewById(R.id.AudioStreamTypeRadioButton_Ring)).setEnabled(false); 
				update_view();

				// オーディオ入出力スレッドを開始する
				(new Thread(new Runnable() {
					@Override
					public void run() {
						int freq = 0;
						//short[] buf = buf_short;
						byte[] buf = buf_byte;

						handler.post( new Runnable() {
							public void run() {
								Toast.makeText(micapp_activity, "オーディオ入出力を開始します", Toast.LENGTH_SHORT).show();
							}
						});

						String err = null;

						// ホワイトノイズ用の乱数生成器
						Random rand = new Random();

						while (audio_io_thread_loop) {
							freq = 0;

							// オーディオ入出力の開始
							do {
								if (audio_record == null) {
									// 2013.6.30
									// ELUGA PでMODE_IN_COMMUNICATIONセット時、AudioRecordからの音のレベルが0(無音)になる
									// MediaRecorder.AudioSource.MIC > VOICE_COMMUNICATION に変更
									audio_record = new AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION,
											sample_rate,
											AudioFormat.CHANNEL_IN_MONO,
											AudioFormat.ENCODING_PCM_16BIT,
											in_buffsize);
									if (audio_record == null) {
										err = "AudioRecord is null";
										audio_io_thread_loop = false;
									} else {
										try {
											audio_record.startRecording();
											Log.d("sakalog", "startRecording ok.");
										} catch (IllegalStateException e) {
											Log.d("sakalog", "startRecording error.");
											err = "startRecording error";
											audio_io_thread_loop = false;
											break;
										}
									}
								}
								if (audio_track == null && audio_io_thread_loop) {
									audio_track = new AudioTrack(get_audio_stream_type(),
											sample_rate,
											AudioFormat.CHANNEL_OUT_MONO,
											AudioFormat.ENCODING_PCM_16BIT,
											out_buffsize,
											AudioTrack.MODE_STREAM);
									if (audio_track == null) {
										err = "AudioTrack is null";
										audio_io_thread_loop = false;
									} else {
										try {
											audio_track.play();
											Log.d("sakalog", "play ok.");
										} catch (IllegalStateException e) {
											Log.d("sakalog", "play error.");
											err = "play error";
											audio_io_thread_loop = false;
											break;
										}
									}
								}
							} while (false);

							// スレッド終了指示されるまで、ひたすらオーディオ入力からのオーディオデータをオーディオ出力に回す
							// ホワイトノイズが開始されたら、オーディオ入力は無視しして、ホワイトノイズをオーディオ出力に回す
							while (audio_io_thread_loop) {
								int read_size = 0;
								if (!white_noise_on) {
									read_size = audio_record.read(buf, 0, buf.length);
									if (read_size > 0) {
										audio_read_bytes += read_size;
										audio_write_bytes += audio_track.write(buf, 0, read_size);
									} else {
									}
								} else {
									for (int i=0; i<buf.length; i++) {
										//buf[i] = (short)rand.nextInt(0xffff);
										buf[i] = (byte)rand.nextInt(0xff);
									}
									read_size = buf.length;
									audio_write_bytes += audio_track.write(buf, 0, read_size);
								}
								freq += read_size;
								int sleep_for_ns = read_size*(1000000/44100);
								if (sleep_for_ns == 0) {
									// 他のアプリがマイクをオープンしているときはリードできないっぽい（０バイト）
									sleep_for_ns = buf.length*(1000000/44100);
								}
								try {
									Thread.sleep(0, sleep_for_ns);
								} catch (InterruptedException e) {
									audio_io_thread_loop = false;
									break;
								}

								// 0.5秒おきに出力総バイト数の表示を更新
								if (freq >= (sample_rate/2)) {
									freq = 0;
									String byte0 = Integer.toHexString((int)buf[0]);
									String byte1 = Integer.toHexString((int)buf[1]);
									Log.d("sakalog", "byte[0]=" + byte0 + " byte[1]=" + byte1);
									handler.post( new Runnable() {
										public void run() {
											update_view();
										}
									});
								}
							}

							// オーディオ入出力を終了する
							if (audio_record != null) {
								try {
									audio_record.stop();
									audio_record.release();
									Log.d("sakalog", "AudioReord stop&release ok.");
								} catch (IllegalStateException e) {
									Log.d("sakalog", "AudioReord stop&release error.");
								}
							}
							if (audio_track != null) {
								try {
									audio_track.stop();
									audio_track.release();
									Log.d("sakalog", "AudioTrack stop&release ok.");
								} catch (IllegalStateException e) {
									Log.d("sakalog", "AudioTrack stop&release error.");
								}
							}
							audio_record = null;
							audio_track = null;

						}

						audio_io_running = false;

						// オーディオ入出力終了時に、テキストビュー、ボタン、ラジオボタン、シークバーを更新する
						// オーディオストリーム選択のラジオボタンは変更可能に戻す
						// これらの処理はオーディオ入出力スレッドのコンテキストでは実行不可であるため、
						// アプリのUIスレッドに処理を委任しないといけない
						if (err != null) {
							toast_msg = "オーディオ入出力は異常で停止しました (" + err + ")";
						} else {
							toast_msg = "オーディオ入出力は正常に停止しました";
						}
						handler.post( new Runnable() {
							public void run() {
								update_view();
								((RadioButton) findViewById(R.id.AudioStreamTypeRadioButton_Music)).setEnabled(true); 
								((RadioButton) findViewById(R.id.AudioStreamTypeRadioButton_VoiceCall)).setEnabled(true); 
								((RadioButton) findViewById(R.id.AudioStreamTypeRadioButton_Ring)).setEnabled(true); 
								Toast.makeText(micapp_activity, toast_msg, Toast.LENGTH_SHORT).show();
							}
						});
					}
				})).start();
			} else {
				// オーディオ入出力スレッドを終了させる
				audio_io_thread_loop = false;
			}
		}
	}
}
