package com.jelleslaats.freakyfriday;

import java.io.IOException;
import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;

import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import com.google.android.gms.cast.framework.CastButtonFactory;
import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.cast.framework.CastSession;
import com.google.android.gms.cast.framework.Session;
import com.google.android.gms.cast.framework.SessionManager;
import com.google.android.gms.cast.framework.SessionManagerListener;


public class MainActivity extends AppCompatActivity {
	private static final String TAG = MainActivity.class.getSimpleName();

	private CastSession mCastSession;
	private SessionManager mSessionManager;
	private MsgStreamImp mGameMessageStream;
	private final SessionManagerListener mSessionListener = new SessionManagerListenerImpl();

	// UI elements
	private TextView bigStatus, promptDisplay, judgeBigStatus;
	private Button nextRoundButton;
	private Button sendCardButton;
	private ListView cardList;
	private RelativeLayout cardListHolder;

	// Colors
	private static final int BACKGROUND_ERROR         = 0xFF800000;
	private static final int BACKGROUND_SUCCESS       = 0xFF006600;
	private static final int BACKGROUND_SELECTED_CARD = 0xFFFFFFCC;

	// Game state
	private int playerID = -1;
	private String playerName = null;
	private Card[] hand;
	private int judgeID = -1;
	private ArrayList<Integer> selectedCards = new ArrayList<Integer>();
	private ArrayList<Response> roundResponses = new ArrayList<Response>();
	private int numOfResponses;
	private boolean judgeMode = false;

	// Constants
	private static final String PREF_FILE = "myPreferences";

	/**
	 * Called when the activity is first created. Initializes the game with necessary listeners
	 * for player interaction, and creates a new message stream.
	 */
	@Override
	public void onCreate(Bundle bundle) {
		super.onCreate(bundle);
		setContentView(R.layout.activity_main);

		mSessionManager = CastContext.getSharedInstance(this).getSessionManager();
		mGameMessageStream = new MsgStreamImp();

		//UI
		bigStatus = findViewById(R.id.big_status);
		promptDisplay = findViewById(R.id.prompt_text);
		judgeBigStatus = findViewById(R.id.judge_big_status);
		nextRoundButton = findViewById(R.id.button_next_round);
		sendCardButton = findViewById(R.id.button_send_cards);
		cardList = findViewById(R.id.card_list);
		cardListHolder = findViewById(R.id.card_list_holder);

		// listeners
		nextRoundButton.setOnClickListener(new Button.OnClickListener(){
			public void onClick(View v){
				mGameMessageStream.startNextRound();
			}
		});
		sendCardButton.setOnClickListener(new Button.OnClickListener(){
			public void onClick(View v){
				trySubmitCards();
			}
		});

		loadPlayerName();
		initScreen();
	}

	private void initScreen(){
		bigStatus.setText(R.string.choose_chromecast);
		bigStatus.setVisibility(View.VISIBLE);
	}

	private void resetGameState(){
		selectedCards.clear();
		roundResponses.clear();
		numOfResponses = 0;
	}

