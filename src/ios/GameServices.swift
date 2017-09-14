
import Foundation
import GameKit

@objc(GameServices)
class GameServices : CDVPlugin {
    
    struct ERROR {
        static let SERVICE_UNAVAILABLE = 1
        static let NOT_SUPPORTED = 2
        static let NOT_CONNECTED = 3
        static let SERVICE_ERROR = 4
        static let LOGIN_PENDING = 5
    }
    
    private var connected = false
    private var setupDone = false
    private var gcDelegate: GKGameCenterControllerDelegate? = nil
    
    private func getErrorResult(_ code: Int, _ message: String) -> CDVPluginResult {
        var obj = [[String:Any]]()
        obj.append(["code": code, "message": message])
        return CDVPluginResult(status:CDVCommandStatus_ERROR, messageAs: obj)
    }
    
    private func getOkResult(_ message: [Any]) -> CDVPluginResult {
        return CDVPluginResult(status: CDVCommandStatus_OK, messageAs: message)
    }
    private func getOkResult(_ message: [AnyHashable:Any]) -> CDVPluginResult {
        return CDVPluginResult(status: CDVCommandStatus_OK, messageAs: message)
    }
    private func getOkResult() -> CDVPluginResult {
        return CDVPluginResult(status: CDVCommandStatus_OK)
    }
    
    private func sendOkResult(_ command: CDVInvokedUrlCommand, _ message: [AnyHashable:Any]) {
        self.commandDelegate.send(getOkResult(message), callbackId: command.callbackId)
    }
    private func sendOkResult(_ command: CDVInvokedUrlCommand, _ message: [Any]) {
        self.commandDelegate.send(getOkResult(message), callbackId: command.callbackId)
    }
    private func sendOkResult(_ command: CDVInvokedUrlCommand) {
        self.commandDelegate.send(getOkResult(), callbackId: command.callbackId)
    }
    private func sendErrorResult(_ command: CDVInvokedUrlCommand, _ code: Int, _ message: String) {
        self.commandDelegate.send(getErrorResult(code, message), callbackId: command.callbackId)
    }
    
    private func checkConnected(_ command: CDVInvokedUrlCommand) -> Bool {
        if(!connected){
            self.commandDelegate.send(getErrorResult(ERROR.NOT_CONNECTED, "Not Connected"), callbackId: command.callbackId)
            return false
        }
        return true
    }
    
    func login(_ command: CDVInvokedUrlCommand) {
        let player = GKLocalPlayer.localPlayer()
        
        if (setupDone) {
            if(connected){
                self.sendOkResult(command, getPlayerOBJ(player))
            } else {
                self.sendErrorResult(command, ERROR.LOGIN_PENDING, "Login Pending")
            }
            return
        }
        
        
        player.authenticateHandler = {(ViewController, error) in
            if((ViewController) != nil) {
                self.viewController.present(ViewController!, animated: true, completion: nil)
            } else if (player.isAuthenticated) {
                self.connected = true
                let result = self.getOkResult(self.getPlayerOBJ(player))
                result.setKeepCallbackAs(true)
                self.commandDelegate.send(result, callbackId: command.callbackId)
            } else {
                self.connected = false
                let result = self.getErrorResult(ERROR.SERVICE_ERROR, error != nil ? error!.localizedDescription : "")
                result.setKeepCallbackAs(true)
                self.commandDelegate.send(result, callbackId: command.callbackId)
            }
        }
        
        setupDone = true
    }
    
    func logout(_ command: CDVInvokedUrlCommand) {
        self.commandDelegate.send(self.getErrorResult(ERROR.NOT_SUPPORTED, "Not Supported"), callbackId: command.callbackId)
    }
    
    func getPlayerDetails(_ command: CDVInvokedUrlCommand) {
        if (!checkConnected(command)) {
            return
        }
        
        let localPlayer = GKLocalPlayer.localPlayer();
        self.sendOkResult(command, getPlayerOBJ(localPlayer))
    }
    
    func getPlayerScore(_ command: CDVInvokedUrlCommand){
        if(!checkConnected(command)){
            return
        }
        
        let leaderboardId = command.argument(at: 0) as! String
        let span = command.argument(at: 1, withDefault: 0) as! Int
        
        let leaderboard = GKLeaderboard()
        leaderboard.identifier = leaderboardId
        switch span {
            case 1: leaderboard.timeScope = GKLeaderboardTimeScope.week
            case 2: leaderboard.timeScope = GKLeaderboardTimeScope.allTime
            case _: leaderboard.timeScope = GKLeaderboardTimeScope.today
        }
        
        leaderboard.loadScores { (scores, error) in
            if (error == nil) {
                self.sendOkResult(command, self.getScoreOBJ(leaderboard.localPlayerScore!))
            } else {
                self.sendErrorResult(command, ERROR.SERVICE_ERROR, error!.localizedDescription)
            }
        }
    }
    
