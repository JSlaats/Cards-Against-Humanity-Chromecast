package com.jelleslaats.freakyfriday;

import com.google.android.gms.cast.Cast;
import com.google.android.gms.cast.CastDevice;
import com.google.android.gms.cast.framework.CastSession;
import com.google.android.gms.cast.framework.SessionManager;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;


public abstract class MessageStream implements Cast.MessageReceivedCallback{
	private static final String TAG = MessageStream.class.getSimpleName();

	private static final String GAME_NAMESPACE = "urn:x-cast:com.jelleslaats.freakyfriday";
	//region keys
	// Json keys
	private static final String KEY_TYPE = "type";
	private static final String KEY_NAME = "name";
	private static final String KEY_CARD_ID_ARRAY = "cardIDs";
	private static final String KEY_CHOSEN_WINNER = "winningPlayerID";
	private static final String KEY_SUBMISSION_THAT_WAS_READ = "cardIDs";
	private static final String KEY_PLAYER_ID = "number";
	private static final String KEY_PLAYER_OBJECT = "player";
	private static final String KEY_JUDGE_ID = "judge";
	private static final String KEY_RESPONSES_ARRAY = "responses";
	private static final String KEY_PROMPT_STRING = "prompt";
	private static final String KEY_NUM_OF_BLANKS = "numOfBlanks";
	private static final String KEY_RESPONSE_CODE = "code";

	// Commands to send to server
	private static final String KEY_JOIN = "join";
	private static final String KEY_LEAVE = "leave";
	private static final String KEY_UPDATE_SETTINGS = "updateSettings";
	private static final String KEY_SUBMIT_CARD = "playSubmission";
	private static final String KEY_SUBMIT_WINNER = "submissionsJudged";
	private static final String KEY_HAVE_READ_SUBMISSION = "submissionRead";
	private static final String KEY_START_NEXT_ROUND = "nextRound";

	// Events to receive from server
	private static final String KEY_USER_QUEUED = "didQueue";
	private static final String KEY_USER_JOINED = "didJoin";
	private static final String KEY_JUDGING_MODE_STARTED = "judging";
	private static final String KEY_GAMESYNC = "gameSync";
	private static final String KEY_YOU_ARE_JUDGE = "judgeSubmissions";
	private static final String KEY_ROUND_HAS_STARTED = "roundStarted";
	private static final String KEY_ROUND_HAS_ENDED = "roundEnded";
	private static final String KEY_SERVER_RESPONSE = "response";

	// Error codes
	static final int ERROR_SENT_INVALID_MESSAGE_TYPE = -1;
	static final int ERROR_WRONG_NUMBER_OF_CARDS = 1;
	static final int ERROR_INVALID_WINNER_SUBMITTED = 2;
	static final int ERROR_JUDGED_BEFORE_CARDS_WERE_READ = 3;
	static final int ERROR_TRIED_TO_JOIN_WITH_BLANK_NAME = 4;
	static final int ERROR_TRIED_TO_START_ROUND_WHILE_ROUND_EXISTS = 5;
	static final int ERROR_TRIED_TO_START_ROUND_INSUFFICIENT_PLAYERS = 6;
	//endregion
	private SessionManager mSessionManager;
	private CastSession mCastSession;


	public MessageStream(SessionManager sessionManager) {
		mSessionManager = sessionManager;
		mCastSession = sessionManager.getCurrentCastSession();
	}

	public static String getGameNamespace() {
		return GAME_NAMESPACE;
	}

	private void sendMessage(JSONObject payload){
		mCastSession = mSessionManager.getCurrentCastSession();
		if(mCastSession != null) {
			mCastSession.sendMessage(GAME_NAMESPACE, payload.toString());
		}else{
			Log.d(TAG,"No session active");
		}
	}

	public final void joinGame(String name){
		try {
			Log.d(TAG, "join: " + name);
			JSONObject payload = new JSONObject();
			payload.put(KEY_TYPE, KEY_JOIN);
			payload.put(KEY_NAME, name);
			sendMessage(payload);

		}
		catch (JSONException e) {
			Log.e(TAG, "Cannot create object to join a game", e);
		} catch (IllegalStateException e) {
			Log.e(TAG, "Message Stream is not attached", e);
		}
	}
	
	public final void leaveGame(){
		try {
			Log.d(TAG, "leaving");
			JSONObject payload = new JSONObject();
			payload.put(KEY_TYPE, KEY_LEAVE);
			sendMessage(payload);
		}
		catch (JSONException e) {
			Log.e(TAG, "Cannot create object to leave a game", e);
		} catch (IllegalStateException e) {
			Log.e(TAG, "Message Stream is not attached", e);
		}
	}

	public final void updateSettings(String name){
		try {
			Log.d(TAG, "updateSettings: " + name);
			JSONObject payload = new JSONObject();
			payload.put(KEY_TYPE, KEY_UPDATE_SETTINGS);
			payload.put(KEY_NAME, name);
			sendMessage(payload);
		}
		catch (JSONException e) {
			Log.e(TAG, "Cannot create object to update settings", e);
		} catch (IllegalStateException e) {
			Log.e(TAG, "Message Stream is not attached", e);
		}
	}

