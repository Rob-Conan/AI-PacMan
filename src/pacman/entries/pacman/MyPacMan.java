package pacman.entries.pacman;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import pacman.controllers.Controller;
import pacman.game.Constants;
import pacman.game.Constants.DM;
import pacman.game.Constants.GHOST;
import pacman.game.Constants.MOVE;
import pacman.game.Game;

/*
 * State Conditions
 * 0 - Pill North of PacMan
 * 1 - Pill East of PacMan
 * 2 - Pill South of PacMan
 * 3 - Pill West of PacMan
 * 4 - Ghost approaching from North
 * 5 - Ghost approaching from East
 * 6 - Ghost approaching from South
 * 7 - Ghost approaching from West
 * 12 - Alternative path between ghost and PacMan
 * 13 - Just eaten the big pill
 * 
 * * * * * * * * * 
 * 
 * Reward values
 * Eat pill = 1
 * Eat big pill = 3
 * Got eaten = -10
 * Eat Ghost = 5
 *  
 * * * * * * * * * 
 * 
 * 1 - UP 
 * 2 - RIGHT
 * 3 - DOWN
 * 4 - LEFT
 */

public class MyPacMan extends Controller<MOVE> {
	static int QValues[][] = new int[2048][2048];
	MOVE table[] = { MOVE.UP, MOVE.RIGHT, MOVE.DOWN, MOVE.LEFT };
	Random rnd = new Random();
	int reward = 0;
	int max = 0;
	boolean firstRun = true;
	int nextMove = 0;
	private MOVE myMove = MOVE.LEFT;
	int current = 0;
	boolean newRun = false;
	int counter = 0;
	int oldLives = 3;
	int maxOldState = 0;
	boolean edible = false;
	int[] chaseIndex;

	int cache[] = new int[20];
	int cacheCount = 0;
	int oldMax = 0;
	int oldCurrent = 0;
	int oldReward = 0;

	public MOVE getMove(Game game, long timeDue) {
		/* Cache is used to store the last 20 node indexes */
		cache[cacheCount] = game.getPacmanCurrentNodeIndex();
		/* If the cache is not full increase the counter else reset */
		if (cacheCount < 19)
			cacheCount++;
		else
			cacheCount = 0;
		/*
		 * A count to identify the start of a new game (a new game will have 3
		 * lives and old lives will be 0/1 if carried over from a previous game
		 */
		if (game.getPacmanNumberOfLivesRemaining() > oldLives) {
			oldLives = game.getPacmanNumberOfLivesRemaining();
		}
		/* No Q-learning takes place in the first move so don't attempt to */
		if (!firstRun) {
			QValues[current][max] = QValue(QValues[current][max], 0.1, 0.9,
					reward, prediction(game, myMove));
			max = 0;
			reward = 0;
			nextMove = 0;
		} else

			firstRun = false;
		/* Reward values calculated below */
		if (game.wasPillEaten())
			reward += 1;
		if (game.wasPowerPillEaten())
			reward += 3;
		if (game.wasPacManEaten()) {
			reward -= 10;
			oldLives--;
		}
		for (GHOST c : Constants.GHOST.values()) {
			if (game.wasGhostEaten(c))
				reward += 5;
		}
		if (!game.wasPacManEaten())
			reward += 1;

		/*
		 * For each ghost, can it be eaten? If so chase down the nearest ghost
		 * and eat it.
		 */

		int minDistance = Integer.MAX_VALUE;
		GHOST minGhost = null;

		for (GHOST c : GHOST.values()) {
			if (game.getGhostEdibleTime(c) > 0) {
				int distance = game.getShortestPathDistance(
						game.getPacmanCurrentNodeIndex(),
						game.getGhostCurrentNodeIndex(c));

				if (distance < minDistance) {
					minDistance = distance;
					minGhost = c;
				}
			}
		}
		/*
		 * Chase the nearest edible ghost, returns myMove so code below is not
		 * executed
		 */
		if (minGhost != null) {
			myMove = game.getNextMoveTowardsTarget(
					game.getPacmanCurrentNodeIndex(),
					game.getGhostCurrentNodeIndex(minGhost), DM.PATH);
			return myMove;
		}

		/* Calculate up to 4 index values from the possible moves available */
		int index[] = { 0, 0, 0, 0 };
		current = getIndex(game, myMove, true);

		MOVE possibleMoves[] = game.getPossibleMoves(game
				.getPacmanCurrentNodeIndex());
		for (int j = 0; j < table.length; j++) {
			for (int i = 0; i < possibleMoves.length; i++) {
				if (table[j].toString().equalsIgnoreCase(
						possibleMoves[i].toString())) {
					index[j] = getIndex(game, table[j], false);
				}
			}
		}

		/* Get the Q-Values for the 4 indexes */
		int[] q = new int[4];
		for (int i = 0; i < 4; i++) {
			if (index[i] != -1) {
				q[i] = QValues[current][index[i]];
			} else
				q[i] = 0;
		}
		/* Get the max of the retrieved Q-Values */
		boolean var = false;
		for (int i = 0; i < 4; i++) {
			if (q[i] > max) {
				max = q[i];
				myMove = nextMove(i);
				var = true;
			}
		}

		boolean alt = true;
		/* If no max was found choose a random move */
		if (!var) {
			MOVE mv[] = game.getPossibleMoves(game.getPacmanCurrentNodeIndex(),
					myMove);
			if (mv != null) {
				myMove = mv[rnd.nextInt(mv.length)];
				alt = false;
			}
		}

		/* If no possible move found, get closest pill */
		if (alt) {
			int[] activePills = game.getActivePillsIndices();
			int[] activePowerPills = game.getActivePowerPillsIndices();
			int[] targetNodeIndices = new int[activePills.length
					+ activePowerPills.length];

			for (int i = 0; i < activePills.length; i++)
				targetNodeIndices[i] = activePills[i];

			for (int i = 0; i < activePowerPills.length; i++)
				targetNodeIndices[activePills.length + i] = activePowerPills[i];

			int nearest = game.getClosestNodeIndexFromNodeIndex(
					game.getPacmanCurrentNodeIndex(), targetNodeIndices,
					DM.PATH);
			max = nearest;
			myMove = game.getNextMoveTowardsTarget(
					game.getPacmanCurrentNodeIndex(), nearest, DM.PATH);

		}

		/*
		 * If the cache is full, count the number of times the current index
		 * occurs
		 */
		if (cacheCount >= 19) {
			int count = 0;
			for (int i = cache.length - 1; i > 0; i--) {
				if (cache[i] == game.getPacmanCurrentNodeIndex()) {
					count++;
				}
			}
			/*
			 * If the current occurs more than 10 (Can be changed) choose a
			 * random move
			 */
			if (count > 10) {
				MOVE nextMove[] = game.getPossibleMoves(
						game.getPacmanCurrentNodeIndex(), myMove);
				myMove = nextMove(rnd.nextInt(nextMove.length));
			}
			cacheCount = 0;
		}

		// epsilon greedy
		/*double esp = 0.75;
		if (rnd.nextInt(100) < (esp * 100)) {
			MOVE mv[] = game.getPossibleMoves(game.getPacmanCurrentNodeIndex(),
					myMove);
			if (mv != null) {
				myMove = mv[rnd.nextInt(mv.length)];
				alt = false;
			}
		}*/

		/* Return the PacMan's next move */
		return myMove;

	}

