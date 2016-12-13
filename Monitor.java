import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Class Monitor
 * To synchronize dining philosophers.
 *
 * @author Serguei A. Mokhov, mokhov@cs.concordia.ca, 
 * Student: Daniel Thagard 40012403
 */
public class Monitor  
{
	/*
	 * ------------   
	 * Data members 
	 * ------------
	 */
	
	// Lock to maintain mutual exclusion of the monitor 
	Lock lock = new ReentrantLock();
	// One condition variable for each philosopher, to handle chopstick allocation
	Condition[] self;
	// Condition variable to control ability of philosophers to talk
	Condition canTalk = lock.newCondition();
	
	boolean isPhilTalking = false;
	int numChopsticks;
	private enum State {THINKING, HUNGRY, EATING, TALKING, STARVING};
	private State[] philosopherStates;

	
	// Keep track of failed eating attempts for each philosopher
	private int[] starveIndex;
	
	// Set the number of failed eating attempts that constitutes starving.
	int starveLimit = 2;
	

	/**
	 * Constructor
	 */
	public Monitor(int piNumberOfPhilosophers){
		/* Chopsticks are located between the philosophers, so there is one chopstick per philosopher*/
		numChopsticks = piNumberOfPhilosophers;
		/*
		 * The state of each philosopher:
		 * Initialize all philosophers to be thinking:
		 */
		philosopherStates = new State[piNumberOfPhilosophers];

		/* A condition variable for each philosopher */
		self = new Condition[piNumberOfPhilosophers];

		/* Starving Index for each philosopher, initialized to 0 */
		starveIndex = new int[piNumberOfPhilosophers];
		
		/* Initialize array elements */
		for (int i = 0; i < piNumberOfPhilosophers; i++){
			philosopherStates[i] = State.THINKING;
			self[i] = lock.newCondition();		
			starveIndex[i] = 0;
		}
	}

	/*
	 * -------------------------------
	 * User-defined monitor procedures
	 * -------------------------------
	 */

	/**
	 * Grants request (returns) to eat when both chopsticks/forks are available.
	 * Else forces the philosopher to wait()
	 */
	public void pickUp(final int piTID)
	{
		lock.lock();
		try{
			/*
			 *  The philosopher TID's begin at 1. They must be 0-indexed to correspond
			 *  to the array indices.
			 */		
			int philID = piTID - 1;

			philosopherStates[piTID - 1] = State.HUNGRY;
			test(philID);
			while (philosopherStates[philID] != State.EATING){
				self[philID].await();
			}
		}
		catch (InterruptedException e){
			DiningPhilosophers.reportException(e);

		}
		finally{
			lock.unlock();
		}
	}

	/**
	 * When a given philosopher's done eating, they put the chopstiks/forks down
	 * and let others know they are available.
	 */
	public void putDown(final int piTID)
	{	
		lock.lock();
		try{
			int philID = piTID - 1;

			philosopherStates[philID] = State.THINKING;
			// Test for the philosophers seated on either side of the current philosopher
			test((philID + numChopsticks - 1) % numChopsticks);
			test((philID + 1) % numChopsticks);
		}
		finally{
			lock.unlock();
		}
	}

	/**
	 * Only one philosopher at a time is allowed to philosophy
	 * (while she is not eating).
	 */
	public void requestTalk(final int piTID)
	{
		lock.lock();
		try{
			if (isPhilTalking){
				canTalk.await();
			}
			philosopherStates[piTID - 1] = State.TALKING;
			isPhilTalking = true;
			
		}
		catch (InterruptedException e){
			DiningPhilosophers.reportException(e);
		}
		finally{
			lock.unlock();
		}
	}

	/**
	 * When one philosopher is done talking stuff, others
	 * can feel free to start talking.
	 */
	public void endTalk(final int piTID)
	{
		lock.lock();
		try{
			isPhilTalking = false;
			philosopherStates[piTID - 1] = State.THINKING;
			canTalk.signal();
		}
		finally{
			lock.unlock();
		}
	}
	
	/**
	 * Tests that the current philosopher is hungry, and that neither of the adjacent philosophers are currently eating.
	 * If this is the case, the current philosopher will begin eating.
	 * @param philID, the 0-indexed philosopher ID
	 */
	private void test(final int philID){
		lock.lock();
		try{
			/* The philosophers to the immediate left and right of current philosopher */
			int left = (philID + 1) % numChopsticks;
			int right = (philID + numChopsticks -1) % numChopsticks;
			/* Only proceed if the philosopher being tested is hungry. Otherwise do nothing */
			if (philosopherStates[philID] == State.HUNGRY || philosopherStates[philID] == State.STARVING){
				/* Check that neither of the neighbours is eating to ensure that chopsticks are available */
				if (philosopherStates[right] != State.EATING &&
						philosopherStates[left] != State.EATING){
					/*
					 * If current philosopher is not starving but has a starving neighbour,
					 * signal the starving neighbour, and increment the current philosopher's starving index.
					 */
					if (philosopherStates[philID] != State.STARVING){
						if (philosopherStates[right] == State.STARVING){
							starveIndex[philID]++;
							if (starveIndex[right] > starveLimit){
								philosopherStates[philID] = State.STARVING;
							}
							self[right].signal();
							return;
						}
						else if (philosopherStates[left] == State.STARVING){
							starveIndex[philID]++;
							if (starveIndex[right] > starveLimit){
								philosopherStates[philID] = State.STARVING;
							}
							self[left].signal();
							return;
						}
					}
					/*
					 * The current philosopher can eat!
					 * Unblock current philosopher if she is blocked waiting for the lock.
					 */
					philosopherStates[philID] = State.EATING;
					starveIndex[philID] = 0;
					self[philID].signal();
				}
				/* If the current philosopher is Hungry but can't eat, increment the starving index*/
				else{
					starveIndex[philID]++;
					if (starveIndex[philID] > starveLimit){
						philosopherStates[philID] = State.STARVING;
					}
				}
			}
		}
		finally{
				lock.unlock();
		}
	}
}

// EOF