    func getLeaderboardScores(_ command: CDVInvokedUrlCommand){
        if(!checkConnected(command)){
            return
        }
        
        let leaderboardId = command.argument(at: 0) as! String
        _ = command.argument(at: 1, withDefault: "player") as! String
        let span = command.argument(at: 2, withDefault: 0) as! Int
        let maxResults = command.argument(at: 3, withDefault: 10) as! Int
        
        let leaderboard = GKLeaderboard()
        leaderboard.identifier = leaderboardId
        leaderboard.playerScope = GKLeaderboardPlayerScope.global
        switch span {
            case 1: leaderboard.timeScope = GKLeaderboardTimeScope.week
            case 2: leaderboard.timeScope = GKLeaderboardTimeScope.allTime
            case _: leaderboard.timeScope = GKLeaderboardTimeScope.today
        }
        leaderboard.range = NSRange(location: 1, length: maxResults)

        leaderboard.loadScores { (scores, error) in
            if (error == nil) {
                let values = scores != nil ? scores!.map { self.getScoreOBJ($0) } : [Any]()
                self.sendOkResult(command, values)
            } else {
                self.sendErrorResult(command, ERROR.SERVICE_ERROR, error!.localizedDescription)
            }
        }
    }
    
    func submitScore(_ command: CDVInvokedUrlCommand){
        if(!checkConnected(command)){
            return
        }
        
        let leaderboardId = command.argument(at: 0) as! String
        let value = command.argument(at: 1, withDefault: 0) as! Int
        let tag = command.argument(at: 2, withDefault: 0)
        
        let score = GKScore(leaderboardIdentifier: leaderboardId)
        score.value = Int64(value)
        score.context = self.getTag(tag)
        
        GKScore.report([score]) { (error) in
            if (error == nil) {
                self.sendOkResult(command)
            } else {
                self.sendErrorResult(command, ERROR.SERVICE_ERROR, error!.localizedDescription)
            }
        }
    }
    
    func submitScores(_ command: CDVInvokedUrlCommand) {
        if(!checkConnected(command)){
            return
        }
        
        let scores = command.arguments.map { (entry) -> GKScore in
            let dict = entry as! NSDictionary
            let score = GKScore(leaderboardIdentifier: dict.value(forKey: "leaderboardId") as! String)
            score.value = Int64(dict.value(forKey: "score") as! Int)
            let tag = dict.value(forKey: "tag")
            score.context = self.getTag(tag)
            return score
        }
        
        GKScore.report(scores) { (error) in
            if (error == nil) {
                self.sendOkResult(command)
            } else {
                self.sendErrorResult(command, ERROR.SERVICE_ERROR, error!.localizedDescription)
            }
        }
    }
    
    func showLeaderboard(_ command: CDVInvokedUrlCommand){
        if(!checkConnected(command)){
            return
        }
        
        let leaderboardId = command.argument(at: 0) as! String
        let span = command.argument(at: 1, withDefault: 0) as! Int

        let controller = GKGameCenterViewController()
        controller.viewState = GKGameCenterViewControllerState.leaderboards
        controller.leaderboardIdentifier = leaderboardId
        switch span {
            case 1: controller.leaderboardTimeScope = GKLeaderboardTimeScope.week
            case 2: controller.leaderboardTimeScope = GKLeaderboardTimeScope.allTime
            case _: controller.leaderboardTimeScope = GKLeaderboardTimeScope.today
        }
        
        controller.gameCenterDelegate = createGCDelegate(command)
        self.viewController.present(controller, animated: true, completion: nil)
    }

    
    func showLeaderboards(_ command: CDVInvokedUrlCommand){
        if(!checkConnected(command)){
            return
        }
        
        let controller = GKGameCenterViewController()
        controller.viewState = GKGameCenterViewControllerState.leaderboards
        controller.gameCenterDelegate = createGCDelegate(command)
        self.viewController.present(controller, animated: true, completion: nil)
    }
    
    func getAchievements(_ command: CDVInvokedUrlCommand){
        if(!checkConnected(command)){
            return
        }
        
        GKAchievement.loadAchievements { (achievements, error) in
            if (error != nil) {
                self.sendErrorResult(command, ERROR.SERVICE_ERROR, error!.localizedDescription)
            } else {
                let array = achievements!.map { self.getAchievementOBJ($0) }

                GKAchievementDescription.loadAchievementDescriptions { (descriptions, error) in
                    if (error != nil) {
                        self.sendErrorResult(command, ERROR.SERVICE_ERROR, error!.localizedDescription)
                    } else {
                        for descr in descriptions! {
                            var achievement = array.first(where: { ($0["id"] as! String) == descr.identifier! })
                            if (achievement != nil) {
                                achievement!["name"] = descr.title
                                achievement!["desc"] = descr.description
                                achievement!["xp"] = descr.maximumPoints
                                achievement!["isHidden"] = descr.isHidden
                            }
                        }
                        
                        self.sendOkResult(command, array)
                    }
                }
            }
        }
    }
    
