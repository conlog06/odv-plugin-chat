package de.crafterteam.meetup.game;

public enum GameState {
    LOBBY,        // warten auf Spieler
    STARTING,     // Lobby-Countdown läuft
    PREPARING,    // Spawns werden generiert / Teleport
    FREEZE,       // Spieler stehen eingefroren auf ihren Spawns
    PROTECTION,   // Schutzzeit (kein PvP)
    RUNNING,      // PvP aktiv
    ENDING;       // Sieger steht fest

    public boolean isLobby() {
        return this == LOBBY || this == STARTING || this == PREPARING;
    }

    public boolean isIngame() {
        return this == FREEZE || this == PROTECTION || this == RUNNING;
    }
}
