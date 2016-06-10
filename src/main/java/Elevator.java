/**
 * Created by aleks on 5/20/16.
 */

import java.util.Set;

/**
 * An Elevator object represents the idea of a real-life elevator.
 * At a basic level, it contains information about how many people it contains,
 * where they are going,
 */
public abstract class Elevator implements ElevatorAlgorithm, Runnable {

    public enum Direction {
        DOWN,
        NOT_MOVING,
        UP;
    }
    // state
    protected int id;
    protected int currentFloor;
    protected Integer nextFloorToVisit; // could be null if the elevator is not needed
    Elevator.Direction travelDirection;

    protected Set<Rider> riders;

    //stats
    int totalRidersTransported = 0;

}