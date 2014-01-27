package pacman.entries.ghosts;

import java.util.EnumMap;
import pacman.controllers.Controller;
import pacman.game.Constants.GHOST;
import pacman.game.Constants.MOVE;
import pacman.game.Game;

/*
 * This is the class you need to modify for your entry. In particular, you need to
 * fill in the getActions() method. Any additional classes you write should either
 * be placed in this package or sub-packages (e.g., game.entries.ghosts.mypackage).
 */
public class MyGhosts extends Controller<EnumMap<GHOST,MOVE>>
{
	private EnumMap<GHOST, MOVE> myMoves=new EnumMap<GHOST, MOVE>(GHOST.class);
	
	public EnumMap<GHOST, MOVE> getMove(Game game, long timeDue)
	{
		myMoves.clear();
		myMoves.put(GHOST.BLINKY, MOVE.UP);
		myMoves.put(GHOST.INKY, MOVE.UP);
		myMoves.put(GHOST.PINKY, MOVE.UP);
		myMoves.put(GHOST.SUE, MOVE.UP);
		//Place your game logic here to play the game as the ghosts
		
		return myMoves;
	}

	@Override
	public void Store() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void Read() {
		// TODO Auto-generated method stub
		
	}
}