package io.nonostante.games.cordova;

import android.support.annotation.NonNull;
import android.util.Log;

import org.apache.cordova.*;
import org.json.*;

import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.ConnectionResult;

import android.content.Intent;

import com.google.android.gms.common.api.*;
import com.google.android.gms.games.achievement.*;
import com.google.android.gms.games.leaderboard.*;
import com.google.android.gms.games.*;

public class GameServices extends CordovaPlugin {
    private String LOG_TAG = "Game";
    private GameHelper mHelper;
    private boolean _connected = false;

    abstract class PluginResultCallbacks<R extends Result> extends ResultCallbacks<R> {
        private CallbackContext _context;

        PluginResultCallbacks(@NonNull CallbackContext context) {
            _context = context;
        }

        @Override
        public void onSuccess(@NonNull R result) {
            if (result instanceof Releasable) {
                try {
                    ((Releasable) result).release();
                } catch (Exception ex) {
                }
            }
        }

        @Override
        public void onFailure(@NonNull Status status) {
            Log.e(LOG_TAG, "Google Play Services: Error -> " + status.getStatusMessage());
            _context.error(getErrorJSON(ERROR.SERVICE_ERROR, status.getStatusCode(), status.getStatusMessage()));
            _context = null;

            if(status.getStatusCode() == GamesStatusCodes.STATUS_CLIENT_RECONNECT_REQUIRED){
                Log.d(LOG_TAG, "Google Play Services: Reconnecting");
                mHelper.reconnectClient();
            }
        }
    }

