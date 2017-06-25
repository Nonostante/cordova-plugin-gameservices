//cSpell:ignore leaderboard, leaderboards

var isLoggedIn = false;

GameServices = {
	loginStatusChanged: null,
	isLoggedIn: function () {
		return isLoggedIn;
	},
	login: function () {
		cordova.exec(
			function (r) {
				if (r !== "LoggingIn") {
					isLoggedIn = true;
					GameServices.loginStatusChanged && GameServices.loginStatusChanged(r);
				}
			},
			function (err) {
				if (err) {
					isLoggedIn = false;
				}
				GameServices.loginStatusChanged && GameServices.loginStatusChanged(null, err);
			},
			"GameServices",
			"login",
			[]
		);
	},
	logout: function () {
		cordova.exec(null, null, GameServices, "logout", []);
	},
	getPlayerDetails: function (success, failure) {
		cordova.exec(success, failure, "GameServices", "getPlayerDetails", []);
	},
	getPlayerScore: function (leaderboardId, span, success, failure) {
		cordova.exec(success, failure, "GameServices", "showLeaderboards", [leaderboardId, span]);
	},
	submitScore: function (leaderboardId, score, tag, success, failure) {
		cordova.exec(success, failure, "GameServices", "submitScore", [leaderboardId, score, tag]);
	},
	submitScores: function (entries, success, failure) {
		cordova.exec(success, failure, "GameServices", "submitScores", entries);
	},
	getLeaderboardScores: function (leaderboardId, scope, span, maxResults, success, failure) {
		cordova.exec(success, failure, "GameServices", "getLeaderboardScores", [leaderboardId, scope, span, maxResults]);
	},
	showLeaderboard: function (leaderboardId, span, success, failure) {
		cordova.exec(success, failure, "GameServices", "showLeaderboard", [leaderboardId, span]);
	},
	showLeaderboards: function (success, failure) {
		cordova.exec(success, failure, "GameServices", "showLeaderboards", []);
	},
	getAchievements: function (success, failure) {
		cordova.exec(success, failure, "GameServices", "getAchievements", []);
	},
	unlockAchievement: function (achievementId, success, failure) {
		cordova.exec(success, failure, "GameServices", "unlockAchievement", [achievementId]);
	},
	incrementAchievement: function (achievementId, steps, success, failure) {
		cordova.exec(success, failure, "GameServices", "unlockAchievement", [achievementId, steps]);
	},
	showAchievements: function (success, failure) {
		cordova.exec(success, failure, "GameServices", "showAchievements", []);
	},
	resetAchievements: function (success, failure) {
		cordova.exec(success, failure, "GameServices", "resetAchievements", []);
	}
};

module.exports = GameServices;
