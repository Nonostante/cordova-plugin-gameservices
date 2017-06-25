declare module Cordova {
    export module Plugin {
        export module GameServices {
            type Player = {
                playerId: string
                playerName: string
                playerImage?: string
            }

            enum LeaderboardEntrySpan {
                today = 0,
                weekly = 1,
                alltime = 2
            }

            type LeaderboardScoreEntry = Player & {
                score: number
                rank: number
                tag?: any
                timestamp: number
            }

            type AchievementEntry = {
                id: string
                isCompleted: boolean
                isHidden: boolean
                xp: number
                name: string
                desc: string
                percent: number
                totalSteps?: number
                steps?: number
                image?: string
                imageUnlocked?: string
                timestamp: number
            }

            type ServiceError = {
                code: number
                message: string
                serviceCode?: number
            }
        }

        interface GameService {
            loginStatusChanged?: (state: number, error?: GameServices.ServiceError) => void

            login(): void
            logout(): void

            isLoggedIn(): boolean

            getPlayerScore(leaderboardId: string, span: GameServices.LeaderboardEntrySpan, success: (data: GameServices.LeaderboardScoreEntry) => void, fail: (error: GameServices.ServiceError) => void): void
            getLeaderboardScores(leaderboardId: string, scope: "top" | "player", span: GameServices.LeaderboardEntrySpan, maxResults: number, success: (data: GameServices.LeaderboardScoreEntry[]) => void, fail: (error: GameServices.ServiceError) => void): void

            submitScore(leaderboardId: string, score: number, tag?: any, success?: () => void, fail?: (ServiceError) => void): void
            submitScores(entries: { leaderboardId: string, score: number, tag?: any }[], success?: () => void, fail?: (ServiceError) => void): void
            showLeaderboard(leaderboardId: string, success?: () => void, fail?: (ServiceError) => void): void
            showLeaderboards(success?: () => void, fail?: (ServiceError) => void): void

            getAchievements(success: (achievements: GameServices.AchievementEntry[]) => void, fail?: (ServiceError) => void): void
            unlockAchievement(id: string, success?: () => void, fail?: (ServiceError) => void): void
            incrementAchievement(id: string, steps: number, success?: () => void, fail?: (ServiceError) => void): void
            showAchievements(success?: () => void, fail?: (ServiceError) => void): void
            resetAchievements(success?: () => void, fail?: (ServiceError) => void): void
        }
    }
}

interface Window {
    readonly gameServices: Cordova.Plugin.GameService
}

declare const gameServices: Cordova.Plugin.GameService