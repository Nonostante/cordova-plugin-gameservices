(function () {

    if (!Microsoft.Xbox) {
        return;
    }

    var xbox = Microsoft.Xbox.Services,
        user = new xbox.System.XboxLiveUser(),
        /** @type {Microsoft.Xbox.Services.XboxLiveContext} */
        context,
        /** @type {string} */
        serviceConfigurationId,
        /** @type {Microsoft.Xbox.Services.Statistics.Manager.StatisticManager} */
        statManager,
        /** @type {Microsoft.Xbox.Services.Social.XboxUserProfile} */
        userProfile;

    var ERROR = {
        SERVICE_UNAVAILABLE: 1,
        NOT_SUPPORTED: 2,
        NOT_CONNECTED: 3,
        SERVICE_ERROR: 4,
        LOGIN_PENDING: 5,
        CANCELLED: 6
    };

    function getErrorResult(code, message, serviceCode) {
        return {
            code: code,
            message: message,
            serviceCode: serviceCode
        };
    }
    function checkConnected(f) {
        if (!user.isSignedIn) {
            f && f(getErrorResult(ERROR.NOT_CONNECTED, "Not Connected"));
            return false;
        }
        return true;
    }

    function getContext() {
        if (!context) {
            context = new xbox.XboxLiveContext(user);
            serviceConfigurationId = context.appConfig.serviceConfigurationId;
            statManager = xbox.Statistics.Manager.StatisticManager.singletonInstance;
            try {
                statManager.addLocalUser(user);
            } catch (e) { }
        }
        return context;
    }

    function getPlayerImage(profile) {
        var url = profile.gameDisplayPictureResizeUri.rawUri;
        //trim mode padding param
        url = url.replace("mode=Padding", "w=128");
        return url;
    }

    function getPlayerData() {
        return {
            playerId: user.xboxUserId,
            playerName: user.gamertag,
            playerImage: userProfile ? getPlayerImage(userProfile) : undefined
        };
    }

    function loadPlayerProfile(userId) {
        return getContext().profileService.getUserProfileAsync(userId);
    }
    function loadPlayerProfiles(userIds) {
        return getContext().profileService.getUserProfilesAsync(userIds);
    }

    function processStatManager(leaderboardId, s, f) {
        var actual = processStatManager.actual;
        if (actual) {
            if (actual.leaderboardId !== leaderboardId || actual.s !== s) {
                actual.f && actual.f(getErrorResult(ERROR.CANCELLED, "Cancelled"));
                //update
                actual.leaderboardId = leaderboardId;
                actual.s = s;
                actual.f = f;
            }
        } else {
            //track
            processStatManager.actual = {
                leaderboardId: leaderboardId,
                s: s,
                f: f
            };
        }

        try {
            var events = statManager.doWork();
            for (var a = 0; a < events.length; a++) {
                if (events[a].eventType === xbox.Statistics.Manager.StatisticEventType.getLeaderboardComplete) {
                    var result = events[a].eventArgs.result;
                    if (result) {
                        processStatManager.actual = null;
                        processLeaderboardResult(result, s);
                    }
                    return;
                }
            }
        } catch (e) { }

        setTimeout(function () {
            processStatManager(leaderboardId, s, f);
        }, 100);
    }

    /**
    * @param {Microsoft.Xbox.Services.Leaderboard.LeaderboardResult} result
    */
    function processLeaderboardResult(result, s) {
        var entries = [],
            profiles = [];

        for (var a = 0, l = result.rows.length; a < l; a++) {
            var row = result.rows[a],
                value = row.values[0];

            profiles.push(row.xboxUserId);

            entries.push({
                playerId: row.xboxUserId,
                playerName: row.gamertag,
                playerImage: undefined,
                rank: row.rank,
                score: !isNaN(value) ? value : parseInt(value, 10)
            });
        }

        if (profiles.length > 0) {
            loadPlayerProfiles(profiles).then(
                function (profiles) {
                    for (var a = 0, l = profiles.length, ll = entries.length; a < l; a++) {
                        for (var b = 0; b < ll; b++) {
                            if (entries[b].playerId === profiles[a].xboxUserId) {
                                entries[b].playerImage = getPlayerImage(profiles[a])
                                break;
                            }
                        }
                    }

                    s && s(entries);
                },
                function (err) {
                    s && s(entries);
                });
        } else {
            s && s(entries);
        }
    }

    var isLoginPending = false;

    cordova.commandProxy.add("GameServices", {
        login: function (s, f, args) {
            if (isLoginPending) {
                f && f(getErrorResult(ERROR.LOGIN_PENDING, "Login Pending"));
                return;
            }

            if (!user.isSignedIn) {
                isLoginPending = true;
                s && s("LoggingIn", { keepCallback: true });

                user.signInAsync(null).then(
                    function (r) {
                        if (r && r.status === xbox.System.SignInStatus.success) {
                            loadPlayerProfile(user.xboxUserId).then(
                                function (profile) {
                                    userProfile = profile;
                                    s && s(getPlayerData());
                                    isLoginPending = false;
                                },
                                function () {
                                    s && s(getPlayerData());
                                    isLoginPending = false;
                                });
                        } else {
                            f && f(getErrorResult(ERROR.SERVICE_ERROR, "Not Connected", r.status));
                            isLoginPending = false;
                        }
                    },
                    function (err) {
                        f && f(getErrorResult(ERROR.SERVICE_ERROR, err.message, err.number));
                        isLoginPending = false;
                    });
            } else {
                s && s(getPlayerData());
            }
        },
        logout: function (s, f) {
            if (user.isSignedIn) {
                f && f(getErrorResult(ERROR.NOT_SUPPORTED, "Not Supported"));
            } else {
                s && s();
            }
        },
        getPlayerDetails: function (s, f) {
            if (!checkConnected(f)) return;

            s && s(getPlayerData());
        },
        getPlayerScore: function (s, f, args) {
            if (!checkConnected(f)) return;

            var leaderboardId = args[0],
                span = args[1] || 0;

            var query = new xbox.Leaderboard.LeaderboardQuery();
            query.skipResultToMe = true;
            query.maxItems = 1;
            statManager.getLeaderboard(user, leaderboardId, query);

            processStatManager(leaderboardId, function (results) {
                s && s(results[0]);
            }, f);
        },
        getLeaderboardScores: function (s, f, args) {
            if (!checkConnected(f)) return;

            var leaderboardId = args[0],
                scope = args[1] || "player",
                span = args[2] || 0,
                maxResults = args[3] || 10;

            var query = new xbox.Leaderboard.LeaderboardQuery();
            query.maxItems = maxResults;
            query.skipResultToMe = scope === "player";

            statManager.getLeaderboard(user, leaderboardId, query);
            processStatManager(leaderboardId, s, f);
        },
        submitScore: function (s, f, args) {
            if (!checkConnected(f)) return;

            var leaderboardId = args[0],
                value = args[1],
                tag = args[2];

            try {
                statManager.setStatisticIntegerData(user.xboxUserId, leaderboardId, value);
                statManager.requestFlushToService(user.xboxUserId);

            } catch (e) {
                f && f(getErrorResult(ERROR.SERVICE_ERROR, e.message, e.number));
                return;
            }
            s && s();
        },
        submitScores: function (s, f, args) {
            if (!checkConnected(f)) return;

            var scores = args;
            try {
                for (var a = 0; a < scores.length; a++) {
                    var entry = scores[a];
                    statManager.setStatisticIntegerData(user, entry.leaderboardId, entry.score);
                }

                statManager.requestFlushToService(user);
            } catch (e) {
                f && f(getErrorResult(ERROR.SERVICE_ERROR, e.message, e.number));
                return;
            }

            s && s();
        },
        showLeaderboard: function (s, f) {
            f && f(getErrorResult(ERROR.NOT_SUPPORTED, "Not Supported"));
        },
        showLeaderboards: function (s, f) {
            f && f(getErrorResult(ERROR.NOT_SUPPORTED, "Not Supported"));
        },
        unlockAchievement: function (s, f) {
            f && f(getErrorResult(ERROR.NOT_SUPPORTED, "Not Supported"));
        },
        incrementAchievement: function (s, f) {
            f && f(getErrorResult(ERROR.NOT_SUPPORTED, "Not Supported"));
        },
        getAchievements: function (s, f) {
            f && f(getErrorResult(ERROR.NOT_SUPPORTED, "Not Supported"));
        },
        showAchievements: function (s, f) {
            f && f(getErrorResult(ERROR.NOT_SUPPORTED, "Not Supported"));
        },
        resetAchievements: function (s, f) {
            f && f(getErrorResult(ERROR.NOT_SUPPORTED, "Not Supported"));
        }
    });
});