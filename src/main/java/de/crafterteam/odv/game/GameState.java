package de.crafterteam.odv.game;

public enum GameState {
    LOBBY("Warten"),
    STARTING("Countdown"),
    PREPARING("Vorbereitung"),
    FREEZE("Freeze"),
    PROTECTION("Schutzzeit"),
    RUNNING("PvP"),
    ENDING("Ende");

    public final String display;

    GameState(String display) {
        this.display = display;
    }

    public boolean isLobby() {
        return this == LOBBY || this == STARTING || this == PREPARING;
    }

    public boolean isIngame() {
        return this == FREEZE || this == PROTECTION || this == RUNNING;
    }
}
