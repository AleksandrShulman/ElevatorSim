import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.logging.StreamHandler;

/**
 * Created by aleks on 5/20/16.
 */
public class Rider {

    final static Logger logger = Logger.getLogger("Rider.class");

    SimpleFormatter fmt = new SimpleFormatter();
    StreamHandler sh = new StreamHandler(System.out, fmt);
    private static AtomicInteger riderIdGenerator;

    {
        logger.addHandler(sh);
        logger.setLevel(Level.INFO);
        if (riderIdGenerator == null) {
            riderIdGenerator = new AtomicInteger();
        }

    }

    final int riderId;
    int startFloor;
    int currentFloor;

    // TODO: have args for tracking how much time a user is waiting.
    // If we collect this info, we can have meaningful insights into
    // which algorithms are better

    // Null means the user is on the startFloor, otherwise they are in an elevator
    ClosestAcceptElevator currentElevator;
    private int destinationFloor;
    private Elevator elevator = null;

    public Rider(final int startFloor, final int destinationFloor) {

        riderId = Rider.riderIdGenerator.incrementAndGet();
        logger.info("Initializing rider " + riderId + " going from " + startFloor + " to "
                + destinationFloor);

        this.currentElevator = null;
        this.currentFloor = startFloor;
        this.startFloor = startFloor;
        this.destinationFloor = destinationFloor;
    }

    public int getDestinationFloor() {
        return destinationFloor;
    }

    public void getInElevator(Elevator e) {
        logger.info("Rider " + riderId + " stepped into elevator " + e.id);
        this.elevator = e;
    }

    public void getOffElevator(Elevator e) {
        //will help w/GC if we need to collect elevators from memory
        logger.info("Rider " + riderId + " got off at " + e.currentFloor);
        if (e.currentFloor != destinationFloor) {
            throw new RuntimeException("Rider did not at expected destination of " +
                    destinationFloor);
        }
        this.elevator = null;
    }

    /**
     * Note that a null output means the user is still waiting for an elevator
     * to arrive
     * @return
     */
    public Elevator getElevator() {
        return elevator;
    }
}
