package com.sfb.server;

/**
 * Moved to amarillo-core so both server and client can reference it.
 * @see com.sfb.dto.GameStateDto
 */
public class GameStateDto extends com.sfb.dto.GameStateDto {
    public GameStateDto(com.sfb.Game game) { super(game); }
}