    abstract class ERROR {
        static final int SERVICE_UNAVAILABLE = 1;
        static final int NOT_SUPPORTED = 2;
        static final int NOT_CONNECTED = 3;
        static final int SERVICE_ERROR = 4;
        static final int LOGIN_PENDING = 5;
    }

    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
        cordova.setActivityResultCallback(this);
    }

    @Override
    public boolean execute(String action, JSONArray args, final CallbackContext callbackContext) throws JSONException {
        if (action.equals("login")) {
            cordova.getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    final GameHelper helper = getGameHelper();
                    if (helper.isConnecting()) {
                        Log.e(LOG_TAG, "Google Play Services: Login pending");
                        callbackContext.error(getErrorJSON(ERROR.LOGIN_PENDING, "Login Pending"));
                        return;
                    } else if (helper.isSignedIn()) {
                        Log.d(LOG_TAG, "Google Play Services: Logged already");
                        callbackContext.success(getPlayerDetailJSON());
                        return;
                    } else if (!helper.isSetupDone()) {
                        GoogleApiAvailability googleAPI = GoogleApiAvailability.getInstance();
                        int res = googleAPI.isGooglePlayServicesAvailable(cordova.getActivity());
                        if (res != ConnectionResult.SUCCESS) {
                            Log.e(LOG_TAG, "Google Play Services: Unavailable");
                            callbackContext.error(getErrorJSON(ERROR.SERVICE_UNAVAILABLE, "Unavailable"));
                            return;
                        }

                        helper.setup(new GameHelper.GameHelperListener() {
                            @Override
                            public void onSignInFailed() {
                                _connected = false;
                                GameHelper.SignInFailureReason error = helper.getSignInError();
                                PluginResult result;
                                if (error != null) {
                                    Log.e(LOG_TAG, "Google Play Services: Login Error -> " + error.getServiceErrorCode());
                                    result = new PluginResult(PluginResult.Status.ERROR, getErrorJSON(ERROR.SERVICE_ERROR, error.getServiceErrorCode(), "Login Failed"));
                                } else {
                                    Log.e(LOG_TAG, "Google Play Services: Login Error -> Suspended");
                                    result = new PluginResult(PluginResult.Status.ERROR, getErrorJSON(ERROR.SERVICE_ERROR, -1, "Login Suspended"));
                                }
                                result.setKeepCallback(true);
                                callbackContext.sendPluginResult(result);
                            }

                            @Override
                            public void onSignInSucceeded() {
                                Log.d(LOG_TAG, "Google Play Services: Login -> Success");
                                _connected = true;
                                PluginResult result = new PluginResult(PluginResult.Status.OK, getPlayerDetailJSON());
                                result.setKeepCallback(true);
                                callbackContext.sendPluginResult(result);
                            }
                        });
                    }

                    Log.d(LOG_TAG, "Google Play Services: Logging In");
                    helper.beginUserInitiatedSignIn();

                    PluginResult result = new PluginResult(PluginResult.Status.OK, "LoggingIn");
                    result.setKeepCallback(true);
                    callbackContext.sendPluginResult(result);
                }
            });
            return true;
        } else if (action.equals("logout")) {
            cordova.getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (getGameHelper().isSignedIn()) {
                        Log.d(LOG_TAG, "Google Play Services: Logout");
                        getGameHelper().signOut();
                    }
                    _connected = false;
                    callbackContext.success();
                }
            });
            return true;
        } else if (action.equals("getPlayerDetails")) {
            if (checkConnected(callbackContext)) {
                cordova.getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        callbackContext.success(getPlayerDetailJSON());
                    }
                });
            }
            return true;
        } else if (action.equals("getPlayerScore")) {
            if (checkConnected(callbackContext)) {
                final String leaderboardId = args.getString(0);
                final int span = args.optInt(1, LeaderboardVariant.TIME_SPAN_DAILY);
                Log.d(LOG_TAG, String.format("Google Play Services: getPlayerScore(%s,%d)", leaderboardId, span));

                cordova.getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Games.Leaderboards.loadCurrentPlayerLeaderboardScore(getGameHelper().getApiClient(), leaderboardId, span, LeaderboardVariant.COLLECTION_PUBLIC)
                                .setResultCallback(new PluginResultCallbacks<Leaderboards.LoadPlayerScoreResult>(callbackContext) {
                                    @Override
                                    public void onSuccess(@NonNull Leaderboards.LoadPlayerScoreResult result) {
                                        callbackContext.success(getScoreJSON(result.getScore()));
                                        super.onSuccess(result);
                                    }
                                });
                    }
                });
            }
            return true;
        } else if (action.equals("submitScore")) {
            if (checkConnected(callbackContext)) {
                final String leaderboardId = args.getString(0);
                final long score = args.getInt(1);
                final String tag = args.optString(2);

                Log.d(LOG_TAG, String.format("Google Play Services: submitScore(%s,%d, %s)", leaderboardId, score, tag));

                cordova.getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Games.Leaderboards.submitScoreImmediate(getGameHelper().getApiClient(), leaderboardId, score, tag)
                                .setResultCallback(new PluginResultCallbacks<Leaderboards.SubmitScoreResult>(callbackContext) {
                                    @Override
                                    public void onSuccess(@NonNull Leaderboards.SubmitScoreResult result) {
                                        super.onSuccess(result);
                                        callbackContext.success();
                                    }
                                });
                    }
                });
            }

            return true;
        } else if (action.equals("submitScores")) {
            if (checkConnected(callbackContext)) {
                Log.d(LOG_TAG, String.format("Google Play Services: submitScores(...count=%d)", args.length()));

                final JSONArray entries = args;

                cordova.getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        GoogleApiClient client = getGameHelper().getApiClient();

                        for (int a = 0, l = entries.length(); a < l; a++) {
                            try {
                                JSONObject entry = entries.getJSONObject(a);

                                String leaderboardId = entry.getString("leaderboardId");
                                long score = entry.getLong("score");
                                String tag = entry.optString("tag");

                                Log.d(LOG_TAG, String.format("Google Play Services: submitScores - entry(%s, %d, %s)", leaderboardId, score, tag));

                                Games.Leaderboards.submitScore(client, leaderboardId, score, tag);
                            } catch (JSONException e) {
                            }
                        }

                        callbackContext.success();
                    }
                });
            }

            return true;
        } else if (action.equals("getLeaderboardScores")) {
            if (checkConnected(callbackContext)) {
                final String leaderboardId = args.getString(0);
                final String scope = args.optString(1, "player");
                final int span = args.optInt(2, LeaderboardVariant.TIME_SPAN_DAILY);
                final int maxResults = args.optInt(3, 10);

                Log.d(LOG_TAG, String.format("Google Play Services: getLeaderboardScore(%s, %s, %d, %d)", leaderboardId, scope, span, maxResults));

                cordova.getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        PendingResult<Leaderboards.LoadScoresResult> result;
                        if (scope.equals("top")) {
                            result = Games.Leaderboards.loadTopScores(getGameHelper().getApiClient(), leaderboardId, span, LeaderboardVariant.COLLECTION_PUBLIC, maxResults);
                        } else {
                            result = Games.Leaderboards.loadPlayerCenteredScores(getGameHelper().getApiClient(), leaderboardId, span, LeaderboardVariant.COLLECTION_PUBLIC, maxResults);
                        }

                        result.setResultCallback(new PluginResultCallbacks<Leaderboards.LoadScoresResult>(callbackContext) {
                            @Override
                            public void onSuccess(@NonNull Leaderboards.LoadScoresResult result) {
                                JSONArray array = new JSONArray();
                                LeaderboardScoreBuffer buffer = result.getScores();
                                for (LeaderboardScore score : buffer) {
                                    array.put(getScoreJSON(score));
                                }
                                buffer.release();
                                super.onSuccess(result);

                                callbackContext.success(array);
                            }
                        });
                    }
                });
            }
            return true;
        } else if (action.equals("showLeaderboard")) {
            if (checkConnected(callbackContext)) {
                final String leaderboardId = args.getString(0);
                final int span = args.optInt(1, LeaderboardVariant.TIME_SPAN_DAILY);

                Log.d(LOG_TAG, String.format("Google Play Services: showLeaderboard(%s,%d)", leaderboardId, span));

                cordova.getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        cordova.getActivity().startActivityForResult(Games.Leaderboards.getLeaderboardIntent(getGameHelper().getApiClient(), leaderboardId, span), 0);
                        callbackContext.success();
                    }
                });
            }
            return true;
        } else if (action.equals("showLeaderboards")) {
            if (checkConnected(callbackContext)) {

                Log.d(LOG_TAG, "Google Play Services: showLeaderboards()");

                cordova.getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        cordova.getActivity().startActivityForResult(Games.Leaderboards.getAllLeaderboardsIntent(getGameHelper().getApiClient()), 0);
                        callbackContext.success();
                    }
                });
            }
            return true;
        } else if (action.equals("unlockAchievement")) {
            if (checkConnected(callbackContext)) {
                final String achievementId = args.getString(0);

                Log.d(LOG_TAG, String.format("Google Play Services: unlockAchievement(%s)", achievementId));

                cordova.getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Games.Achievements.unlockImmediate(getGameHelper().getApiClient(), achievementId)
                                .setResultCallback(new PluginResultCallbacks<Achievements.UpdateAchievementResult>(callbackContext) {
                                    @Override
                                    public void onSuccess(@NonNull Achievements.UpdateAchievementResult result) {
                                        super.onSuccess(result);
                                        callbackContext.success();
                                    }
                                });
                    }
                });
            }
            return true;
        } else if (action.equals("incrementAchievement")) {
            if (checkConnected(callbackContext)) {
                final String achievementId = args.getString(0);
                final int steps = args.getInt(1);

                Log.d(LOG_TAG, String.format("Google Play Services: incrementAchievement(%s,%d)", achievementId, steps));

                cordova.getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Games.Achievements.incrementImmediate(getGameHelper().getApiClient(), achievementId, steps)
                                .setResultCallback(new PluginResultCallbacks<Achievements.UpdateAchievementResult>(callbackContext) {
                                    @Override
                                    public void onSuccess(@NonNull Achievements.UpdateAchievementResult result) {
                                        super.onSuccess(result);
                                        callbackContext.success();
                                    }
                                });
                    }
                });
            }
            return true;
        } else if (action.equals("showAchievements")) {
            if (checkConnected(callbackContext)) {

                Log.d(LOG_TAG, "Google Play Services: showAchievements()");

                cordova.getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        cordova.getActivity().startActivityForResult(Games.Achievements.getAchievementsIntent(getGameHelper().getApiClient()), 0);
                        callbackContext.success();
                    }
                });
            }
            return true;
        } else if (action.equals("getAchievements")) {
            if (checkConnected(callbackContext)) {

                Log.d(LOG_TAG, "Google Play Services: getAchievements()");

                cordova.getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Games.Achievements.load(getGameHelper().getApiClient(), false)
                                .setResultCallback(new PluginResultCallbacks<Achievements.LoadAchievementsResult>(callbackContext) {
                                    @Override
                                    public void onSuccess(@NonNull Achievements.LoadAchievementsResult result) {
                                        JSONArray array = new JSONArray();
                                        AchievementBuffer buffer = result.getAchievements();
                                        for (Achievement ach : buffer) {
                                            array.put(getAchievementJSON(ach));
                                        }
                                        buffer.release();
                                        super.onSuccess(result);

                                        callbackContext.success(array);
                                    }
                                });
                    }
                });
            }
            return true;
        } else if (action.equals("resetAchievements")) {
            Log.d(LOG_TAG, "Google Play Services: resetAchievements()");

            callbackContext.error(getErrorJSON(ERROR.NOT_SUPPORTED, "Not Supported"));
            return true;
        }

        return false;
    }

    @Override
    public void onStart() {
        super.onStart();
        if (_connected) {
            getGameHelper().onStart(cordova.getActivity());
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (_connected) {
            getGameHelper().onStop();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        getGameHelper().onActivityResult(requestCode, resultCode, intent);
    }

    private boolean checkConnected(CallbackContext context) {
        if (!_connected) {
            Log.e(LOG_TAG, "Google Play Services: Not Connected");
            context.error(getErrorJSON(ERROR.NOT_CONNECTED, "Not Connected"));
            return false;
        }
        return true;
    }

    private GameHelper getGameHelper() {
        if (mHelper == null) {
            mHelper = new GameHelper(this.cordova.getActivity(), GameHelper.CLIENT_GAMES);//public GameHelper(Activity activity, int clientsToUse) {
            mHelper.enableDebugLog(true);
        }
        return mHelper;
    }

    private JSONObject getErrorJSON(int code, String message) {
        JSONObject json = new JSONObject();

        try {
            json.put("code", code)
                    .put("message", message);
        } catch (JSONException e) {
        }

        return json;
    }

    private JSONObject getErrorJSON(int code, int internalCode, String message) {
        JSONObject json = getErrorJSON(code, message);
        try {
            json.put("serviceCode", internalCode);
        } catch (JSONException e) {
        }
        return json;
    }

    private JSONObject getPlayerDetailJSON() {
        Player player = Games.Players.getCurrentPlayer(getGameHelper().getApiClient());
        JSONObject json = new JSONObject();

        if (player != null) {
            try {
                json.put("playerId", player.getPlayerId())
                        .put("playerName", player.getDisplayName())
                        .put("playerImage", player.hasHiResImage() ? player.getHiResImageUrl() : player.getIconImageUrl());
            } catch (JSONException e) {
            }
        }

        return json;
    }

    private JSONObject getScoreJSON(LeaderboardScore score) {
        JSONObject json = new JSONObject();

        try {
            Player player = score.getScoreHolder();
            json.put("playerId", player.getPlayerId())
                    .put("playerName", player.getDisplayName())
                    .put("playerImage", player.hasHiResImage() ? player.getHiResImageUrl() : score.getScoreHolderIconImageUrl())
                    .put("score", score.getRawScore())
                    .put("rank", score.getRank())
                    .put("timestamp", score.getTimestampMillis())
                    .put("tag", score.getScoreTag());
        } catch (JSONException e) {
        }

        return json;
    }

    private JSONObject getAchievementJSON(Achievement ach) {
        JSONObject json = new JSONObject();

        try {
            int state = ach.getState();
            json.put("id", ach.getAchievementId())
                    .put("isCompleted", state == Achievement.STATE_UNLOCKED)
                    .put("isHidden", state == Achievement.STATE_HIDDEN)
                    .put("xp", ach.getXpValue())
                    .put("name", ach.getName())
                    .put("desc", ach.getDescription())
                    .put("percent", ach.getCurrentSteps() / ach.getTotalSteps() * 100)
                    .put("totalSteps", ach.getTotalSteps())
                    .put("steps", ach.getCurrentSteps())
                    .put("image", ach.getRevealedImageUri())
                    .put("imageUnlocked", ach.getUnlockedImageUri())
                    .put("timestamp", ach.getLastUpdatedTimestamp());
        } catch (JSONException e) {
        }

        return json;
    }
}