	/**
	 * Called when the options menu is first created.
	 */
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		getMenuInflater().inflate(R.menu.main, menu);
		CastButtonFactory.setUpMediaRouteButton(getApplicationContext(),
				menu,
				R.id.media_route_menu_item);

		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item){
		if (item.getItemId() == R.id.set_name_media_item) {
			updatePlayerName();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}


	/**
	 * Removes the activity from memory when the activity is paused.
	 */
	@Override
	protected void onPause() {
		super.onPause();
		mSessionManager.removeSessionManagerListener(mSessionListener);
		mCastSession = null;
	}

	@Override
	protected void onResume() {
		mCastSession = mSessionManager.getCurrentCastSession();
		mSessionManager.addSessionManagerListener(mSessionListener);
		super.onResume();
	}

	/**
	 * Attempts to end the current game session when the activity stops.
	 */
	@Override
	protected void onStop() {
		if (mSessionManager != null){
			if(mGameMessageStream != null) {
				mGameMessageStream.leaveGame();
				mSessionManager.endCurrentSession(true);
			}
		}
		super.onStop();
	}


	public void showErrorMessage(String messageText){
		new AlertDialog.Builder(this)
		.setTitle("Uh oh!")
		.setMessage(messageText)
		.setPositiveButton("Dismiss", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) { 
			}
		})
		.show();
	}

	public void showJudgeInstructions(){
		new AlertDialog.Builder(this)
		.setTitle("You Are Judging!")
		.setMessage("Choose the response you think should win this round.")
		.setPositiveButton("Dismiss", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) { 
			}
		})
		.show();
	}

	public void showJudgeUI(){
		judgeMode = true;
		sendCardButton.setVisibility(View.VISIBLE);
		cardListHolder.setVisibility(View.VISIBLE);
	}

	private class SessionManagerListenerImpl implements SessionManagerListener {

		@Override
		public void onSessionStarted(Session session, String sessionId) {
			invalidateOptionsMenu();
			Log.i(TAG, "onSessionStarted");

			mCastSession = mSessionManager.getCurrentCastSession();
			if (mCastSession == null) {
				Log.w(TAG, "onSessionStarted: mCastSession is null");
				return;
			}
			try {
				mCastSession.setMessageReceivedCallbacks(MessageStream.getGameNamespace(),mGameMessageStream);
			} catch (IOException e) {
				e.printStackTrace();
			}
			Log.i(TAG, "onSessionStarted requestPlayerNameThenJoinGame");

			requestPlayerNameThenJoinGame();
		}
		//region overrides
		@Override
		public void onSessionStarting(Session session) {
			Log.i(TAG, "onSessionStarting");
		}
		@Override
		public void onSessionStartFailed(Session session, int i) {
			Log.i(TAG, "onSessionStartFailed");
		}

		@Override
		public void onSessionEnding(Session session) {
			Log.i(TAG, "onSessionEnding");
		}

		@Override
		public void onSessionResumed(Session session, boolean wasSuspended) {
			invalidateOptionsMenu();
			Log.i(TAG, "onSessionResumed");
		}

		@Override
		public void onSessionResumeFailed(Session session, int i) {
			Log.i(TAG, "onSessionResumeFailed");
		}

		@Override
		public void onSessionSuspended(Session session, int i) {
			Log.i(TAG, "onSessionSuspended");
		}

		@Override
		public void onSessionEnded(Session session, int error) {
			Log.i(TAG, "onSessionEnded");
			finish();
		}

		@Override
		public void onSessionResuming(Session session, String s) {
			Log.i(TAG, "onSessionResuming");
		}
		//endregion
	}

	// validate selection, then submit cards
	private void trySubmitCards(){
		if(!judgeMode){
			if(numOfResponses == selectedCards.size()){
				int[] submissionIDs = new int[numOfResponses];
				for(int i = 0; i < numOfResponses; ++i){
					submissionIDs[i] = selectedCards.get(i);
				}
				Log.d(TAG, "Submitting " + numOfResponses + " cards");
				mGameMessageStream.submitResponse(submissionIDs);

				submittedUI();
			}
			else{
				Log.i(TAG, "Tried to submit " + selectedCards.size() + " responses for a prompt that wants " + numOfResponses);
				showErrorMessage("This prompt requires exactly " + numOfResponses + " card(s).\n(You tried to play " + selectedCards.size() +")");
			}
		}
		else{
			// choose winner
			if(selectedCards.size() > 1){
				Log.e(TAG, "More than one winner was in array!");
				showErrorMessage("You're trying to declare more than one winning card. That's bad.");
				return;
			}
			else if(selectedCards.size() < 1){
				Log.e(TAG, "No winner was selected!");
				showErrorMessage("You need to select a winner!");
				return;
			}
			Log.i(TAG, "Declaring user #" + selectedCards.get(0) + " as winner");
			mGameMessageStream.declareWinner(selectedCards.get(0));
			judgeMode = false;
		}
	}

	private void submittedUI(){
		Log.i(TAG, "submittedUI()");
		bigStatus.setText("Waiting on everyone else\nto submit their cards.");
		hideUIShowBigStatus();
	}

	private void hideUIShowBigStatus(){
		Log.i(TAG, "hideUIShowBigStatus()");
		cardListHolder.setVisibility(View.GONE);
		sendCardButton.setVisibility(View.GONE);
		bigStatus.setVisibility(View.VISIBLE);
	}

	private void savePlayerName(String name){
		SharedPreferences settings = getSharedPreferences(PREF_FILE, 0);
		SharedPreferences.Editor editor = settings.edit();
		playerName = name;
		editor.putString("name", name);
		editor.apply();
	}

	private void loadPlayerName(){
		SharedPreferences settings = getSharedPreferences(PREF_FILE, 0);
		playerName = settings.getString("name", null);
	}

	private void requestPlayerNameThenJoinGame(){
		Log.i(TAG, "Requesting player name");

		// only request name if not set
		if(playerName != null){
			mGameMessageStream.joinGame(playerName);
			return;
		}

		AlertDialog.Builder alert = new AlertDialog.Builder(this);

		alert.setTitle("Enter Player Name");
		alert.setMessage("Set your player name to be displayed on the Chromecast.");
		final EditText input = new EditText(this);

		alert.setView(input);

		alert.setPositiveButton("Set Name", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton) {
				// check for blank name
				String newName = input.getText().toString();

				// keep asking until a name is supplied
				if(newName.length() == 0){
					showErrorMessage("You've got to supply a name!");
					requestPlayerNameThenJoinGame();
					return;
				}

				savePlayerName(newName);
				Toast.makeText(getApplicationContext(), "Name set to " + playerName, Toast.LENGTH_LONG).show();
				mGameMessageStream.joinGame(playerName);
			}
		});

		alert.create();
		alert.show();
	}

	private void updatePlayerName(){
		Log.i(TAG, "updatePlayerName");

		AlertDialog.Builder alert = new AlertDialog.Builder(this);

		alert.setTitle("Update Player Name");
		alert.setMessage("Set your player name to be displayed on the Chromecast.");
		final EditText input = new EditText(this);

		alert.setView(input);
		if(playerName != null){
			input.setText(playerName);
		}

		alert.setPositiveButton("Change Name", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton) {
				// check for blank name
				String newName = input.getText().toString();

				if(newName.length() == 0){
					Toast.makeText(getApplicationContext(), "Didn't update player name.", Toast.LENGTH_LONG).show();
					return;
				}

				if(newName.equals(playerName)){
					return;
				}

				savePlayerName(newName);
				Toast.makeText(getApplicationContext(), "Name updated to " + playerName, Toast.LENGTH_LONG).show();

				mGameMessageStream.updateSettings(playerName);
			}
		});

		alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton) {
			}
		});

		alert.create();
		alert.show();
	}

	// update card list with contents of hand, and if any cards are selected highlight them.
	// also update the submit button's contents but don't change its visibility.
	private void updateHandDisplay(){
		Log.i(TAG, "selected cards contains this many items: " + selectedCards.size());

		if(!judgeMode){
			String[] prompts = new String[hand.length];
			for(int i = 0; i < hand.length; ++i){
				prompts[i] = hand[i].toString();
			}

			// populate ListView
			setCardListContents(prompts);

			// set button
			if(selectedCards.size() > numOfResponses){
				int diff = selectedCards.size() - numOfResponses;
				String label = "Select " + Math.abs(diff) + " Less Card";
				if(Math.abs(diff) != 1){
					label += "s";
				}

				sendCardButton.setText(label);
				sendCardButton.setBackgroundColor(BACKGROUND_ERROR);
			}
			else if(selectedCards.size() < numOfResponses){
				int diff = selectedCards.size() - numOfResponses;
				String label = "Select " + Math.abs(diff) + " More Card";
				if(Math.abs(diff) != 1){
					label += "s";
				}

				sendCardButton.setText(label);
				sendCardButton.setBackgroundColor(BACKGROUND_ERROR);
			}
			else{
				String label = "Play " + numOfResponses + " Card";

				if(numOfResponses != 1){
					label += "s";
				}

				sendCardButton.setText(label);
				sendCardButton.setBackgroundColor(BACKGROUND_SUCCESS);
			}
		}
		else{
			if(selectedCards.size() == 1){
				String label = "Choose Winner";
				sendCardButton.setText(label);
				sendCardButton.setBackgroundColor(BACKGROUND_SUCCESS);
			}
			else{
				String label = "Select a Winner";
				sendCardButton.setText(label);
				sendCardButton.setBackgroundColor(BACKGROUND_ERROR);
			}
		}
	}

	private void setCardListContents(String[] contents){
		MyAdapter adapter = new MyAdapter(this, R.layout.list_card, contents);
		cardList.setAdapter(adapter);
		cardList.setOnItemClickListener(mMessageClickedHandler);
	}

	public class MyAdapter extends ArrayAdapter<String>{

		Context thisContext;

		public MyAdapter(Context context, int resource, String[] objects) {
			super(context, resource, objects);
			thisContext = context;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			View view = super.getView(position, convertView, parent);

			if(judgeMode){
				if(selectedCards.size() > 0){
					Log.i(TAG, roundResponses.get(position).owner + " = " + selectedCards.get(0) + "? " + (roundResponses.get(position).owner == selectedCards.get(0)));
				}
			}

			view.setBackgroundColor(0xFFFFFFFF);
			
			if (judgeMode){
				if(selectedCards.size() > 0){
					if(roundResponses.get(position).owner == selectedCards.get(0)){
						Log.i(TAG, "Setting background color of selected winning card");
						view.setBackgroundColor(BACKGROUND_SELECTED_CARD);
					}
				}
			}
			if(!judgeMode){
				if(!judgeMode && selectedCards.contains(hand[position].id)) {
					Log.i(TAG, "Setting background color of selected card to be played");
					view.setBackgroundColor(BACKGROUND_SELECTED_CARD);
				}
			}

			return view;
		}

	}

	// Create a message handling object as an anonymous class.
	private OnItemClickListener mMessageClickedHandler = new OnItemClickListener() {
		@Override
		public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
			Log.i(TAG, "Clicked item #" + position);
			if(!judgeMode){
				Card thisCard = hand[position];

				// if selected, deselect and vice versa
				if(selectedCards.contains(thisCard.id)){
					selectedCards.remove((Integer) thisCard.id);
				}
				else{
					selectedCards.add(thisCard.id);
				}

				updateHandDisplay();
			}
			else{ // select a winning response
				if(selectedCards.size() > 0){
					selectedCards.clear();
				}

				int responseOwner = roundResponses.get(position).owner;
				selectedCards.add(responseOwner);

				updateHandDisplay();
			}
		}
	};


	private class MsgStreamImp extends MessageStream {

		MsgStreamImp() {
			super(mSessionManager);
		}

		// Player is queued. Update UI to notify them.
		protected void onPlayerQueued(){
			bigStatus.setText(R.string.player_queued_notice);
			nextRoundButton.setVisibility(View.VISIBLE);
		}

		// Player has joined. Store ID internally and update UI.
		protected void onPlayerJoined(int newID){
			if(newID < 0){
				Log.w(TAG, "Got negative playerID (" + newID + ")");
				showErrorMessage("Received a negative playerID... that's bad.");
			}
			playerID = newID;
			onRoundEnded();
		}

		// The game has entered judge mode. Update UI.
		protected void onJudgeModeStarted(){
			judgeBigStatus.setVisibility(View.GONE);
			if(playerID == judgeID){
				// let onJudgeResponses handle it
				return;
			}

			bigStatus.setText(R.string.waiting_for_judge);
			hideUIShowBigStatus();
		}

		// Update game state with server information
		protected void onGameSync(JSONObject player, int newJudge){
			judgeID = newJudge;
			try {
				playerID = player.getInt("ID");
				JSONArray handObject = (JSONArray) player.get("hand");

				Card[] newHand = new Card[handObject.length()];

				for(int i = 0; i < handObject.length(); ++i){
					int cardID = handObject.getJSONObject(i).getInt("ID");
					String cardText = handObject.getJSONObject(i).getString("text");
					newHand[i] = new Card(cardID, cardText);
				}

				String newHandLog ="newHand:\n";
				for(int i = 0; i < newHand.length; ++i){
					newHandLog += newHand[i].toString() + "\n";
				}
				Log.d(TAG, newHandLog);

				hand = newHand;

				updateHandDisplay();
			}
			catch (JSONException e) {
				Log.e(TAG, "Couldn't get list of cards in gameSync.");
				showErrorMessage("Couldn't get your updated hand from the server.");
			}
		}

		// Player is judging responses. Display responses and handle submission.
		protected void onJudgeResponses(JSONArray responses){
			showJudgeInstructions();

			try{
				// populate table with responses instead of hand
				ArrayList<Response> responseList = new ArrayList<Response>();
				for(int i = 0; i < responses.length(); ++i){
					JSONObject thisResponseJSONObject = (JSONObject) responses.get(i);
					int thisOwner = thisResponseJSONObject.getInt("submitter");

					JSONArray thisCardsArray = (JSONArray) thisResponseJSONObject.get("cards");
					Card[] theseCards = new Card[thisCardsArray.length()];
					int[] theseCardIDs = new int[thisCardsArray.length()];
					for(int j = 0; j < thisCardsArray.length(); ++j){
						JSONObject thisCardJSONObject = (JSONObject) thisCardsArray.get(j);
						int thisCardId = thisCardJSONObject.getInt("ID");
						String thisCardPrompt = thisCardJSONObject.getString("text");
						theseCards[j] = new Card(thisCardId, thisCardPrompt);
						theseCardIDs[j] = thisCardId;
					}

					Response thisResponse = new Response(thisOwner, theseCards);
					Log.i(TAG, thisResponse.toString());
					responseList.add(thisResponse);
					roundResponses.add(thisResponse);

					// mark as read on server
					mGameMessageStream.readSubmission(theseCardIDs);
				}

				String[] responseStrings = new String[responseList.size()];
				for(int i = 0; i < responseList.size(); ++i){
					String tempString = "";
					for(int j = 0; j < responseList.get(i).contents.length; ++j){
						tempString += responseList.get(i).contents[j];

						if(j < (responseList.get(i).contents.length - 1)){
							tempString += "\n";
						}
					}

					responseStrings[i] = tempString;
				}

				showJudgeUI();
				setCardListContents(responseStrings);
			}
			catch (JSONException e){
				Log.e(TAG, "Couldn't get list of responses in onJudgeResponses.");
				showErrorMessage("There was a problem getting everyone's responses from the server.");
			}
		}

		// The round has started. Display the new prompt and the player's hand.
		protected void onRoundStarted(String newPrompt, int numOfBlanks){
			promptDisplay.setText(newPrompt);
			promptDisplay.setVisibility(View.VISIBLE);
			bigStatus.setVisibility(View.GONE);
			nextRoundButton.setVisibility(View.GONE);

			numOfResponses = numOfBlanks;
			updateHandDisplay();

			if(playerID == judgeID){
				Log.i(TAG, "I'm judge!");
				judgeBigStatus.setVisibility(View.VISIBLE);
				numOfResponses = 1;
				return;
			}

			Log.i(TAG, "Showing cardListHolder");
			sendCardButton.setVisibility(View.VISIBLE);
			cardListHolder.setVisibility(View.VISIBLE);
		}

		// The round has ended. Update UI.
		protected void onRoundEnded(){
			bigStatus.setText(R.string.waiting_for_round);
			bigStatus.setVisibility(View.VISIBLE);
			nextRoundButton.setVisibility(View.VISIBLE);
			sendCardButton.setVisibility(View.GONE);
			cardListHolder.setVisibility(View.GONE);
			promptDisplay.setVisibility(View.GONE);

			resetGameState();
		}

		// Some error code has been received. Let the user know.
		protected void onServerError(int errorCode){
			String messageText;
			switch(errorCode){
			case ERROR_SENT_INVALID_MESSAGE_TYPE:
				messageText = "Something went wrong with the code...";
				Log.e(TAG, "ERROR_SENT_INVALID_MESSAGE_TYPE");
				break;
			case ERROR_WRONG_NUMBER_OF_CARDS:
				messageText = "You tried to submit the wrong number of cards.";
				break;
			case ERROR_INVALID_WINNER_SUBMITTED:
				messageText = "You tried to declare yourself or a nonexistent player as winner.";
				break;
			case ERROR_JUDGED_BEFORE_CARDS_WERE_READ:
				messageText = "You tried to declare a winner before reading all the cards.";
				break;
			case ERROR_TRIED_TO_JOIN_WITH_BLANK_NAME:
				messageText = "You tried to join the game with a blank name.";
				break;
			case ERROR_TRIED_TO_START_ROUND_WHILE_ROUND_EXISTS:
				messageText = "You can't start a round while one is in progress.";
				break;
			case ERROR_TRIED_TO_START_ROUND_INSUFFICIENT_PLAYERS:
				messageText = "There aren't enough players to start the round yet.";
				break;
			default:
				messageText = "An unknown error occurred.";
			}

			showErrorMessage(messageText);
		}
	}
}