	/* Store the QValues in a file */
	public void Store() {
		try {
			@SuppressWarnings("resource")
			FileChannel file = new FileOutputStream("fc.txt").getChannel();
			ByteBuffer buf = ByteBuffer.allocateDirect(4 * 2048 * 2048);
			for (int i = 0; i < 2048; i++) {
				for (int j = 0; j < 2048; j++) {
					buf.put(String.valueOf(QValues[i][j]).getBytes());
					buf.put("\n".getBytes());
				}
			}
			buf.flip();
			file.write(buf);
			buf.clear();
			file.close();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	/* Read the QValues from a file */
	public void Read() {
		List<Integer> body = new ArrayList<>();

		BufferedReader file;
		try {
			file = new BufferedReader(new FileReader("fc.txt"));
			String read = file.readLine();
			while (read != null) {
				body.add(Integer.parseInt(read));
				read = file.readLine();
			}
			for (int i = 0; i < 2048; i++) {
				for (int j = 0; j < 2048; j++) {
					QValues[i][j] = body.get((i * 2048) + j);
				}
			}
			file.close();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		// return array;
	}

	private static int prediction(Game game, MOVE myMove) {

		/*
		 * Same code as above but no decisions used with this solely used to
		 * calculate the max value of the next state
		 */
		int index[] = { 0, 0, 0, 0 };
		int current = getIndex(game, myMove, true);
		int max = 0;

		index[0] = getIndex(game, MOVE.UP, false);
		index[1] = getIndex(game, MOVE.RIGHT, false);
		index[2] = getIndex(game, MOVE.DOWN, false);
		index[3] = getIndex(game, MOVE.LEFT, false);

		int[] q = new int[4];
		for (int i = 0; i < 4; i++) {
			if (index[i] != -1) {
				q[i] = QValues[current][index[i]];
			} else
				q[i] = 0;
		}

		for (int i = 0; i < 4; i++) {
			if (q[i] >= max) {
				max = q[i];
			}
		}

		return max;
	}

	/* Calculate the QValue using the Q-learning formula */
	private static int QValue(int old, double d, double e, int reward,
			int maxNext) {
		int x = (int) (old + d * (reward + e * maxNext - old));
		return x;
	}

	/* Get a Move based on a given index */
	private static MOVE nextMove(int nextMove) {
		if (nextMove == 0) {
			return MOVE.UP;
		} else if (nextMove == 1) {
			return MOVE.RIGHT;
		} else if (nextMove == 2) {
			return MOVE.DOWN;
		} else if (nextMove == 3) {
			return MOVE.LEFT;
		} else
			System.out.println("Unreachable move");

		return MOVE.LEFT;
	}

	private static int getIndex(Game game, MOVE mv, Boolean curr) {
		/* 
		 * Get the 11 bit index value for the QValue and 
		 * transform to a single number between 0 and 2048 
		 */
		int next = game.getNeighbour(game.getPacmanCurrentNodeIndex(), mv);

		if ((next != -1) || (curr)) {
			int zone = getQuadrant(game,
					game.getNodeXCood(game.getPacmanCurrentNodeIndex()),
					game.getNodeYCood(game.getPacmanCurrentNodeIndex()));

			int array[] = new int[11];

			int pillValue = pillIndex(game, mv);
			int bigPill = powerPillIndex(game, mv);
			int ghosts[] = Ghosts(game, zone);
			int danger[] = dangerClose(game, mv);

			array[0] = pillValue;
			array[1] = bigPill;
			for (int i = 0; i < ghosts.length; i++) {
				array[i + 2] = ghosts[i];
			}
			for (int j = 0; j < danger.length; j++) {
				array[j + 6] = danger[j];
			}

			int result = 0;
			for (int i = 10; i >= 0; i--)
				result += Math.pow(2, i) * array[i];

			// System.out.println(result);
			return result;
		} else
			return -1;
	}

	/* If a pill exits in the immediate vicinity */
	private static int pillIndex(Game game, MOVE mv) {
		int currentPosition = game.getPacmanCurrentNodeIndex();
		int next = game.getNeighbour(currentPosition, mv);
		for (int i = 0; i < 5; i++) {
			if (next != -1) {
				int pillIndex = game.getPillIndex(next);
				if ((pillIndex != -1) && (game.isPillStillAvailable(pillIndex))) {
					return 1;
				}
				next = game.getNeighbour(next, mv);
			}
		}
		return 0;
	}

	/* If a power pill exits in the immediate vicinity */
	private static int powerPillIndex(Game game, MOVE mv) {
		int currentPosition = game.getPacmanCurrentNodeIndex();
		int next = game.getNeighbour(currentPosition, mv);
		for (int i = 0; i < 5; i++) {
			if (next != -1) {
				int pillIndex = game.getPowerPillIndex(next);
				if ((pillIndex != -1)
						&& (game.isPowerPillStillAvailable(pillIndex))) {
					return 1;
				}
				next = game.getNeighbour(next, mv);
			}

		}
		return 0;
	}

	/* If the ghosts are within a X steps of PacMan */
	private static int[] dangerClose(Game game, MOVE pacMove) {
		int danger[] = { 0, 0, 0, 0, 0 };
		int currentPosition = game.getPacmanCurrentNodeIndex();
		for (GHOST c : Constants.GHOST.values()) {
			MOVE ghostDirection = game.getGhostLastMoveMade(c);
			int ghostPosition = game.getGhostCurrentNodeIndex(c);

			int pacX = game.getNodeXCood(currentPosition);
			int pacY = game.getNodeYCood(currentPosition);

			int GstX = game.getNodeXCood(ghostPosition);
			int GstY = game.getNodeYCood(ghostPosition);

			int thresholdValue = 100;

			double distance = game.getDistance(currentPosition, ghostPosition,
					DM.PATH);

			if (thresholdValue > distance) {

				if (ghostDirection == MOVE.DOWN) {
					if (pacY > GstY)
						danger[0] = 1;
				} else if (ghostDirection == MOVE.LEFT) {
					if (pacX < GstX)
						danger[1] = 1;
				} else if (ghostDirection == MOVE.UP) {
					if (pacY < GstY)
						danger[2] = 1;
				} else if (ghostDirection == MOVE.RIGHT) {
					if (pacX > GstX)
						danger[3] = 1;
				}

				for (int j = 0; j < distance / 2; j++) {
					int next = game.getNeighbour(currentPosition, pacMove);
					if (next != -1) {
						if (game.isJunction(next)) {
							danger[4] = 1;
							break;
						}
					}
				}

			}
		}
		return danger;
	}

	private static int[] Ghosts(Game game, int zone) {

		int ghostPosition[] = { 0, 0, 0, 0 };
		int i = 0;
		int newX = 0;
		int newY = 0;
		/* North/East/South/West */
		int results[] = { 0, 0, 0, 0 };
		for (Constants.GHOST c : Constants.GHOST.values()) {
			ghostPosition[i] = game.getGhostCurrentNodeIndex(c);
			newX = game.getNodeXCood(ghostPosition[i]);
			newY = game.getNodeYCood(ghostPosition[i]);
			int ghostZone = getQuadrant(game, newX, newY);
			// System.out.println(c.name() + " Zone: " + ghostZone);

			/*
			 * PacMan in the top left region
			 */
			if (zone == 0) {
				if (ghostZone == 0) {
					// Possibly have an immediate danger value
				} else if (ghostZone == 1) {
					// East variable set to 1
					if (game.getGhostLastMoveMade(c) == MOVE.LEFT) {
						results[1] = 1;
					}
				} else if (ghostZone == 2) {
					// South variable set to 1
					if (game.getGhostLastMoveMade(c) == MOVE.UP) {
						results[2] = 1;
					}
				} else if (ghostZone == 3) {
					// South variable set to 1
					if (game.getGhostLastMoveMade(c) == MOVE.UP) {
						results[2] = 1;
					}
					// East variable set to 1
					if (game.getGhostLastMoveMade(c) == MOVE.LEFT) {
						results[1] = 1;
					}
				} else {
					// Possibly inbetween regions
				}
			}
			/*
			 * PacMan in the top right region
			 */
			else if (zone == 1) {
				if (ghostZone == 0) {
					// West variable set to 1
					if (game.getGhostLastMoveMade(c) == MOVE.RIGHT) {
						results[3] = 1;
					}
				} else if (ghostZone == 1) {
					// Again not sure
				} else if (ghostZone == 2) {
					// South variable set to 1
					if (game.getGhostLastMoveMade(c) == MOVE.UP) {
						results[2] = 1;
					}
					// West variable set to 1
					if (game.getGhostLastMoveMade(c) == MOVE.RIGHT) {
						results[3] = 1;
					}
				} else if (ghostZone == 3) {
					// South variable set to 1
					if (game.getGhostLastMoveMade(c) == MOVE.UP) {
						results[2] = 1;
					}
				}

			}
			/*
			 * PacMan in the bottom left region
			 */
			else if (zone == 2) {
				if (ghostZone == 0) {
					// North variable set to 1
					if (game.getGhostLastMoveMade(c) == MOVE.DOWN) {
						results[0] = 1;
					}
				} else if (ghostZone == 1) {
					// East variable set to 1
					if (game.getGhostLastMoveMade(c) == MOVE.LEFT) {
						results[1] = 1;
					}
					// North variable set to 1
					if (game.getGhostLastMoveMade(c) == MOVE.DOWN) {
						results[0] = 1;
					}
				} else if (ghostZone == 2) {
					// Again not sure
				} else if (ghostZone == 3) {
					// East variable set to 1
					if (game.getGhostLastMoveMade(c) == MOVE.LEFT) {
						results[1] = 1;
					}
				}
			}
			/*
			 * PacMan in the bottom right region
			 */
			else if (zone == 3) {
				if (ghostZone == 0) {
					// West variable set to 1
					if (game.getGhostLastMoveMade(c) == MOVE.RIGHT) {
						results[3] = 1;
					}
					// North variable set to 1
					if (game.getGhostLastMoveMade(c) == MOVE.DOWN) {
						results[0] = 1;
					}
				} else if (ghostZone == 1) {
					// North variable set to 1
					if (game.getGhostLastMoveMade(c) == MOVE.DOWN) {
						results[0] = 1;
					}
				} else if (ghostZone == 2) {
					// West variable set to 1
					if (game.getGhostLastMoveMade(c) == MOVE.RIGHT) {
						results[3] = 1;
					}
				} else if (ghostZone == 3) {
					// Again not sure
				}
			} else {
				System.out.println("Should not reach me EVER");
			}

			i++;
		}
		return results;
	}

	private static int getQuadrant(Game game, int locationX, int locationY) {
		int startX = game.getNodeXCood(0);
		int startY = game.getNodeYCood(0);
		int lastX = game.getNodeXCood(game.getNumberOfNodes() - 2);
		int lastY = game.getNodeYCood(game.getNumberOfNodes() - 2);

		int width = lastX - startX;
		int height = lastY - startY;
		if (locationY >= height / 2) { /* Bottom region */
			if (locationX >= width / 2)
				return 3; /* Quadrant 3 */
			else
				return 2; /* Quadrant 2 */
		} else { /* Top region */
			if (locationX >= width / 2)
				return 1; /* Quadrant 1 */
			else
				return 0; /* Quadrant 0 */
		}
	}

}