	public final void submitResponse(int[] cardIDs){
		// build responses JSONArray
		JSONArray responses = new JSONArray();
		for(int i = 0; i < cardIDs.length; ++i){
			responses.put(cardIDs[i]);
		}

		try{
			// submit
			JSONObject payload = new JSONObject();
			payload.put(KEY_TYPE, KEY_SUBMIT_CARD);
			payload.put(KEY_CARD_ID_ARRAY, responses);
			sendMessage(payload);
		}
		catch (JSONException e) {
			Log.e(TAG, "Cannot create object to submit response", e);
		} catch (IllegalStateException e) {
			Log.e(TAG, "Message Stream is not attached", e);
		}
	}
	
	public final void declareWinner(int winnerID){
		try{
			JSONObject payload = new JSONObject();
			payload.put(KEY_TYPE, KEY_SUBMIT_WINNER);
			payload.put(KEY_CHOSEN_WINNER, winnerID);
			sendMessage(payload);
		}
		catch (JSONException e) {
			Log.e(TAG, "Cannot create object to declare a winner", e);
		} catch (IllegalStateException e) {
			Log.e(TAG, "Message Stream is not attached", e);
		}
	}
	
	public final void readSubmission(int[] cardsRead){
		try{
			JSONArray cardsJSON = new JSONArray();
			for(int i = 0; i < cardsRead.length; ++i){
				cardsJSON.put(cardsRead[i]);
			}
			
			JSONObject payload = new JSONObject();
			payload.put(KEY_TYPE, KEY_HAVE_READ_SUBMISSION);
			payload.put(KEY_SUBMISSION_THAT_WAS_READ, cardsJSON);
			sendMessage(payload);
		}
		catch (JSONException e) {
			Log.e(TAG, "Cannot create object to send which submissions the judge has read", e);
		} catch (IllegalStateException e) {
			Log.e(TAG, "Message Stream is not attached", e);
		}
	}
	
	public final void startNextRound(){
		Log.d(TAG, "trying to start next round");
		try{
			JSONObject payload = new JSONObject();
			payload.put(KEY_TYPE, KEY_START_NEXT_ROUND);
			sendMessage(payload);
		}
		catch (JSONException e) {
			Log.e(TAG, "Cannot create object to start next round", e);
		} catch (IllegalStateException e) {
			Log.e(TAG, "Message Stream is not attached", e);
		}
	}

	@Override
	public void onMessageReceived(CastDevice castDevice, String namespace, String msg) {
		try {
			Log.d(TAG, "onMessageReceived: " + msg);
			JSONObject message = new JSONObject(msg);

			if (message.has(KEY_TYPE)) {
				String event = message.getString(KEY_TYPE);

				// if we're getting confirmation that we're queued
				switch (event) {
					case KEY_USER_QUEUED:
						Log.d(TAG, "Confirmed enqueued");
						onPlayerQueued();
						break;

					// if we're getting confirmation that we've joined
					case KEY_USER_JOINED:
						Log.d(TAG, "Confirmed joined");
						try {
							int newID = message.getInt(KEY_PLAYER_ID);
							onPlayerJoined(newID);
						} catch (JSONException e) {
							e.printStackTrace();
						}
						break;

					// if the judging period is starting
					case KEY_JUDGING_MODE_STARTED:
						Log.d(TAG, "Judging mode starting");
						onJudgeModeStarted();
						break;

					// if we're receiving a game state update (gameSync)
					case KEY_GAMESYNC:
						Log.d(TAG, "GameSync");
						try {
							JSONObject thisPlayer = message.getJSONObject(KEY_PLAYER_OBJECT);
							int newJudge = message.getInt(KEY_JUDGE_ID);
							onGameSync(thisPlayer, newJudge);
						} catch (JSONException e) {
							e.printStackTrace();
						}
						break;

					// if we're receiving an array of responses to judge
					case KEY_YOU_ARE_JUDGE:
						Log.d(TAG, "You are judge");
						try {
							JSONArray responses = message.getJSONArray(KEY_RESPONSES_ARRAY);
							onJudgeResponses(responses);
						} catch (JSONException e) {
							e.printStackTrace();
						}
						break;

					// if we're receiving a new prompt for a new round
					case KEY_ROUND_HAS_STARTED:
						Log.d(TAG, "Round started");
						try {
							String newPrompt = message.getString(KEY_PROMPT_STRING);
							int numOfBlanks = message.getInt(KEY_NUM_OF_BLANKS);
							onRoundStarted(newPrompt, numOfBlanks);
						} catch (JSONException e) {
							e.printStackTrace();
						}
						break;

					// if we're receiving notice that a round has ended
					case KEY_ROUND_HAS_ENDED:
						Log.d(TAG, "Round ended");
						onRoundEnded();
						break;

					// if we're receiving a response message (possible error)
					case KEY_SERVER_RESPONSE:
						Log.d(TAG, "Response received");
						try {
							int responseCode = message.getInt(KEY_RESPONSE_CODE);
							if (responseCode != 0) {
								onServerError(responseCode);

							}
						} catch (JSONException e) {
							e.printStackTrace();
						}
						break;
				}
			}
			else {
				Log.w(TAG, "Unknown message (no type): " + message);
			}
		}
		catch (JSONException e) {
			Log.w(TAG, "Message doesn't contain an expected key.", e);
		}
	}

	protected abstract void onPlayerQueued();
	protected abstract void onPlayerJoined(int newID);
	protected abstract void onJudgeModeStarted();
	protected abstract void onGameSync(JSONObject player, int newJudge);
	protected abstract void onJudgeResponses(JSONArray responses);
	protected abstract void onRoundStarted(String newPrompt, int numOfBlanks);
	protected abstract void onRoundEnded();
	protected abstract void onServerError(int errorCode);
}