    func unlockAchievement(_ command: CDVInvokedUrlCommand){
        if(!checkConnected(command)){
            return
        }
        
        let achievementId = command.argument(at: 0) as! String
        let showBanner = command.argument(at: 1, withDefault: true) as! Bool

        let achievement = GKAchievement(identifier: achievementId)
        achievement.percentComplete = 100.0
        achievement.showsCompletionBanner = showBanner
        
        GKAchievement.report([achievement]) { (error) in
            if (error == nil) {
                self.sendOkResult(command)
            } else {
                self.sendErrorResult(command, ERROR.SERVICE_ERROR, error!.localizedDescription)
            }
        }
    }
    
    func incrementAchievement(_ command: CDVInvokedUrlCommand){
        if(!checkConnected(command)){
            return
        }
        
        let achievementId = command.argument(at: 0) as! String
        let percent = command.argument(at: 1) as! Double
        let showBanner = command.argument(at: 2, withDefault: true) as! Bool
        
        let achievement = GKAchievement(identifier: achievementId)
        achievement.percentComplete = percent
        achievement.showsCompletionBanner = showBanner
        
        GKAchievement.report([achievement]) { (error) in
            if (error == nil) {
                self.sendOkResult(command)
            } else {
                self.sendErrorResult(command, ERROR.SERVICE_ERROR, error!.localizedDescription)
            }
        }
    }

    func showAchievements(_ command: CDVInvokedUrlCommand){
        if(!checkConnected(command)){
            return
        }
        
        let controller = GKGameCenterViewController()
        controller.viewState = GKGameCenterViewControllerState.achievements
        controller.gameCenterDelegate = createGCDelegate(command)
        self.viewController.present(controller, animated: true, completion: nil)
    }
    
    func resetAchievements(_ command: CDVInvokedUrlCommand){
        if(!checkConnected(command)){
            return
        }
        
        GKAchievement.resetAchievements { (error) in
            if (error == nil) {
                self.sendOkResult(command)
            } else {
                self.sendErrorResult(command, ERROR.SERVICE_ERROR, error!.localizedDescription)
            }
        }
    }

    private func getTag(_ value: Any?) -> UInt64 {
        if value == nil {
            return 0
        } else if let s = value as? String {
            if let v = UInt64(s, radix: 16) {
                return v
            }
        } else if let v = value as? Int {
            return UInt64(v)
        } else if let v = value as? UInt {
            return UInt64(v)
        } else if let v = value as? Int64 {
            return UInt64(v)
        } else if let v = value as? UInt64 {
            return v
        }
        
        return 0
    }    
    
    private func getPlayerOBJ(_ player: GKPlayer) -> [String:Any] {
        return [
            "playerId": player.playerID!,
            "playerName": player.alias!
        ]
    }
    
    private func getScoreOBJ(_ score: GKScore) -> [String:Any] {
        let player = score.player!
        return [
            "playerId": player.playerID!,
            "playerName": player.alias!,
            "score": score.value,
            "rank": score.rank,
            "timestamp": Int64((score.date.timeIntervalSince1970 * 1000.0).rounded()),
            "tag": score.context
        ]
    }
    
    private func getAchievementOBJ(_ achievement: GKAchievement) -> [String:Any] {
        return [
            "id": achievement.identifier!,
            "timestamp": Int((achievement.lastReportedDate.timeIntervalSince1970 * 1000.0).rounded()),
            "isCompleted": achievement.isCompleted,
            "percent": achievement.percentComplete
        ]
    }
    
    private func createGCDelegate(_ command: CDVInvokedUrlCommand) -> GKGameCenterControllerDelegate? {
        self.gcDelegate = GameCenterDelegate(handler: {
            self.sendOkResult(command)
            self.gcDelegate = nil
        })
        return self.gcDelegate!
    }
}

class GameCenterDelegate : NSObject, GKGameCenterControllerDelegate {
    private var handler : ()->()
    
    init(handler: @escaping ()->()){
        self.handler = handler
    }
    
    func gameCenterViewControllerDidFinish(_ controller: GKGameCenterViewController) {
        controller.dismiss(animated: true, completion: {
            self.handler()
        })
    }
}
